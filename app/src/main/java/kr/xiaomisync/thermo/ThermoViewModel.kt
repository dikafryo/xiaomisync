package kr.xiaomisync.thermo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.withTimeoutOrNull

/* ===================== 화면 상태 ===================== */

enum class ThermoStep { FIND, CONNECTING, CONNECTED }

data class ThermoMessage(val text: String, val isError: Boolean)

data class ThermoUiState(
    val step: ThermoStep = ThermoStep.FIND,
    val scanning: Boolean = false,
    val foundDevices: List<FoundDevice> = emptyList(),
    val connectedName: String = "",
    val busyText: String? = null,
    val message: ThermoMessage? = null,
    val latest: ThermoReading? = null,
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

/** 온습도계 공통 화면 상태. [type] 으로 기기를 가리고, 기기별 차이는 [ThermoClient] 구현이 맡는다. */
class ThermoViewModel(app: Application, private val type: DeviceType) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ThermoUiState())
    val state: StateFlow<ThermoUiState> = _state.asStateFlow()

    private val finder = DeviceFinder(app, viewModelScope, type)
    private var readingJob: Job? = null
    private var session: GattSession? = null
    private var client: ThermoClient? = null

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
                showError("온습도계를 찾지 못했습니다. 온습도계를 휴대폰 가까이 두고, Mi Home 앱이 연결 중이면 닫은 뒤 다시 찾아 주세요.")
            },
        )
    }

    fun stopScan() = finder.stop()

    /* ----- ② 연결 ----- */

    fun connect(found: FoundDevice) {
        stopScan()
        _state.update { it.copy(step = ThermoStep.CONNECTING, connectedName = found.name, message = null) }
        viewModelScope.launch {
            val newSession = GattSession(getApplication(), found.device)
            try {
                newSession.connect()
            } catch (e: BleException) {
                newSession.close()
                _state.update { it.copy(step = ThermoStep.FIND) }
                showError(e.message ?: "기기에 연결하지 못했습니다.")
                return@launch
            }
            session = newSession
            client = createClient(newSession)
            _state.update { it.copy(step = ThermoStep.CONNECTED) }
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
        _state.value = ThermoUiState()
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
                device.readBattery()?.let { battery -> _state.update { it.copy(battery = battery) } }
                val firmware = device.readFirmware()
                _state.update { it.copy(firmware = firmware) }
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

    private fun onReading(reading: ThermoReading) {
        _state.update {
            it.copy(
                latest = reading,
                battery = reading.batteryPercent ?: it.battery,
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
                // 값과 함께 배터리가 오는 기기는 따로 읽을 것이 없다 (다음 값이 오면 갱신됨)
                device.readBattery()?.let { battery -> _state.update { it.copy(battery = battery) } }
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
        _state.update { it.copy(message = ThermoMessage(text, isError = true)) }
    }

    private fun createClient(session: GattSession): ThermoClient = when (type) {
        DeviceType.LYWSD03MMC -> Lywsd03Client(session)
        else -> MjhtClient(session)
    }

    override fun onCleared() {
        finder.stop()
        session?.close()
    }

    companion object {
        private const val FIRST_READING_TIMEOUT_MS = 15_000L
    }
}
