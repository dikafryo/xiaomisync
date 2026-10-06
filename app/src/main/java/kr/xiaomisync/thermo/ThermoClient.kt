package kr.xiaomisync.thermo

import kr.xiaomisync.ble.BleException
import kr.xiaomisync.ble.GattSession
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** 온습도계가 보낸 값 한 건. 배터리를 값과 함께 보내는 기기(LYWSD03MMC)는 [batteryPercent] 도 채운다. */
data class ThermoReading(
    val temperature: Double,
    val humidity: Double,
    val receivedAtMillis: Long,
    val batteryPercent: Int? = null,
)

/**
 * '연결하면 몇 초마다 온도·습도를 보내 주는' 온습도계 공통 동작.
 * 기기마다 다른 것은 값의 형식과 배터리 읽는 법뿐이라, 화면(ThermoScreen)·상태(ThermoViewModel)는 함께 쓴다.
 */
interface ThermoClient {
    /** 알림으로 들어오는 값. [startReadings] 를 부른 뒤부터 흐른다. */
    val readings: Flow<ThermoReading>

    suspend fun startReadings()

    /** 배터리 %. 값과 함께 오는 기기는 null 을 돌려주고 [ThermoReading.batteryPercent] 로 준다. */
    suspend fun readBattery(): Int?

    /** 펌웨어 버전 (표시용 — 읽지 못하면 null) */
    suspend fun readFirmware(): String?
}

private val UUID_FIRMWARE = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

/** 블루투스 표준 '기기 정보 > 펌웨어 버전'(0x2A26). 없거나 읽지 못하면 null — 표시용이라 실패해도 연결은 유지 */
internal suspend fun readFirmwareOf(session: GattSession): String? = try {
    String(session.read(UUID_FIRMWARE), Charsets.US_ASCII).trim('\u0000', ' ').ifEmpty { null }
} catch (e: BleException) {
    null
}
