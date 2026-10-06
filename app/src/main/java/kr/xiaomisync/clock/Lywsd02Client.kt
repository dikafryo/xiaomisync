package kr.xiaomisync.clock

import kr.xiaomisync.ble.BleException
import kr.xiaomisync.ble.GattSession
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.TimeZone
import java.util.UUID

/* ===================== 데이터 형식 ===================== */

enum class TemperatureUnit(val code: Byte, val label: String) {
    CELSIUS(0xFF.toByte(), "섭씨(℃)"),
    FAHRENHEIT(0x01, "화씨(℉)"),
}

data class SensorReading(val temperature: Double, val humidity: Int)

/** 시계에 저장된 시간: 표준 시각(초) + 시간대(시간 단위, 한국은 +9) */
data class DeviceClock(val epochSeconds: Long, val timezoneHours: Int)

/**
 * LYWSD02 계열 시계 명령 모음 — LYWSD02 와 MHO-C303 이 같은 서비스(EBE0CCB0)·같은 5바이트 시간 형식을 쓴다.
 * 프로토콜은 https://github.com/h4/lywsd02 (lywsd02/client.py) 를 그대로 옮겼다.
 * MHO-C303 시간 형식 확인: https://gist.github.com/vtjnash/bb288ffba2a5386f622c1f22d9bae2a3
 * 모든 숫자는 리틀 엔디언(작은 자리 먼저)이다.
 */
class Lywsd02Client(private val session: GattSession) {

    suspend fun readSensor(): SensorReading {
        // 온습도는 '읽기'가 아니라 '알림'으로만 오므로, 알림을 켜고 첫 값을 기다린다
        val value = coroutineScope {
            val firstValue = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(SENSOR_TIMEOUT_MS) {
                    session.notifications.first { it.uuid == UUID_DATA }.value
                }
            }
            session.enableNotifications(UUID_DATA)
            firstValue.await()
        } ?: throw BleException("온도·습도 값을 받지 못했습니다. 잠시 후 '다시 읽기'를 눌러 주세요.")

        return parseSensor(value)
    }

    suspend fun readBattery(): Int {
        val value = session.read(UUID_BATTERY)
        if (value.isEmpty()) throw BleException("배터리 값을 읽지 못했습니다.")
        return value[0].toInt() and 0xFF
    }

    suspend fun readUnit(): TemperatureUnit {
        val value = session.read(UUID_UNITS)
        return if (value.firstOrNull() == TemperatureUnit.FAHRENHEIT.code) {
            TemperatureUnit.FAHRENHEIT
        } else {
            TemperatureUnit.CELSIUS
        }
    }

    suspend fun writeUnit(unit: TemperatureUnit) {
        session.write(UUID_UNITS, byteArrayOf(unit.code))
    }

    suspend fun readClock(): DeviceClock {
        val value = session.read(UUID_TIME)
        if (value.size < 4) throw BleException("시계의 시간을 읽지 못했습니다.")
        val buffer = littleEndian(value)
        val epochSeconds = buffer.int.toLong() and 0xFFFFFFFFL
        // 옛 펌웨어는 시간대 없이 4바이트만 보낸다
        val timezoneHours = if (value.size >= 5) buffer.get().toInt() else 0
        return DeviceClock(epochSeconds, timezoneHours)
    }

    /** 휴대폰의 현재 시간과 시간대로 시계를 맞춘다. 맞춘 값을 돌려준다. */
    suspend fun syncClockToPhone(): DeviceClock {
        val nowMillis = System.currentTimeMillis()
        val clock = DeviceClock(
            epochSeconds = nowMillis / 1000,
            // 서머타임까지 반영된 현재 시간대 (한국은 항상 +9)
            timezoneHours = TimeZone.getDefault().getOffset(nowMillis) / 3_600_000,
        )
        val data = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(clock.epochSeconds.toInt())
            .put(clock.timezoneHours.toByte())
            .array()
        session.write(UUID_TIME, data)
        return clock
    }

    private fun parseSensor(value: ByteArray): SensorReading {
        if (value.size < 3) throw BleException("온도·습도 값의 형식이 올바르지 않습니다.")
        val buffer = littleEndian(value)
        val temperature = buffer.short / 100.0
        val humidity = buffer.get().toInt() and 0xFF
        return SensorReading(temperature, humidity)
    }

    private fun littleEndian(value: ByteArray): ByteBuffer =
        ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)

    companion object {
        private const val SENSOR_TIMEOUT_MS = 8_000L

        private fun lywsdUuid(shortId: String): UUID =
            UUID.fromString("EBE0$shortId-7A0A-4B0C-8A1A-6FF2997DA3A6")

        private val UUID_UNITS = lywsdUuid("CCBE")   // 1바이트: 0xFF 섭씨, 0x01 화씨
        private val UUID_TIME = lywsdUuid("CCB7")    // 5바이트: 시각(4) + 시간대(1)
        private val UUID_DATA = lywsdUuid("CCC1")    // 3바이트: 온도x100(2) + 습도(1), 알림 전용
        private val UUID_BATTERY = lywsdUuid("CCC4") // 1바이트: 0~100
    }
}
