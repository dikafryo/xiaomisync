package kr.xiaomisync.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.xiaomisync.device.DeviceType

/** 앱을 켜면 가장 먼저 나오는 화면: 연결할 기기 종류를 고른다 */
@Composable
fun DeviceSelectScreen(onSelect: (DeviceType) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Tokens.gapM),
        verticalArrangement = Arrangement.spacedBy(Tokens.gapM),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.gapS)) {
                Text("샤오미 기기 연결", style = TitleStyle)
                Text(
                    "Mi Home 앱 없이 블루투스로 바로 연결합니다.\n연결할 기기를 눌러 주세요.",
                    style = SubStyle,
                )
            }
        }
        item {
            StepIndicator(steps = DEVICE_FLOW_STEPS, currentIndex = 0)
        }
        items(DeviceType.entries) { type ->
            DeviceTypeCard(type = type, onClick = { onSelect(type) })
        }
        item {
            Text(
                "· '준비 중' 기기는 이후 업데이트에서 추가됩니다.\n· Mi Home 앱이 같은 기기에 연결되어 있으면 연결이 안 될 수 있으니 먼저 닫아 주세요.",
                style = SubStyle,
            )
        }
    }
}

/** 기기 화면들이 같이 쓰는 단계 이름 */
val DEVICE_FLOW_STEPS = listOf("기기 선택", "기기 찾기", "동기화")

@Composable
private fun DeviceTypeCard(type: DeviceType, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = type.available, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(Tokens.radius),
        color = if (type.available) Tokens.surface else Tokens.background,
        border = BorderStroke(1.dp, Tokens.border),
    ) {
        Row(
            modifier = Modifier.padding(Tokens.gapM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(type.displayName, style = SectionStyle)
                Text("모델명 ${type.model}", style = SubStyle)
                Text(type.description, style = SubStyle)
            }
            if (type.available) {
                Text("선택 ›", style = SectionStyle.copy(color = Tokens.accent, fontSize = 15.sp))
            } else {
                Text("준비 중", style = SubStyle)
            }
        }
    }
}
