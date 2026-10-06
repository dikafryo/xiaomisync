package kr.xiaomisync.kettle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.xiaomisync.ble.BlePermissions
import kr.xiaomisync.device.DeviceType
import kr.xiaomisync.ui.BigValue
import kr.xiaomisync.ui.BusyRow
import kr.xiaomisync.ui.DEVICE_FLOW_STEPS
import kr.xiaomisync.ui.DeviceHeader
import kr.xiaomisync.ui.FindDeviceSection
import kr.xiaomisync.ui.LabeledValue
import kr.xiaomisync.ui.MessageBanner
import kr.xiaomisync.ui.PrimaryButton
import kr.xiaomisync.ui.SecondaryButton
import kr.xiaomisync.ui.SectionCard
import kr.xiaomisync.ui.SectionStyle
import kr.xiaomisync.ui.StepIndicator
import kr.xiaomisync.ui.SubStyle
import kr.xiaomisync.ui.Tokens
import kotlin.math.roundToInt

/** YM-K1501 주전자: 찾기 → 연결·인증 → 상태 보기 + 보온 설정 */
@Composable
fun KettleScreen(viewModel: KettleViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            viewModel.startScan()
        } else {
            viewModel.showError("'주변 기기'(또는 '위치') 권한을 허용해야 주전자를 찾을 수 있습니다. 휴대폰 설정 > 애플리케이션에서 권한을 켜 주세요.")
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
        DeviceHeader(title = DeviceType.KETTLE.displayName, model = DeviceType.KETTLE.model, onBack = onBack)
        StepIndicator(steps = DEVICE_FLOW_STEPS, currentIndex = if (state.step == KettleStep.CONNECTED) 2 else 1)
        state.message?.let {
            MessageBanner(text = it.text, isError = it.isError, onClose = viewModel::dismissMessage)
        }
        when (state.step) {
            KettleStep.FIND -> FindDeviceSection(
                noun = "주전자",
                scanning = state.scanning,
                foundDevices = state.foundDevices,
                onFindClick = onFindClick,
                onStopClick = viewModel::stopScan,
                onConnectClick = viewModel::connect,
            )
            KettleStep.CONNECTING -> SectionCard { BusyRow("${state.connectedName}에 연결·인증하는 중… (최대 25초)") }
            KettleStep.CONNECTED -> ConnectedSection(state, viewModel)
        }
    }
}

@Composable
private fun ConnectedSection(state: KettleUiState, viewModel: KettleViewModel) {
    val status = state.status
    val isBusy = state.busyText != null

    SectionCard {
        Text("✓ ${state.connectedName} 연결됨", style = SectionStyle.copy(color = Tokens.success))
        state.busyText?.let { BusyRow(it) }
    }

    SectionCard {
        Text("주전자 상태", style = SectionStyle)
        Row {
            BigValue("지금 물 온도", status?.let { "${it.currentTemperature}℃" }, Modifier.weight(1f))
            BigValue("상태", status?.action?.label, Modifier.weight(1f))
        }
        LabeledValue(
            "보온",
            status?.let {
                if (it.keepWarmOn) "켜짐 · ${it.keepWarmTemperature}℃ · ${it.keepWarmElapsedMinutes}분째" else "꺼짐 (설정 ${it.keepWarmTemperature}℃)"
            } ?: "상태를 기다리는 중…",
        )
        Text("끓이기와 보온 시작은 주전자 본체 버튼으로 합니다. 여기서는 보온 방식·온도·시간을 바꿀 수 있습니다.", style = SubStyle)
    }

    KeepWarmCard(status = status, enabled = !isBusy && status != null, onApply = viewModel::setKeepWarm)
    KeepWarmTimeCard(enabled = !isBusy && status != null, onApply = viewModel::setKeepWarmHours)

    SecondaryButton(text = "연결 끊기", onClick = viewModel::disconnect)
}

@Composable
private fun KeepWarmCard(status: KettleStatus?, enabled: Boolean, onApply: (KeepWarmType, Int) -> Unit) {
    var temperature by remember(status?.keepWarmTemperature) {
        mutableFloatStateOf((status?.keepWarmTemperature ?: 50).coerceIn(40, 95).toFloat())
    }
    var type by remember(status?.keepWarmType) { mutableStateOf(status?.keepWarmType ?: KeepWarmType.BOIL_THEN_COOL) }

    SectionCard {
        Text("보온 온도 · 방식", style = SectionStyle)
        LabeledValue("보온 온도", "${temperature.roundToInt()}℃")
        Slider(
            value = temperature,
            onValueChange = { temperature = it },
            valueRange = 40f..95f,
            steps = 54, // 1℃ 단위
            enabled = enabled,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.gapS)) {
            KeepWarmType.entries.forEach { option ->
                SecondaryButton(
                    text = (if (option == type) "● " else "○ ") + option.label,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    onClick = { type = option },
                )
            }
        }
        PrimaryButton(text = "보온 설정 바꾸기", enabled = enabled) { onApply(type, temperature.roundToInt()) }
    }
}

@Composable
private fun KeepWarmTimeCard(enabled: Boolean, onApply: (Double) -> Unit) {
    var halfHours by remember { mutableFloatStateOf(24f) } // 기본 12시간
    SectionCard {
        Text("보온 유지 시간", style = SectionStyle)
        LabeledValue("보온 시간", hoursLabel(halfHours.roundToInt() / 2.0))
        Slider(
            value = halfHours,
            onValueChange = { halfHours = it },
            valueRange = 0f..24f,
            steps = 23, // 30분 단위
            enabled = enabled,
        )
        SecondaryButton(text = "보온 시간 바꾸기", enabled = enabled) { onApply(halfHours.roundToInt() / 2.0) }
    }
}
