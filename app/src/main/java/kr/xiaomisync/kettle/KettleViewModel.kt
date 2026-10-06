package kr.xiaomisync.kettle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kr.xiaomisync.ble.DiagLog
import kr.xiaomisync.ble.BleException
import kr.xiaomisync.ble.DeviceFinder
import kr.xiaomisync.ble.FoundDevice
import kr.xiaomisync.ble.GattSession
import kr.xiaomisync.device.DeviceType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* ===================== 화면 상태 ===================== */

enum class KettleStep { FIND, CONNECTING, CONNECTED }

data class KettleMessage(val text: String, val isError: Boolean)

data class KettleUiState(
    val step: KettleStep = KettleStep.FIND,
    val scanning: Boolean = false,
    val foundDevices: List<FoundDevice> = emptyList(),
    val connectedName: String = "",
    val busyText: String? = null,
    val message: KettleMessage? = null,
    val status: KettleStatus? = null,
)

/* ===================== 동작 ===================== */

class KettleViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(KettleUiState())
    val state: StateFlow<KettleUiState> = _state.asStateFlow()

    private val finder = DeviceFinder(app, viewModelScope, DeviceType.KETTLE)
    private var statusJob: Job? = null
    private var session: GattSession? = null
    private var client: KettleClient? = null

    init {
        viewModelScope.launch { finder.scanning.collect { v -> _state.update { it.copy(scanning = v) } } }
        viewModelScope.launch { finder.found.collect { v -> _state.update { it.copy(foundDevices = v) } } }
    }

    /* ----- ① 기기 찾기 ----- */

    fun startScan() {
        _state.update { it.copy(message = null) }
        finder.start(
            onError = ::showError,
            onNothingFound = {
                showError("주전자를 찾지 못했습니다. 주전자를 받침대에 올려 전원을 연결하고, Mi Home 앱이 연결 중이면 닫은 뒤 다시 찾아 주세요.")
            },
        )
    }

    fun stopScan() = finder.stop()

    /* ----- ② 연결 + 인증 ----- */

    fun connect(found: FoundDevice) {
        stopScan()
        _state.update { it.copy(step = KettleStep.CONNECTING, connectedName = found.name, message = null) }
        viewModelScope.launch {
            val newSession = GattSession(getApplication(), found.device)
            val newClient = KettleClient(newSession, found.address, found.productId ?: KettleClient.DEFAULT_PRODUCT_ID)
            try {
                newSession.connect()
                newClient.authenticateAndSubscribe()
            } catch (e: BleException) {
                newSession.close()
                _state.update { it.copy(step = KettleStep.FIND) }
                showError(e.message ?: "주전자에 연결하지 못했습니다.")
                return@launch
            }
            session = newSession
            client = newClient
            _state.update { it.copy(step = KettleStep.CONNECTED) }
            statusJob = viewModelScope.launch {
                newClient.status.collect { status -> _state.update { it.copy(status = status) } }
            }
            watchDisconnect(newSession)
        }
    }

    fun disconnect() {
        statusJob?.cancel()
        statusJob = null
        session?.close()
        session = null
        client = null
        _state.value = KettleUiState()
    }

    private fun watchDisconnect(watched: GattSession) {
        viewModelScope.launch {
            watched.connected.first { !it }
            if (session !== watched) return@launch // 사용자가 직접 끊은 경우
            disconnect()
            showError("주전자와 연결이 끊어졌습니다. '기기 찾기 시작'을 눌러 다시 연결해 주세요.")
        }
    }

    /* ----- ③ 보온 설정 ----- */

    fun setKeepWarm(type: KeepWarmType, temperature: Int) = runWithClient("보온 설정을 바꾸는 중…") { kettle ->
        kettle.setKeepWarm(type, temperature)
        showSuccess("보온을 ${type.label}, ${temperature}℃ 로 맞췄습니다. 보온은 주전자의 보온 버튼을 눌러야 시작됩니다.")
    }

    fun setKeepWarmHours(hours: Double) = runWithClient("보온 시간을 바꾸는 중…") { kettle ->
        kettle.setKeepWarmHours(hours)
        showSuccess("보온 유지 시간을 ${hoursLabel(hours)}(으)로 맞췄습니다.")
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun showError(text: String) {
        DiagLog.add("화면 안내(오류): $text")
        _state.update { it.copy(message = KettleMessage(text, isError = true)) }
    }

    private fun showSuccess(text: String) {
        _state.update { it.copy(message = KettleMessage(text, isError = false)) }
    }

    private fun runWithClient(busyText: String, work: suspend (KettleClient) -> Unit) {
        val kettle = client ?: return
        if (_state.value.busyText != null) return
        _state.update { it.copy(busyText = busyText, message = null) }
        viewModelScope.launch {
            try {
                work(kettle)
            } catch (e: BleException) {
                showError(e.message ?: "작업에 실패했습니다.")
            } finally {
                _state.update { it.copy(busyText = null) }
            }
        }
    }

    override fun onCleared() {
        finder.stop()
        session?.close()
    }
}

/** 1.5 → "1시간 30분", 0 → "보온 안 함" */
fun hoursLabel(hours: Double): String {
    if (hours <= 0.0) return "보온 안 함"
    val whole = hours.toInt()
    val half = hours - whole >= 0.5
    return listOfNotNull(if (whole > 0) "${whole}시간" else null, if (half) "30분" else null).joinToString(" ")
}
