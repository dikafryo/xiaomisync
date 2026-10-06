package kr.xiaomisync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.xiaomisync.device.DeviceType
import kr.xiaomisync.lywsd02.Lywsd02Screen
import kr.xiaomisync.lywsd02.Lywsd02ViewModel
import kr.xiaomisync.mjht.MjhtScreen
import kr.xiaomisync.mjht.MjhtViewModel
import kr.xiaomisync.ui.DeviceSelectScreen
import kr.xiaomisync.ui.Tokens
import kr.xiaomisync.ui.XiaomiSyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XiaomiSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    color = Tokens.background,
                ) { AppNavigation() }
            }
        }
    }
}

/** 첫 화면은 항상 '기기 선택'. 기기를 고르면 그 기기 전용 화면으로 넘어간다. */
@Composable
private fun AppNavigation() {
    // 화면을 돌려도 선택이 유지되도록 이름(문자열)으로 저장
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { DeviceType.valueOf(it) }

    if (selected == null) {
        DeviceSelectScreen(onSelect = { selectedName = it.name })
        return
    }
    DeviceScreen(type = selected, onBack = { selectedName = null })
}

/** 새 기기를 지원할 때 여기에 화면을 하나씩 추가한다 */
@Composable
private fun DeviceScreen(type: DeviceType, onBack: () -> Unit) {
    when (type) {
        DeviceType.LYWSD02 -> {
            val lywsd02ViewModel: Lywsd02ViewModel = viewModel()
            val leave = {
                // 다른 기기를 고를 수 있게 연결을 정리하고 돌아간다
                lywsd02ViewModel.stopScan()
                lywsd02ViewModel.disconnect()
                onBack()
            }
            BackHandler(onBack = leave)
            Lywsd02Screen(viewModel = lywsd02ViewModel, onBack = leave)
        }
        DeviceType.LYWSDCGQ -> {
            val mjhtViewModel: MjhtViewModel = viewModel()
            val leave = {
                mjhtViewModel.stopScan()
                mjhtViewModel.disconnect()
                onBack()
            }
            BackHandler(onBack = leave)
            MjhtScreen(viewModel = mjhtViewModel, onBack = leave)
        }
        // 아직 '준비 중'인 기기는 선택 화면에서 누를 수 없다
        else -> LaunchedEffect(type) { onBack() }
    }
}
