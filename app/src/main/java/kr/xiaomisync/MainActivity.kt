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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.xiaomisync.device.DeviceType
import kr.xiaomisync.clock.ClockScreen
import kr.xiaomisync.clock.ClockViewModel
import kr.xiaomisync.kettle.KettleScreen
import kr.xiaomisync.kettle.KettleViewModel
import kr.xiaomisync.scale.ScaleScreen
import kr.xiaomisync.scale.ScaleViewModel
import kr.xiaomisync.thermo.ThermoScreen
import kr.xiaomisync.thermo.ThermoViewModel
import kr.xiaomisync.ui.DeviceSelectScreen
import kr.xiaomisync.ui.DiagScreen
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
    var showDiag by rememberSaveable { mutableStateOf(false) }
    val selected = selectedName?.let { DeviceType.valueOf(it) }

    if (showDiag) {
        BackHandler { showDiag = false }
        DiagScreen(onBack = { showDiag = false })
        return
    }
    if (selected == null) {
        DeviceSelectScreen(onSelect = { selectedName = it.name }, onOpenDiag = { showDiag = true })
        return
    }
    DeviceScreen(type = selected, onBack = { selectedName = null })
}

/** 새 기기를 지원할 때 여기에 화면을 하나씩 추가한다 */
@Composable
private fun DeviceScreen(type: DeviceType, onBack: () -> Unit) {
    when (type) {
        // 시계(LYWSD02, MHO-C303)는 같은 명령·화면을 쓴다 (기기마다 ViewModel 은 따로 — key 로 구분)
        DeviceType.LYWSD02, DeviceType.MHO_C303 -> {
            val clockViewModel: ClockViewModel = viewModel(key = type.name) {
                ClockViewModel(checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]), type)
            }
            val leave = {
                // 다른 기기를 고를 수 있게 연결을 정리하고 돌아간다
                clockViewModel.stopScan()
                clockViewModel.disconnect()
                onBack()
            }
            BackHandler(onBack = leave)
            ClockScreen(type = type, viewModel = clockViewModel, onBack = leave)
        }
        // 온습도계는 화면·동작을 함께 쓴다 (기기마다 ViewModel 은 따로 — key 로 구분)
        DeviceType.LYWSDCGQ, DeviceType.LYWSD03MMC -> {
            val thermoViewModel: ThermoViewModel = viewModel(key = type.name) {
                ThermoViewModel(checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]), type)
            }
            val leave = {
                thermoViewModel.stopScan()
                thermoViewModel.disconnect()
                onBack()
            }
            BackHandler(onBack = leave)
            ThermoScreen(type = type, viewModel = thermoViewModel, onBack = leave)
        }
        DeviceType.KETTLE -> {
            val kettleViewModel: KettleViewModel = viewModel()
            val leave = {
                kettleViewModel.stopScan()
                kettleViewModel.disconnect()
                onBack()
            }
            BackHandler(onBack = leave)
            KettleScreen(viewModel = kettleViewModel, onBack = leave)
        }
        DeviceType.MI_SCALE -> {
            val scaleViewModel: ScaleViewModel = viewModel()
            val leave = {
                scaleViewModel.stopListening()
                onBack()
            }
            BackHandler(onBack = leave)
            ScaleScreen(viewModel = scaleViewModel, onBack = leave)
        }
        // 아직 '준비 중'인 기기는 선택 화면에서 누를 수 없다
        else -> LaunchedEffect(type) { onBack() }
    }
}
