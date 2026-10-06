package kr.xiaomisync.thermo

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
import kr.xiaomisync.device.DeviceType
import kr.xiaomisync.ui.BigValue
import kr.xiaomisync.ui.BusyRow
import kr.xiaomisync.ui.DEVICE_FLOW_STEPS
import kr.xiaomisync.ui.DeviceHeader
import kr.xiaomisync.ui.FindDeviceSection
import kr.xiaomisync.ui.LabeledValue
import kr.xiaomisync.ui.MessageBanner
import kr.xiaomisync.ui.SecondaryButton
import kr.xiaomisync.ui.SectionCard
import kr.xiaomisync.ui.SectionStyle
import kr.xiaomisync.ui.StepIndicator
import kr.xiaomisync.ui.SubStyle
import kr.xiaomisync.ui.Tokens
import kr.xiaomisync.ui.batteryLabel

/** 온습도계 공통 화면 (LYWSDCGQ/01ZM, LYWSD03MMC): 찾기 → 연결 → 실시간 온도·습도 */
@Composable
fun ThermoScreen(type: DeviceType, viewModel: ThermoViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            viewModel.startScan()
        } else {
            viewModel.showError("'주변 기기'(또는 '위치') 권한을 허용해야 온습도계를 찾을 수 있습니다. 휴대폰 설정 > 애플리케이션에서 권한을 켜 주세요.")
        }
    }
    val onFindClick = {
        if (BlePermissions.hasAll(context)) viewModel.startScan() else permissionLauncher.launch(BlePermissions.required)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Tokens.gapM),
        verticalArrangement = Arrangement.spacedBy(Tokens.gapM),
    ) {
        DeviceHeader(title = type.displayName, model = type.model, onBack = onBack)
        StepIndicator(steps = DEVICE_FLOW_STEPS, currentIndex = if (state.step == ThermoStep.CONNECTED) 2 else 1)
        state.message?.let {
            MessageBanner(text = it.text, isError = it.isError, onClose = viewModel::dismissMessage)
        }
        when (state.step) {
            ThermoStep.FIND -> FindDeviceSection(
                noun = "온습도계",
                scanning = state.scanning,
                foundDevices = state.foundDevices,
                onFindClick = onFindClick,
                onStopClick = viewModel::stopScan,
                onConnectClick = viewModel::connect,
            )
            ThermoStep.CONNECTING -> SectionCard { BusyRow("${state.connectedName}에 연결하는 중… (최대 15초)") }
            ThermoStep.CONNECTED -> ConnectedSection(state, viewModel)
        }
    }
}

@Composable
private fun ConnectedSection(state: ThermoUiState, viewModel: ThermoViewModel) {
    val isBusy = state.busyText != null

    SectionCard {
        Text("✓ ${state.connectedName} 연결됨", style = SectionStyle.copy(color = Tokens.success))
        state.busyText?.let { BusyRow(it) }
    }

    SectionCard {
        Text("지금 온도·습도", style = SectionStyle)
        val latest = state.latest
        Row {
            BigValue("온도", latest?.let { "%.1f℃".format(it.temperature) }, Modifier.weight(1f))
            BigValue("습도", latest?.let { "%.1f%%".format(it.humidity) }, Modifier.weight(1f))
        }
        Text(receivedLabel(latest?.receivedAtMillis), style = SubStyle)
    }

    SectionCard {
        Text("연결한 뒤 최저 · 최고", style = SectionStyle)
        LabeledValue("온도", rangeLabel(state.minTemperature, state.maxTemperature, "℃"))
        LabeledValue("습도", rangeLabel(state.minHumidity, state.maxHumidity, "%"))
        Text("받은 값 ${state.readingCount}개 · 연결을 끊으면 처음부터 다시 셉니다.", style = SubStyle)
    }

    SectionCard {
        Text("기기 정보", style = SectionStyle)
        LabeledValue("배터리", batteryLabel(state.battery))
        LabeledValue("펌웨어", state.firmware ?: "–")
    }

    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.gapS)) {
        SecondaryButton(
            text = "배터리 다시 읽기",
            modifier = Modifier.weight(1f),
            enabled = !isBusy,
            onClick = viewModel::refreshBattery,
        )
        SecondaryButton(
            text = "연결 끊기",
            modifier = Modifier.weight(1f),
            onClick = viewModel::disconnect,
        )
    }
}

/** "3초 전에 받음" — 1초마다 다시 계산해 값이 계속 오는지 보이게 한다 */
@Composable
private fun receivedLabel(receivedAtMillis: Long?): String {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now.longValue = System.currentTimeMillis()
        }
    }
    if (receivedAtMillis == null) return "값을 기다리는 중… (온습도계가 몇 초마다 보냅니다)"
    val seconds = ((now.longValue - receivedAtMillis) / 1000).coerceAtLeast(0)
    return "${seconds}초 전에 받음 · 몇 초마다 자동으로 새로 고칩니다"
}

private fun rangeLabel(min: Double?, max: Double?, unit: String): String =
    if (min == null || max == null) "–" else "%.1f".format(min) + unit + " ~ " + "%.1f".format(max) + unit
