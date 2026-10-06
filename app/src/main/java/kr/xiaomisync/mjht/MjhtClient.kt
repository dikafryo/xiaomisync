package kr.xiaomisync.mjht

import kr.xiaomisync.ble.BleException
import kr.xiaomisync.ble.GattSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import java.util.UUID

/** 온습도계가 보낸 값 한 건. 이 기기는 습도도 소수 한 자리까지 보낸다. */
data class MjhtReading(val temperature: Double, val humidity: Double, val receivedAtMillis: Long)

/**
 * LYWSDCGQ/01ZM (블루투스 이름 MJ_HT_V1) 1세대 둥근 온습도계 전용 명령 모음.
 * 프로토콜은 https://github.com/ratcashdev/mitemp (mitemp_bt_poller.py) 를 참고했다.
 *  - 데이터 특성에 알림을 켜면 몇 초마다 ASCII 글자 "T=23.5 H=45.0" 이 온다 (끝에 0x00 이 붙기도 함)
 *  - 배터리는 블루투스 표준 배터리 항목(0x2A19) 1바이트 0~100
 */
class MjhtClient(private val session: GattSession) {

    /** 알림으로 들어오는 온도·습도. [startReadings] 를 부른 뒤부터 값이 흐른다. */
    val readings: Flow<MjhtReading> = session.notifications
        .filter { it.uuid == UUID_DATA }
        .mapNotNull { parse(it.value) }

    suspend fun startReadings() {
        session.enableNotifications(UUID_DATA)
    }

    suspend fun readBattery(): Int {
        val value = session.read(UUID_BATTERY)
        if (value.isEmpty()) throw BleException("배터리 값을 읽지 못했습니다.")
        return value[0].toInt() and 0xFF
    }

    /** 펌웨어 버전 (없거나 읽지 못하면 null — 표시만 하는 값이라 실패해도 연결은 유지) */
    suspend fun readFirmware(): String? = try {
        String(session.read(UUID_FIRMWARE), Charsets.US_ASCII).trim('\u0000', ' ').ifEmpty { null }
    } catch (e: BleException) {
        null
    }

    companion object {
        private val UUID_DATA = UUID.fromString("226caa55-6476-4566-7562-66734470666d")
        private val UUID_BATTERY = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        private val UUID_FIRMWARE = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

        private val PATTERN = Regex("""T=(-?\d+(?:\.\d+)?)\s+H=(\d+(?:\.\d+)?)""")

        /** "T=23.5 H=45.0" → 값. 형식이 다르거나 말이 안 되는 값이면 버린다. */
        fun parse(value: ByteArray, nowMillis: Long = System.currentTimeMillis()): MjhtReading? {
            val text = String(value, Charsets.US_ASCII)
            val match = PATTERN.find(text) ?: return null
            val temperature = match.groupValues[1].toDoubleOrNull() ?: return null
            val humidity = match.groupValues[2].toDoubleOrNull() ?: return null
            if (temperature !in -40.0..80.0 || humidity !in 0.0..100.0) return null
            return MjhtReading(temperature, humidity, nowMillis)
        }
    }
}
