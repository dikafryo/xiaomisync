package kr.xiaomisync.kettle

import kr.xiaomisync.ble.BleException
import kr.xiaomisync.ble.GattSession
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/* ===================== 데이터 형식 ===================== */

enum class KettleAction(val label: String) { IDLE("대기"), HEATING("가열 중"), COOLING("식히는 중"), KEEPING_WARM("보온 중") }

/** 보온 방식: 끓인 뒤 목표 온도까지 식히기 / 끓이지 않고 목표 온도까지만 데우기 */
enum class KeepWarmType(val code: Int, val label: String) {
    BOIL_THEN_COOL(0, "끓인 뒤 식혀서 보온"),
    HEAT_ONLY(1, "끓이지 않고 데워서 보온"),
}

data class KettleStatus(
    val action: KettleAction,
    val keepWarmOn: Boolean,
    val keepWarmTemperature: Int,
    val currentTemperature: Int,
    val keepWarmType: KeepWarmType,
    val keepWarmElapsedMinutes: Int,
)

/**
 * YM-K1501 미 스마트 전기주전자.
 * 프로토콜 출처: https://github.com/aprosvetova/xiaomi-kettle , https://github.com/drndos/mikettle
 *  - 연결 후 샤오미 인증(FE95 서비스)을 먼저 통과해야 상태를 받을 수 있다
 *  - 상태는 데이터 서비스 aa02 알림, 설정은 aa01(보온 방식·온도) / aa04(보온 시간)
 *  - 끓이기 시작·보온 켜기는 본체 버튼으로만 된다 (블루투스 명령이 없다)
 */
class KettleClient(
    private val session: GattSession,
    private val address: String,
    private val productId: Int,
) {
    private lateinit var statusUuid: UUID

    val status: Flow<KettleStatus>
        get() = session.notifications
            .filter { it.uuid == statusUuid }
            .mapNotNull { parse(it.value) }

    /** 샤오미 인증 → 상태 알림 켜기. 실패하면 사용자에게 보여줄 문장으로 BleException. */
    suspend fun authenticateAndSubscribe() {
        val mac = KettleCipher.reversedMac(address)
        val authUuid = session.characteristicUuid(MI_SERVICE, 0x0001)
        val authInitUuid = session.characteristicUuid(MI_SERVICE, 0x0010)
        val versionUuid = session.characteristicUuid(MI_SERVICE, 0x0004)

        session.write(authInitUuid, KEY1)
        val reply = coroutineScope {
            val answer = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(AUTH_TIMEOUT_MS) { session.notifications.first { it.uuid == authUuid }.value }
            }
            session.enableNotifications(authUuid)
            session.write(authUuid, KettleCipher.cipher(KettleCipher.mixA(mac, productId), TOKEN))
            answer.await()
        } ?: throw BleException("주전자가 인증에 응답하지 않습니다. 주전자를 휴대폰 가까이 두고 다시 연결해 주세요.")

        val decoded = KettleCipher.cipher(
            KettleCipher.mixB(mac, productId),
            KettleCipher.cipher(KettleCipher.mixA(mac, productId), reply),
        )
        if (!decoded.contentEquals(TOKEN)) {
            throw BleException("주전자 인증에 실패했습니다. Mi Home 앱이 주전자에 연결되어 있으면 닫은 뒤 다시 연결해 주세요.")
        }
        session.write(authUuid, KettleCipher.cipher(TOKEN, KEY2))
        session.read(versionUuid)

        statusUuid = session.characteristicUuid(DATA_SERVICE, 0xAA02)
        session.enableNotifications(statusUuid)
    }

    /** 보온 방식과 온도(40~95℃) 설정 */
    suspend fun setKeepWarm(type: KeepWarmType, temperature: Int) {
        require(temperature in 40..95)
        session.write(session.characteristicUuid(DATA_SERVICE, 0xAA01), byteArrayOf(type.code.toByte(), temperature.toByte()))
    }

    /** 보온 유지 시간 (0~12시간, 30분 단위) */
    suspend fun setKeepWarmHours(hours: Double) {
        require(hours in 0.0..12.0)
        session.write(session.characteristicUuid(DATA_SERVICE, 0xAA04), byteArrayOf((hours * 2).toInt().toByte()))
    }

    companion object {
        private const val AUTH_TIMEOUT_MS = 10_000L

        /** YM-K1501 기본 제품 번호. 광고(MiBeacon)에 실린 번호가 있으면 그것을 쓴다 */
        const val DEFAULT_PRODUCT_ID = 131

        private val MI_SERVICE = UUID.fromString("0000fe95-0000-1000-8000-00805f9b34fb")
        private val DATA_SERVICE = UUID.fromString("01344736-0000-1000-8000-262837236156")

        private val KEY1 = byteArrayOf(0x90.toByte(), 0xCA.toByte(), 0x85.toByte(), 0xDE.toByte())
        private val KEY2 = byteArrayOf(0x92.toByte(), 0xAB.toByte(), 0x54, 0xFA.toByte())

        // mikettle 이 실기기로 검증한 12바이트 토큰 (세션마다 같은 값을 써도 된다)
        private val TOKEN = byteArrayOf(
            0x01, 0x5C, 0xCB.toByte(), 0xA8.toByte(), 0x80.toByte(), 0x0A,
            0xBD.toByte(), 0xC1.toByte(), 0x2E, 0xB8.toByte(), 0xED.toByte(), 0x82.toByte(),
        )

        /** 상태 알림: [0]동작 [1]모드(1 끓이기, 2·3 보온, 255 없음) [4]보온 온도 [5]현재 온도 [6]보온 방식 [7..8]보온 경과(분) */
        fun parse(data: ByteArray): KettleStatus? {
            if (data.size < 8) return null
            val u = { i: Int -> data[i].toInt() and 0xFF }
            val action = KettleAction.entries.getOrNull(u(0)) ?: return null
            val elapsed = if (data.size >= 9) (u(7) shl 8) or u(8) else u(7)
            return KettleStatus(
                action = action,
                keepWarmOn = u(1) == 2 || u(1) == 3,
                keepWarmTemperature = u(4),
                currentTemperature = u(5),
                keepWarmType = if (u(6) == 1) KeepWarmType.HEAT_ONLY else KeepWarmType.BOIL_THEN_COOL,
                keepWarmElapsedMinutes = elapsed,
            )
        }
    }
}
