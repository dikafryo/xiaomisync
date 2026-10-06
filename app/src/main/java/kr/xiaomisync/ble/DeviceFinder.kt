package kr.xiaomisync.ble

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.xiaomisync.device.DeviceType

/**
 * ② 기기 찾기 공통 동작: 정해진 시간 동안 찾고, [type] 에 맞는 기기만 신호 세기 순으로 모은다.
 * 기기 화면의 ViewModel 이 하나씩 가지고 쓴다 (찾기 코드를 기기마다 복사하지 않으려고).
 */
class DeviceFinder(
    context: Context,
    private val scope: CoroutineScope,
    private val type: DeviceType,
    private val durationMs: Long = 15_000L,
) {
    private val scanner = BleScanner(context)
    private var timeoutJob: Job? = null

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _found = MutableStateFlow<List<FoundDevice>>(emptyList())
    val found: StateFlow<List<FoundDevice>> = _found.asStateFlow()

    /**
     * 찾기를 시작한다. 시작하지 못하면 사용자에게 보여 줄 이유를 [onError] 로 알린다.
     * 시간이 다 되었는데 하나도 못 찾으면 [onNothingFound] 를 부른다.
     */
    fun start(onError: (String) -> Unit, onNothingFound: () -> Unit) {
        stop()
        _found.value = emptyList()
        val problem = scanner.start(
            onFound = ::add,
            onFailed = {
                stop()
                onError("기기 찾기에 실패했습니다. 블루투스와 위치(GPS)가 켜져 있는지 확인해 주세요.")
            },
        )
        if (problem != null) {
            onError(problem)
            return
        }
        _scanning.value = true
        timeoutJob = scope.launch {
            delay(durationMs)
            stop()
            if (_found.value.isEmpty()) onNothingFound()
        }
    }

    fun stop() {
        timeoutJob?.cancel()
        timeoutJob = null
        scanner.stop()
        _scanning.value = false
    }

    private fun add(device: FoundDevice) {
        if (!type.matches(device)) return
        _found.update { current ->
            // 같은 기기는 신호 세기만 새로 고쳐 한 줄로 유지
            (current.filterNot { it.address == device.address } + device).sortedByDescending { it.rssi }
        }
    }
}
