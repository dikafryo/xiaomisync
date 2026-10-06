package kr.xiaomisync.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kr.xiaomisync.ble.FoundDevice

/* 기기 화면들이 함께 쓰는 조각: 머리글, ② 기기 찾기 단계, 값 표시 */

@Composable
fun DeviceHeader(title: String, model: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = TitleStyle)
            Text("모델명 $model", style = SubStyle)
        }
        SecondaryButton(text = "‹ 기기 선택", onClick = onBack)
    }
}

/**
 * ② 기기 찾기: 안내 → 찾기 버튼 → 찾은 기기 목록.
 * [noun] 은 화면에 쓰는 기기 이름 (예: "시계", "온습도계").
 * [variantLabel] 은 같은 종류 안의 신형 모델 표시 (예: LYWSD02MMC) — 없으면 null.
 */
@Composable
fun FindDeviceSection(
    noun: String,
    scanning: Boolean,
    foundDevices: List<FoundDevice>,
    onFindClick: () -> Unit,
    onStopClick: () -> Unit,
    onConnectClick: (FoundDevice) -> Unit,
    variantLabel: (FoundDevice) -> String? = { null },
) {
    SectionCard {
        Text("$noun 찾기", style = SectionStyle)
        Text(
            "1. 휴대폰의 블루투스를 켭니다.\n2. ${noun}를 휴대폰 가까이(1m 이내) 둡니다.\n3. 아래 버튼을 누르고, 목록에 ${noun}가 나오면 '연결'을 누릅니다.",
            style = BodyStyle,
        )
        if (scanning) {
            BusyRow("주변의 ${noun}를 찾는 중… (15초)")
            SecondaryButton(text = "찾기 멈추기", onClick = onStopClick)
        } else {
            PrimaryButton(text = "기기 찾기 시작", onClick = onFindClick)
        }
    }

    if (foundDevices.isEmpty()) {
        if (!scanning) {
            Text("아직 찾은 ${noun}가 없습니다. '기기 찾기 시작'을 눌러 주세요.", style = SubStyle)
        }
        return
    }
    SectionCard {
        Text("찾은 $noun ${foundDevices.size}대", style = SectionStyle)
        Text("여러 대가 보이면 신호가 강한(위쪽) 것이 가장 가까운 ${noun}입니다.", style = SubStyle)
        foundDevices.forEach { found ->
            FoundDeviceRow(found = found, variant = variantLabel(found), onConnectClick = { onConnectClick(found) })
        }
    }
}

@Composable
private fun FoundDeviceRow(found: FoundDevice, variant: String?, onConnectClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(found.name, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
            variant?.let { Text(it, style = SubStyle.copy(color = Tokens.accent)) }
            Text("${found.address} · 신호 ${signalLabel(found.rssi)}", style = SubStyle)
        }
        SecondaryButton(text = "연결", onClick = onConnectClick)
    }
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -60 -> "강함"
    rssi >= -80 -> "보통"
    else -> "약함"
}

@Composable
fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, style = SubStyle)
        Text(value, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun BigValue(label: String, value: String?, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = SubStyle)
        Text(value ?: "–", style = TitleStyle.copy(fontSize = 30.sp))
    }
}

/** 배터리 표시: 20% 이하면 교체 안내를 붙인다 */
fun batteryLabel(percent: Int?): String =
    percent?.let { "$it%" + if (it <= 20) " (교체 필요)" else "" } ?: "읽는 중…"
