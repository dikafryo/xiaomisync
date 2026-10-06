package kr.xiaomisync.scale

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kr.xiaomisync.ble.DiagLog
import kr.xiaomisync.ble.BleScanner
import kr.xiaomisync.ble.FoundDevice
import kr.xiaomisync.device.DeviceType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class ScaleUiState(
    val listening: Boolean = false,
    val message: String? = null,
    /** 지금 체중계가 보내는 값 (올라서 있는 동안 계속 바뀐다) */
    val live: ScaleReading? = null,
    /** 이번에 측정이 끝난 값들 (최근 것이 앞) */
    val results: List<ScaleReading> = emptyList(),
)

/** 체중계: 연결하지 않고 광고만 듣는다. 측정이 끝난(숫자가 멈춘) 값을 기록에 남긴다. */
class ScaleViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ScaleUiState())
    val state: StateFlow<ScaleUiState> = _state.asStateFlow()

    private val scanner = BleScanner(app)
    private var timeoutJob: Job? = null

    fun startListening() {
        stopListening()
        val problem = scanner.start(
            onFound = ::onAdvertisement,
            onFailed = {
                stopListening()
                showError("블루투스 듣기에 실패했습니다. 블루투스와 위치(GPS)가 켜져 있는지 확인해 주세요.")
            },
        )
        if (problem != null) {
            showError(problem)
            return
        }
        _state.update { it.copy(listening = true, message = null) }
        // 배터리 소모를 막기 위해 일정 시간 뒤 멈춘다
        timeoutJob = viewModelScope.launch {
            delay(LISTEN_DURATION_MS)
            stopListening()
        }
    }

    fun stopListening() {
        timeoutJob?.cancel()
        timeoutJob = null
        scanner.stop()
        _state.update { it.copy(listening = false) }
    }

    fun showError(text: String) {
        DiagLog.add("화면 안내(오류): $text")
        _state.update { it.copy(message = text) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun onAdvertisement(device: FoundDevice) {
        if (!DeviceType.MI_SCALE.matches(device)) return
        val reading = ScaleParser.parse(device) ?: return
        _state.update { current ->
            val last = current.results.firstOrNull()
            // 측정이 끝난 값은 한 번만 남긴다 (체중계는 같은 값을 여러 번 보낸다)
            val isNewResult = reading.stable && !reading.removed && reading.weightKg > 0 &&
                (last == null || reading.receivedAtMillis - last.receivedAtMillis > SAME_RESULT_WINDOW_MS ||
                    abs(last.weightKg - reading.weightKg) >= 0.05)
            if (isNewResult) {
                val raw = device.serviceData.entries.joinToString(" ") { (id, d) -> "0x%04X=${DiagLog.hex(d)}".format(id) }
                DiagLog.add("체중 측정 완료 %.2f kg (${device.address} $raw)".format(reading.weightKg))
            }
            current.copy(
                live = reading,
                results = if (isNewResult) (listOf(reading) + current.results).take(MAX_RESULTS) else current.results,
            )
        }
    }

    override fun onCleared() {
        scanner.stop()
    }

    companion object {
        private const val LISTEN_DURATION_MS = 3 * 60_000L
        private const val SAME_RESULT_WINDOW_MS = 30_000L
        private const val MAX_RESULTS = 20
    }
}
