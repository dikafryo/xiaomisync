package kr.xiaomisync.lywsd02

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kr.xiaomisync.ble.BlePermissions
import kr.xiaomisync.ui.BigValue
import kr.xiaomisync.ui.BusyRow
import kr.xiaomisync.ui.DEVICE_FLOW_STEPS
import kr.xiaomisync.ui.DeviceHeader
import kr.xiaomisync.ui.FindDeviceSection
import kr.xiaomisync.ui.LabeledValue
import kr.xiaomisync.ui.batteryLabel
import kr.xiaomisync.ui.MessageBanner
import kr.xiaomisync.ui.PrimaryButton
import kr.xiaomisync.ui.SecondaryButton
import kr.xiaomisync.ui.SectionCard
import kr.xiaomisync.ui.SectionStyle
import kr.xiaomisync.ui.StepIndicator
import kr.xiaomisync.ui.SubStyle
import kr.xiaomisync.ui.Tokens
import kr.xiaomisync.ui.formatKoreanDateTime
import java.util.TimeZone

/* ===================== 화면 전체 ===================== */

@Composable
fun Lywsd02Screen(viewModel: Lywsd02ViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            viewModel.startScan()
        } else {
            viewModel.showError("'주변 기기'(또는 '위치') 권한을 허용해야 시계를 찾을 수 있습니다. 휴대폰 설정 > 애플리케이션에서 권한을 켜 주세요.")
        }
    }
    val onFindClick = {
        if (BlePermissions.hasAll(context)) {
            viewModel.startScan()
        } else {
            permissionLauncher.launch(BlePermissions.required)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Tokens.gapM),
        verticalArrangement = Arrangement.spacedBy(Tokens.gapM),
    ) {
        DeviceHeader(title = "블루투스 디지털 시계", model = "LYWSD02", onBack = onBack)
        StepIndicator(
            steps = DEVICE_FLOW_STEPS,
            currentIndex = if (state.step == Lywsd02Step.CONNECTED) 2 else 1,
        )
        state.message?.let {
            MessageBanner(text = it.text, isError = it.isError, onClose = viewModel::dismissMessage)
        }
        when (state.step) {
            Lywsd02Step.FIND -> FindDeviceSection(
                noun = "시계",
                scanning = state.scanning,
                foundDevices = state.foundDevices,
                onFindClick = onFindClick,
                onStopClick = viewModel::stopScan,
                onConnectClick = viewModel::connect,
            )
            Lywsd02Step.CONNECTING -> SectionCard { BusyRow("${state.connectedName}에 연결하는 중… (최대 15초)") }
            Lywsd02Step.CONNECTED -> ConnectedSection(state, viewModel)
        }
    }
}

/* ===================== ③ 동기화 ===================== */

@Composable
private fun ConnectedSection(state: Lywsd02UiState, viewModel: Lywsd02ViewModel) {
    val isBusy = state.busyText != null

    SectionCard {
        Text("✓ ${state.connectedName} 연결됨", style = SectionStyle.copy(color = Tokens.success))
        state.busyText?.let { BusyRow(it) }
    }

    ClockCard(state.clock)
    PrimaryButton(text = "시계 시간 맞추기", enabled = !isBusy, onClick = viewModel::syncClock)

    SensorCard(state)
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.gapS)) {
        SecondaryButton(
            text = "다시 읽기",
            modifier = Modifier.weight(1f),
            enabled = !isBusy,
            onClick = viewModel::refreshAll,
        )
        SecondaryButton(
            text = "온도 단위 바꾸기",
            modifier = Modifier.weight(1f),
            enabled = !isBusy && state.unit != null,
            onClick = viewModel::toggleUnit,
        )
    }
    SecondaryButton(
        text = "연결 끊기",
        modifier = Modifier.padding(top = Tokens.gapS),
        enabled = !isBusy,
        onClick = viewModel::disconnect,
    )
}

@Composable
private fun ClockCard(clock: DeviceClock?) {
    val phoneNow = rememberPhoneClock()
    SectionCard {
        Text("시간", style = SectionStyle)
        LabeledValue("휴대폰 시간", formatKoreanDateTime(phoneNow))
        LabeledValue(
            "시계에 저장된 시간",
            clock?.let { "${formatKoreanDateTime(it)} (마지막으로 읽은 값)" } ?: "읽는 중…",
        )
        Text("버튼을 누르면 휴대폰 시간으로 시계를 맞춥니다.", style = SubStyle)
    }
}

@Composable
private fun SensorCard(state: Lywsd02UiState) {
    SectionCard {
        Text("시계 센서 값", style = SectionStyle)
        val sensor = state.sensor
        Row {
            BigValue("온도", sensor?.let { "%.1f℃".format(it.temperature) }, Modifier.weight(1f))
            BigValue("습도", sensor?.let { "${it.humidity}%" }, Modifier.weight(1f))
        }
        LabeledValue("배터리", batteryLabel(state.battery))
        LabeledValue("시계 화면 온도 단위", state.unit?.label ?: "읽는 중…")
    }
}

/** 1초마다 갱신되는 휴대폰 현재 시간 (시계와 비교해 보기 쉽게) */
@Composable
private fun rememberPhoneClock(): DeviceClock {
    val nowMillis = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMillis.longValue = System.currentTimeMillis()
        }
    }
    val millis = nowMillis.longValue
    return DeviceClock(millis / 1000, TimeZone.getDefault().getOffset(millis) / 3_600_000)
}
