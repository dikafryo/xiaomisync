package kr.xiaomisync.lywsd02

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kr.xiaomisync.ble.BleException
import kr.xiaomisync.ble.BleScanner
import kr.xiaomisync.ble.FoundDevice
import kr.xiaomisync.ble.GattSession
import kr.xiaomisync.device.DeviceType
import kr.xiaomisync.ui.formatKoreanDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* ===================== 화면 상태 ===================== */

enum class Lywsd02Step { FIND, CONNECTING, CONNECTED }

data class UiMessage(val text: String, val isError: Boolean)

data class Lywsd02UiState(
    val step: Lywsd02Step = Lywsd02Step.FIND,
    val scanning: Boolean = false,
    val foundDevices: List<FoundDevice> = emptyList(),
    val connectedName: String = "",
    val busyText: String? = null,
    val message: UiMessage? = null,
    val sensor: SensorReading? = null,
    val battery: Int? = null,
    val unit: TemperatureUnit? = null,
    val clock: DeviceClock? = null,
)

/* ===================== 동작 ===================== */

class Lywsd02ViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(Lywsd02UiState())
    val state: StateFlow<Lywsd02UiState> = _state.asStateFlow()

    private val scanner = BleScanner(app)
    private var scanTimeoutJob: Job? = null
    private var session: GattSession? = null
    private var client: Lywsd02Client? = null

    /* ----- ① 기기 찾기 ----- */

    fun startScan() {
        stopScan()
        _state.update { it.copy(foundDevices = emptyList(), message = null) }

        val problem = scanner.start(
            onFound = ::addFoundDevice,
            onFailed = {
                stopScan()
                showError("기기 찾기에 실패했습니다. 블루투스와 위치(GPS)가 켜져 있는지 확인해 주세요.")
            },
        )
        if (problem != null) {
            showError(problem)
            return
        }
        _state.update { it.copy(scanning = true) }
        // 배터리 소모를 막기 위해 일정 시간 뒤 자동으로 멈춘다
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_DURATION_MS)
            stopScan()
            if (_state.value.foundDevices.isEmpty()) {
                showError("시계를 찾지 못했습니다. 시계를 휴대폰 가까이 두고, Mi Home 앱이 연결 중이면 닫은 뒤 다시 찾아 주세요.")
            }
        }
    }

    fun stopScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        scanner.stop()
        _state.update { it.copy(scanning = false) }
    }

    private fun addFoundDevice(found: FoundDevice) {
        if (!DeviceType.LYWSD02.matches(found)) return
        _state.update { current ->
            // 같은 기기는 신호 세기만 새로 고쳐 한 줄로 유지
            val others = current.foundDevices.filterNot { it.address == found.address }
            current.copy(foundDevices = (others + found).sortedByDescending { it.rssi })
        }
    }

    /* ----- ② 연결 ----- */

    fun connect(found: FoundDevice) {
        stopScan()
        _state.update {
            it.copy(step = Lywsd02Step.CONNECTING, connectedName = found.name, message = null)
        }
        viewModelScope.launch {
            val newSession = GattSession(getApplication(), found.device)
            try {
                newSession.connect()
            } catch (e: BleException) {
                newSession.close()
                _state.update { it.copy(step = Lywsd02Step.FIND) }
                showError(e.message ?: "기기에 연결하지 못했습니다.")
                return@launch
            }
            session = newSession
            client = Lywsd02Client(newSession)
            _state.update { it.copy(step = Lywsd02Step.CONNECTED) }
            watchDisconnect(newSession)
            refreshAll()
        }
    }

    fun disconnect() {
        session?.close()
        session = null
        client = null
        _state.value = Lywsd02UiState()
    }

    private fun watchDisconnect(watched: GattSession) {
        viewModelScope.launch {
            watched.connected.first { !it }
            if (session !== watched) return@launch // 사용자가 직접 끊은 경우
            disconnect()
            showError("시계와 연결이 끊어졌습니다. '기기 찾기 시작'을 눌러 다시 연결해 주세요.")
        }
    }

    /* ----- ③ 읽기 · 시간 맞추기 ----- */

    /**
     * 시간 → 배터리 → 단위 → 온습도 순서로 읽는다. 각 값은 읽히는 대로 화면에 바로 반영한다.
     * 온습도는 알림을 기다려야 해서 가장 잘 실패하므로 마지막에 둔다 — 실패해도 시간 맞추기는 쓸 수 있다.
     */
    fun refreshAll() = runWithClient("시계 정보를 읽는 중…") { device ->
        val clock = device.readClock()
        _state.update { it.copy(clock = clock) }
        val battery = device.readBattery()
        _state.update { it.copy(battery = battery) }
        val unit = device.readUnit()
        _state.update { it.copy(unit = unit) }
        val sensor = device.readSensor()
        _state.update { it.copy(sensor = sensor) }
    }

    fun syncClock() = runWithClient("시간을 맞추는 중…") { device ->
        device.syncClockToPhone()
        // 실제로 들어갔는지 시계에서 다시 읽어 확인한다
        val clock = device.readClock()
        _state.update { it.copy(clock = clock) }
        showSuccess("시계를 ${formatKoreanDateTime(clock)}(으)로 맞췄습니다.")
    }

    fun toggleUnit() = runWithClient("단위를 바꾸는 중…") { device ->
        val next = if (_state.value.unit == TemperatureUnit.FAHRENHEIT) {
            TemperatureUnit.CELSIUS
        } else {
            TemperatureUnit.FAHRENHEIT
        }
        device.writeUnit(next)
        _state.update { it.copy(unit = device.readUnit()) }
        showSuccess("시계의 온도 단위를 ${next.label}(으)로 바꿨습니다.")
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun showError(text: String) {
        _state.update { it.copy(message = UiMessage(text, isError = true)) }
    }

    /** 처리 중 표시를 켜고(중복 클릭 방지), 오류는 사용자 문장으로 바꿔 보여준다. */
    private fun runWithClient(busyText: String, work: suspend (Lywsd02Client) -> Unit) {
        val device = client ?: return
        if (_state.value.busyText != null) return
        _state.update { it.copy(busyText = busyText, message = null) }
        viewModelScope.launch {
            try {
                work(device)
            } catch (e: BleException) {
                showError(e.message ?: "작업에 실패했습니다.")
            } finally {
                _state.update { it.copy(busyText = null) }
            }
        }
    }

    private fun showSuccess(text: String) {
        _state.update { it.copy(message = UiMessage(text, isError = false)) }
    }

    override fun onCleared() {
        scanner.stop()
        session?.close()
    }

    companion object {
        private const val SCAN_DURATION_MS = 15_000L
    }
}
