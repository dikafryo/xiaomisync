package kr.xiaomisync.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.xiaomisync.ble.DiagLog

/**
 * 진단 로그 화면: 블루투스로 찾고·보내고·받은 기록을 보여 주고 복사·공유한다.
 * 기기가 안 될 때 이 내용을 개발자에게 보내면 어느 단계에서 멈췄는지 알 수 있다.
 */
@Composable
fun DiagScreen(onBack: () -> Unit) {
    val lines by DiagLog.lines.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // 새 줄이 생기면 맨 아래로
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Tokens.gapM),
        verticalArrangement = Arrangement.spacedBy(Tokens.gapS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("진단 로그", style = TitleStyle)
                Text("최근 ${lines.size}줄 · 앱을 닫으면 지워집니다", style = SubStyle)
            }
            SecondaryButton(text = "‹ 돌아가기", onClick = onBack)
        }
        Text(
            "기기가 안 될 때: 안 되는 동작을 한 번 더 해 본 뒤 '공유'로 보내 주세요. 기기 주소(MAC)가 들어 있습니다.",
            style = SubStyle,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.gapS)) {
            SecondaryButton(text = "복사", modifier = Modifier.weight(1f), enabled = lines.isNotEmpty()) {
                copyToClipboard(context, report(context, lines))
            }
            SecondaryButton(text = "공유", modifier = Modifier.weight(1f), enabled = lines.isNotEmpty()) {
                share(context, report(context, lines))
            }
            SecondaryButton(text = "지우기", modifier = Modifier.weight(1f), enabled = lines.isNotEmpty(), onClick = DiagLog::clear)
        }
        SectionCard(modifier = Modifier.weight(1f)) {
            if (lines.isEmpty()) {
                Text("아직 기록이 없습니다. 기기를 고르고 '기기 찾기 시작'을 누르면 기록이 쌓입니다.", style = SubStyle)
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(lines) { line ->
                        Text(line, style = SubStyle.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp))
                    }
                }
            }
        }
    }
}

/** 보낼 내용: 앱 버전·휴대폰·안드로이드 버전을 머리에 붙인다 */
private fun report(context: Context, lines: List<String>): String {
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    val header = "XiaomiSync $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    return (listOf(header, "") + lines).joinToString("\n")
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("XiaomiSync 진단 로그", text))
    Toast.makeText(context, "진단 로그를 복사했습니다", Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "XiaomiSync 진단 로그")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "진단 로그 보내기"))
}
