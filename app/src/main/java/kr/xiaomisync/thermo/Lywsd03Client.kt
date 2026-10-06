package kr.xiaomisync.thermo

import kr.xiaomisync.ble.GattSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * LYWSD03MMC 블루투스 온습도계 2 (정사각 LCD, CR2032) — 순정 펌웨어 기준.
 * 프로토콜은 https://github.com/JsBergbau/MiTemperature2 (LYWSD03MMC.py) 를 참고했다.
 *  - LYWSD02 와 같은 EBE0CCC1 특성에 알림을 켜면 약 6초마다 5바이트가 온다:
 *    온도×100 (2바이트, 부호 있음) + 습도 % (1바이트) + 전지 전압 mV (2바이트), 모두 리틀 엔디언
 *  - 배터리 %는 전압으로 계산한다: 2.1V = 0%, 3.1V 이상 = 100%
 *  - ATC/pvvx 사설 펌웨어를 올린 기기는 이름이 ATC_ 로 바뀌고 동작이 달라 여기서는 다루지 않는다
 */
class Lywsd03Client(private val session: GattSession) : ThermoClient {

    override val readings: Flow<ThermoReading> = session.notifications
        .filter { it.uuid == UUID_DATA }
        .mapNotNull { parse(it.value) }

    override suspend fun startReadings() {
        session.enableNotifications(UUID_DATA)
    }

    /** 배터리는 값과 함께 온다 */
    override suspend fun readBattery(): Int? = null

    override suspend fun readFirmware(): String? = readFirmwareOf(session)

    companion object {
        private val UUID_DATA = UUID.fromString("EBE0CCC1-7A0A-4B0C-8A1A-6FF2997DA3A6")

        fun parse(value: ByteArray, nowMillis: Long = System.currentTimeMillis()): ThermoReading? {
            if (value.size < 5) return null
            val buffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
            val temperature = buffer.short / 100.0
            val humidity = (buffer.get().toInt() and 0xFF).toDouble()
            val millivolts = buffer.short.toInt() and 0xFFFF
            if (temperature !in -40.0..80.0 || humidity !in 0.0..100.0) return null
            return ThermoReading(temperature, humidity, nowMillis, batteryPercent(millivolts))
        }

        /** 2100mV → 0%, 3100mV 이상 → 100% (직선) */
        fun batteryPercent(millivolts: Int): Int = ((millivolts - 2100) / 10).coerceIn(0, 100)
    }
}
