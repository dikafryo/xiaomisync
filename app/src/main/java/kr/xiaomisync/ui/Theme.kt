package kr.xiaomisync.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.xiaomisync.clock.DeviceClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/* ===================== 1. 디자인 토큰 (색·간격·둥글기) ===================== */
// 색은 여기에서만 정의한다. 강조색 1개(샤오미 주황) + 회색 계열.

object Tokens {
    val accent = Color(0xFFE85D00)      // 흰 글자와 대비를 맞추려고 샤오미 주황보다 약간 진하게
    val accentSoft = Color(0xFFFFF1E8)
    val background = Color(0xFFF5F6F8)
    val surface = Color(0xFFFFFFFF)
    val border = Color(0xFFDDE1E6)
    val textMain = Color(0xFF1B1F24)
    val textSub = Color(0xFF4A525C)     // 보조 글씨도 충분히 진하게
    val success = Color(0xFF1E7B3C)
    val successSoft = Color(0xFFE8F5EC)
    val error = Color(0xFFC62828)
    val errorSoft = Color(0xFFFDECEC)

    val gapS = 8.dp
    val gapM = 16.dp
    val gapL = 24.dp
    val radius = 12.dp
}

@Composable
fun XiaomiSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Tokens.accent,
            onPrimary = Color.White,
            background = Tokens.background,
            surface = Tokens.surface,
            onSurface = Tokens.textMain,
            outline = Tokens.border,
        ),
        content = content,
    )
}

/* ===================== 2. 공통 화면 요소 ===================== */

val TitleStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tokens.textMain)
val SectionStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Tokens.textMain)
val BodyStyle = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, color = Tokens.textMain)
val SubStyle = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = Tokens.textSub)

/** 흰 바탕 카드: 화면의 모든 묶음은 이 모양을 쓴다 */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.radius),
        color = Tokens.surface,
        border = BorderStroke(1.dp, Tokens.border),
    ) {
        Column(
            modifier = Modifier.padding(Tokens.gapM),
            verticalArrangement = Arrangement.spacedBy(Tokens.gapS),
        ) { content() }
    }
}

/** 화면에서 가장 중요한 버튼 하나에만 쓴다 */
@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(Tokens.radius),
        colors = ButtonDefaults.buttonColors(containerColor = Tokens.accent),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(Tokens.radius),
        border = BorderStroke(1.dp, Tokens.border),
        contentPadding = PaddingValues(horizontal = Tokens.gapM),
    ) { Text(text, fontSize = 15.sp, color = Tokens.textMain) }
}

/** 성공/오류 안내. 색만으로 구분하지 않도록 기호(✓/!)와 말머리를 함께 붙인다. */
@Composable
fun MessageBanner(text: String, isError: Boolean, onClose: () -> Unit) {
    val (mark, color, background) = if (isError) {
        Triple("! 확인 필요", Tokens.error, Tokens.errorSoft)
    } else {
        Triple("✓ 완료", Tokens.success, Tokens.successSoft)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.radius),
        color = background,
        border = BorderStroke(1.dp, color),
    ) {
        Row(
            modifier = Modifier.padding(Tokens.gapM),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(mark, style = SectionStyle.copy(color = color, fontSize = 15.sp))
                Text(text, style = BodyStyle)
            }
            SecondaryButton(text = "닫기", onClick = onClose)
        }
    }
}

/** "처리 중…" 표시 */
@Composable
fun BusyRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = Tokens.accent,
        )
        Box(modifier = Modifier.padding(start = Tokens.gapS)) {
            Text(text, style = BodyStyle)
        }
    }
}

/** 지금 몇 번째 단계인지 보여주는 줄: ① 기기 선택 → ② 기기 찾기 → ③ 동기화 */
@Composable
fun StepIndicator(steps: List<String>, currentIndex: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.gapS),
    ) {
        steps.forEachIndexed { index, label ->
            val isCurrent = index == currentIndex
            val isDone = index < currentIndex
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(Tokens.radius),
                color = if (isCurrent) Tokens.accentSoft else Tokens.surface,
                border = BorderStroke(1.dp, if (isCurrent) Tokens.accent else Tokens.border),
            ) {
                val prefix = if (isDone) "✓" else CIRCLED_NUMBERS[index]
                Text(
                    text = "$prefix $label",
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                    style = SubStyle.copy(
                        color = if (isCurrent) Tokens.accent else Tokens.textSub,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

private val CIRCLED_NUMBERS = listOf("①", "②", "③", "④", "⑤")

/* ===================== 3. 날짜 표시 ===================== */

/** 2026. 10. 6.(화) 14:30:05 형식 */
fun formatKoreanDateTime(clock: DeviceClock): String {
    val format = SimpleDateFormat("yyyy. M. d.(E) HH:mm:ss", Locale.KOREA)
    format.timeZone = TimeZone.getTimeZone(gmtId(clock.timezoneHours))
    return format.format(Date(clock.epochSeconds * 1000))
}

private fun gmtId(hours: Int): String = if (hours >= 0) "GMT+$hours" else "GMT$hours"
