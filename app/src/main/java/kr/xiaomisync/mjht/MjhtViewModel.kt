package kr.xiaomisync.mjht

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kr.xiaomisync.ble.BleException
import kr.xiaomisync.ble.BleScanner
import kr.xiaomisync.ble.FoundDevice
import kr.xiaomisync.ble.GattSession
import kr.xiaomisync.device.DeviceType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/* ===================== 화면 상태 ===================== */

enum class MjhtStep { FIND, CONNECTING, CONNECTED }

data class MjhtMessage(val text: String, val isError: Boolean)

data class MjhtUiState(
    val step: MjhtStep = MjhtStep.FIND,
    val scanning: Boolean = false,
    val foundDevices: List<FoundDevice> = emptyList(),
    val connectedName: String = "",
    val busyText: String? = null,
    val message: MjhtMessage? = null,
    val latest: MjhtReading? = null,
    /** 이번 연결 동안의 최저·최고 (연결을 끊으면 초기화) */
    val minTemperature: Double? = null,
    val maxTemperature: Double? = null,
    val minHumidity: Double? = null,
    val maxHumidity: Double? = null,
    val readingCount: Int = 0,
    val battery: Int? = null,
    val firmware: String? = null,
)

/* ===================== 동작 ===================== */

class MjhtViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(MjhtUiState())
    val state: StateFlow<MjhtUiState> = _state.asStateFlow()

    private val scanner = BleScanner(app)
    private var scanTimeoutJob: Job? = null
    private var readingJob: Job? = null
    private var session: GattSession? = null
    private var client: MjhtClient? = null

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
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_DURATION_MS)
            stopScan()
            if (_state.value.foundDevices.isEmpty()) {
                showError("온습도계를 찾지 못했습니다. 온습도계를 휴대폰 가까이 두고, Mi Home 앱이 연결 중이면 닫은 뒤 다시 찾아 주세요.")
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
        if (!DeviceType.LYWSDCGQ.matches(found.name)) return
        _state.update { current ->
            val others = current.foundDevices.filterNot { it.address == found.address }
            current.copy(foundDevices = (others + found).sortedByDescending { it.rssi })
        }
    }

    /* ----- ② 연결 ----- */

    fun connect(found: FoundDevice) {
        stopScan()
        _state.update { it.copy(step = MjhtStep.CONNECTING, connectedName = found.name, message = null) }
        viewModelScope.launch {
            val newSession = GattSession(getApplication(), found.device)
            try {
                newSession.connect()
            } catch (e: BleException) {
                newSession.close()
                _state.update { it.copy(step = MjhtStep.FIND) }
                showError(e.message ?: "기기에 연결하지 못했습니다.")
                return@launch
            }
            session = newSession
            client = MjhtClient(newSession)
            _state.update { it.copy(step = MjhtStep.CONNECTED) }
            watchDisconnect(newSession)
            startLiveReadings()
        }
    }

    fun disconnect() {
        readingJob?.cancel()
        readingJob = null
        session?.close()
        session = null
        client = null
        _state.value = MjhtUiState()
    }

    private fun watchDisconnect(watched: GattSession) {
        viewModelScope.launch {
            watched.connected.first { !it }
            if (session !== watched) return@launch // 사용자가 직접 끊은 경우
            disconnect()
            showError("온습도계와 연결이 끊어졌습니다. '기기 찾기 시작'을 눌러 다시 연결해 주세요.")
        }
    }

    /* ----- ③ 실시간 값 ----- */

    /** 값 받기를 켜고, 들어오는 값마다 화면과 최저·최고를 갱신한다. 배터리·펌웨어는 한 번 읽는다. */
    private fun startLiveReadings() {
        val device = client ?: return
        readingJob?.cancel()
        readingJob = viewModelScope.launch { device.readings.collect(::onReading) }

        _state.update { it.copy(busyText = "온도·습도 값을 기다리는 중…") }
        viewModelScope.launch {
            try {
                device.startReadings()
                val battery = device.readBattery()
                val firmware = device.readFirmware()
                _state.update { it.copy(battery = battery, firmware = firmware) }
                // 첫 값이 너무 늦으면 안내한다 (보통 2~5초 안에 온다)
                val first = withTimeoutOrNull(FIRST_READING_TIMEOUT_MS) { state.first { it.latest != null } }
                if (first == null) {
                    showError("온도·습도 값이 오지 않습니다. '연결 끊기' 후 다시 연결해 주세요.")
                }
            } catch (e: BleException) {
                showError(e.message ?: "값 받기를 켜지 못했습니다.")
            } finally {
                _state.update { it.copy(busyText = null) }
            }
        }
    }

    private fun onReading(reading: MjhtReading) {
        _state.update {
            it.copy(
                latest = reading,
                minTemperature = minOf(it.minTemperature ?: reading.temperature, reading.temperature),
                maxTemperature = maxOf(it.maxTemperature ?: reading.temperature, reading.temperature),
                minHumidity = minOf(it.minHumidity ?: reading.humidity, reading.humidity),
                maxHumidity = maxOf(it.maxHumidity ?: reading.humidity, reading.humidity),
                readingCount = it.readingCount + 1,
            )
        }
    }

    fun refreshBattery() {
        val device = client ?: return
        if (_state.value.busyText != null) return
        _state.update { it.copy(busyText = "배터리를 읽는 중…", message = null) }
        viewModelScope.launch {
            try {
                val battery = device.readBattery()
                _state.update { it.copy(battery = battery) }
            } catch (e: BleException) {
                showError(e.message ?: "배터리 값을 읽지 못했습니다.")
            } finally {
                _state.update { it.copy(busyText = null) }
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun showError(text: String) {
        _state.update { it.copy(message = MjhtMessage(text, isError = true)) }
    }

    override fun onCleared() {
        scanner.stop()
        session?.close()
    }

    companion object {
        private const val SCAN_DURATION_MS = 15_000L
        private const val FIRST_READING_TIMEOUT_MS = 15_000L
    }
}
