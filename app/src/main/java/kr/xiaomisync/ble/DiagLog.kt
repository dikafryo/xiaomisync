package kr.xiaomisync.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 진단 로그: 블루투스로 무엇을 찾고·보내고·받았는지 시간순으로 남긴다.
 * 기기가 안 될 때 사용자가 '진단 로그' 화면에서 복사·공유해 개발자에게 보내는 용도.
 * 메모리에만 최근 [MAX_LINES] 줄을 둔다 — 파일로 쓰거나 밖으로 보내지 않는다.
 */
object DiagLog {
    private const val MAX_LINES = 400

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.KOREA)

    fun add(message: String) {
        val line = "${synchronized(timeFormat) { timeFormat.format(Date()) }} $message"
        _lines.update { (it + line).takeLast(MAX_LINES) }
    }

    fun clear() {
        _lines.value = emptyList()
    }

    /** 바이트 → "01 5C CB" (진단용) */
    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    /** 표준 16비트 UUID 는 0x2A19 처럼 짧게, 나머지는 전체 */
    fun shortUuid(uuid: UUID): String {
        val text = uuid.toString()
        return if (text.endsWith("-0000-1000-8000-00805f9b34fb") && text.startsWith("0000")) {
            "0x" + text.substring(4, 8).uppercase()
        } else {
            text
        }
    }
}
