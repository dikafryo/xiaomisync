package kr.xiaomisync.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** 주변에서 찾은 블루투스 기기 한 대 */
data class FoundDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice,
)

/** 안드로이드 버전마다 필요한 블루투스 권한이 달라서 한 곳에 모아 둔다 */
object BlePermissions {
    val required: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasAll(context: Context): Boolean = required.all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
}

/** 주변 블루투스 기기 찾기. 결과는 이름이 있는 기기만 [onFound]로 알려 준다. */
@SuppressLint("MissingPermission") // 화면에서 BlePermissions.hasAll() 확인 후에만 호출
class BleScanner(context: Context) {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var callback: ScanCallback? = null

    /** 찾기를 시작한다. 시작할 수 없으면 사용자에게 보여줄 이유를 돌려준다. */
    fun start(onFound: (FoundDevice) -> Unit, onFailed: () -> Unit): String? {
        val bluetooth = adapter ?: return "이 휴대폰은 블루투스를 지원하지 않습니다."
        if (!bluetooth.isEnabled) return "블루투스가 꺼져 있습니다. 블루투스를 켠 뒤 다시 눌러 주세요."
        val scanner = bluetooth.bluetoothLeScanner
            ?: return "블루투스를 준비하지 못했습니다. 잠시 후 다시 눌러 주세요."

        stop()
        val newCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                toFoundDevice(result)?.let(onFound)
            }

            override fun onScanFailed(errorCode: Int) {
                onFailed()
            }
        }
        callback = newCallback

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, newCallback)
        return null
    }

    fun stop() {
        val current = callback ?: return
        callback = null
        try {
            adapter?.bluetoothLeScanner?.stopScan(current)
        } catch (e: IllegalStateException) {
            // 찾는 도중 사용자가 블루투스를 끈 경우: 이미 멈춘 상태이므로 할 일 없음
        }
    }

    private fun toFoundDevice(result: ScanResult): FoundDevice? {
        // 광고 신호 안의 이름을 먼저 쓴다(기기 객체의 이름은 아직 비어 있을 때가 많다)
        val name = result.scanRecord?.deviceName ?: result.device.name ?: return null
        return FoundDevice(name, result.device.address, result.rssi, result.device)
    }
}
