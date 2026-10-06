package kr.xiaomisync.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.xiaomisync.device.DeviceType

/** 앱을 켜면 가장 먼저 나오는 화면: 기기 그림을 보고 연결할 기기를 고른다 (2열) */
@Composable
fun DeviceSelectScreen(onSelect: (DeviceType) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Tokens.gapM),
        horizontalArrangement = Arrangement.spacedBy(Tokens.gapM),
        verticalArrangement = Arrangement.spacedBy(Tokens.gapM),
    ) {
        fullWidth {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.gapS)) {
                Text("샤오미 기기 연결", style = TitleStyle)
                Text(
                    "Mi Home 앱 없이 블루투스로 바로 연결합니다.\n가지고 있는 기기와 같은 그림을 눌러 주세요.",
                    style = SubStyle,
                )
            }
        }
        fullWidth {
            StepIndicator(steps = DEVICE_FLOW_STEPS, currentIndex = 0)
        }
        items(DeviceType.entries) { type ->
            DeviceTypeCard(type = type, onClick = { onSelect(type) })
        }
        fullWidth {
            WishLink()
        }
        fullWidth {
            Text(
                "· 모델명은 기기 뒷면이나 상자에 적혀 있습니다.\n· '준비 중' 기기는 이후 업데이트에서 추가됩니다.\n· Mi Home 앱이 같은 기기에 연결되어 있으면 연결이 안 될 수 있으니 먼저 닫아 주세요.",
                style = SubStyle,
            )
        }
    }
}

/** 연결하고 싶은 기기가 목록에 없을 때: 다운로드 사이트의 요청 방명록으로 보낸다 */
@Composable
private fun WishLink() {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { uriHandler.openUri(WISH_URL) },
        shape = RoundedCornerShape(Tokens.radius),
        color = Tokens.accentSoft,
    ) {
        Column(modifier = Modifier.padding(Tokens.gapM), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("원하는 기기가 없나요?", style = SectionStyle)
            Text("기기 모델명을 남겨 주세요. 요청이 많은 기기부터 추가합니다. 요청 남기기 ›", style = SubStyle.copy(color = Tokens.accent))
        }
    }
}

private const val WISH_URL = "https://device.sw4u.kr/#wish"

/** 2열 격자에서 한 줄을 다 쓰는 항목 (제목·안내문) */
private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** 기기 화면들이 같이 쓰는 단계 이름 */
val DEVICE_FLOW_STEPS = listOf("기기 선택", "기기 찾기", "동기화")

/** 기기 그림 → 모델명 → 기기 이름 순서로 쌓은 카드 */
@Composable
private fun DeviceTypeCard(type: DeviceType, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = type.available, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(Tokens.radius),
        color = Tokens.surface,
        border = BorderStroke(if (type.available) 1.5.dp else 1.dp, if (type.available) Tokens.accent else Tokens.border),
    ) {
        Column(
            modifier = Modifier.padding(Tokens.gapS),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Image(
                painter = painterResource(type.imageRes),
                contentDescription = "${type.displayName} 그림",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .alpha(if (type.available) 1f else 0.45f),
            )
            Text(type.model, style = SectionStyle, textAlign = TextAlign.Center)
            Text(type.displayName, style = SubStyle, textAlign = TextAlign.Center)
            val (label, color, soft) = if (type.available) {
                Triple("선택", Tokens.accent, Tokens.accentSoft)
            } else {
                Triple("준비 중", Tokens.textSub, Tokens.background)
            }
            Text(
                label,
                style = SubStyle.copy(color = color, fontSize = 13.sp),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .background(soft, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
    }
}
