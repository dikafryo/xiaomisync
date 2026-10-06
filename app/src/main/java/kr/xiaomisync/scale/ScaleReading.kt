package kr.xiaomisync.scale

import kr.xiaomisync.ble.FoundDevice

/** 체중계 광고 한 건을 해석한 값 */
data class ScaleReading(
    val weightKg: Double,
    /** 숫자가 멈춰 측정이 끝남 */
    val stable: Boolean,
    /** 사람이 내려옴 */
    val removed: Boolean,
    val receivedAtMillis: Long,
)

/**
 * 미 체중계는 연결하지 않아도 올라서면 광고에 체중을 실어 보낸다. 형식은 openScale 의 해석을 따랐다.
 * (https://github.com/oliexdev/openScale — MiScale v1/v2 핸들러)
 *  - 미 스마트 체중계 1 (XMTZC01HM): 서비스 데이터 0x181D
 *      [0] 제어(bit0 파운드, bit4 근(斤), bit5 측정 완료, bit7 내려옴) [1..2] 체중 (리틀 엔디언)
 *  - 미 체성분 체중계 (XMTZC02HM 등): 서비스 데이터 0x181B, 13바이트
 *      [0] 제어0(bit0 파운드) [1] 제어1(bit1 임피던스 있음, bit5 측정 완료, bit6 근, bit7 내려옴) … [11..12] 체중
 *  - 단위별 값: kg = 값/200, 파운드 = 값/100, 근 = 값/100 → 모두 kg 으로 바꿔 쓴다
 */
object ScaleParser {
    const val SERVICE_V1 = 0x181D
    const val SERVICE_V2 = 0x181B

    fun parse(device: FoundDevice, nowMillis: Long = System.currentTimeMillis()): ScaleReading? {
        device.serviceData[SERVICE_V2]?.takeIf { it.size >= 13 }?.let { d ->
            val c0 = d[0].toInt() and 0xFF
            val c1 = d[1].toInt() and 0xFF
            val raw = u16(d, 11)
            return ScaleReading(
                weightKg = toKg(raw, pounds = c0 and 0x01 != 0, catty = c1 and 0x40 != 0),
                stable = c1 and 0x20 != 0,
                removed = c1 and 0x80 != 0,
                receivedAtMillis = nowMillis,
            )
        }
        device.serviceData[SERVICE_V1]?.takeIf { it.size >= 3 }?.let { d ->
            val c = d[0].toInt() and 0xFF
            return ScaleReading(
                weightKg = toKg(u16(d, 1), pounds = c and 0x01 != 0, catty = c and 0x10 != 0),
                stable = c and 0x20 != 0,
                removed = c and 0x80 != 0,
                receivedAtMillis = nowMillis,
            )
        }
        return null
    }

    private fun u16(d: ByteArray, at: Int): Int = (d[at].toInt() and 0xFF) or ((d[at + 1].toInt() and 0xFF) shl 8)

    private fun toKg(raw: Int, pounds: Boolean, catty: Boolean): Double = when {
        pounds -> raw / 100.0 * 0.45359237
        catty -> raw / 100.0 * 0.5
        else -> raw / 200.0
    }
}
