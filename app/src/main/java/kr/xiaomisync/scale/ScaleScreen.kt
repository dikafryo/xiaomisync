package kr.xiaomisync.scale

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.xiaomisync.ble.BlePermissions
import kr.xiaomisync.device.DeviceType
import kr.xiaomisync.ui.BigValue
import kr.xiaomisync.ui.BodyStyle
import kr.xiaomisync.ui.BusyRow
import kr.xiaomisync.ui.DeviceHeader
import kr.xiaomisync.ui.LabeledValue
import kr.xiaomisync.ui.MessageBanner
import kr.xiaomisync.ui.PrimaryButton
import kr.xiaomisync.ui.SecondaryButton
import kr.xiaomisync.ui.SectionCard
import kr.xiaomisync.ui.SectionStyle
import kr.xiaomisync.ui.SubStyle
import kr.xiaomisync.ui.Tokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 체중계: 연결 없이 '측정 시작' → 체중계에 올라서기 → 숫자가 멈추면 기록 */
@Composable
fun ScaleScreen(viewModel: ScaleViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            viewModel.startListening()
        } else {
            viewModel.showError("'주변 기기'(또는 '위치') 권한을 허용해야 체중계 값을 받을 수 있습니다. 휴대폰 설정 > 애플리케이션에서 권한을 켜 주세요.")
        }
    }
    val onStartClick = {
        if (BlePermissions.hasAll(context)) viewModel.startListening() else permissionLauncher.launch(BlePermissions.required)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Tokens.gapM),
        verticalArrangement = Arrangement.spacedBy(Tokens.gapM),
    ) {
        DeviceHeader(title = DeviceType.MI_SCALE.displayName, model = DeviceType.MI_SCALE.model, onBack = onBack)
        state.message?.let { MessageBanner(text = it, isError = true, onClose = viewModel::dismissMessage) }

        SectionCard {
            Text("체중 재기", style = SectionStyle)
            Text(
                "1. 아래 '측정 시작'을 누릅니다. (체중계와 연결할 필요는 없습니다)\n2. 체중계에 올라서서 숫자가 멈출 때까지 가만히 서 있습니다.\n3. 측정이 끝나면 아래 기록에 남습니다.",
                style = BodyStyle,
            )
            if (state.listening) {
                BusyRow("체중계 신호를 듣는 중… (3분 동안)")
                SecondaryButton(text = "측정 멈추기", onClick = viewModel::stopListening)
            } else {
                PrimaryButton(text = "측정 시작", onClick = onStartClick)
            }
        }

        SectionCard {
            Text("지금 체중계", style = SectionStyle)
            val live = state.live
            Row {
                BigValue("체중", live?.let { "%.1f kg".format(it.weightKg) }, Modifier.weight(1f))
                BigValue("상태", live?.let { liveLabel(it) }, Modifier.weight(1f))
            }
            if (live == null) {
                Text("체중계에 올라서면 값이 나타납니다. 체중계 화면이 켜져 있어야 신호를 보냅니다.", style = SubStyle)
            }
        }

        if (state.results.isNotEmpty()) {
            SectionCard {
                Text("이번 측정 기록", style = SectionStyle)
                state.results.forEach { r -> LabeledValue(timeLabel(r.receivedAtMillis), "%.1f kg".format(r.weightKg)) }
                Text("앱을 닫으면 기록은 지워집니다.", style = SubStyle)
            }
        }
    }
}

private fun liveLabel(r: ScaleReading): String = when {
    r.removed -> "내려옴"
    r.stable -> "측정 완료"
    else -> "재는 중…"
}

private fun timeLabel(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(millis))
