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
import android.os.ParcelUuid

/**
 * 주변에서 찾은 블루투스 기기 한 대.
 * [productId] 는 샤오미 광고(MiBeacon, 서비스 데이터 0xFE95)에 실린 제품 번호 — 이름 없이 광고하는
 * 신형 기기(예: LYWSD02MMC)도 이 번호로 알아본다. 샤오미 광고가 아니면 null.
 */
data class FoundDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice,
    val productId: Int? = null,
    /** 광고에 실린 서비스 데이터 (체중계처럼 연결 없이 광고만 읽는 기기용). 키는 16비트 서비스 번호 */
    val serviceData: Map<Int, ByteArray> = emptyMap(),
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

/** 주변 블루투스 기기 찾기. 이름이 있거나 샤오미 광고를 보내는 기기를 [onFound]로 알려 준다. */
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
        val productId = miBeaconProductId(result)
        // 광고 신호 안의 이름을 먼저 쓴다(기기 객체의 이름은 아직 비어 있을 때가 많다)
        val name = result.scanRecord?.deviceName
            ?: result.device.name
            ?: productId?.let { "샤오미 기기 (제품 0x%04X)".format(it) }
            ?: return null
        val serviceData = result.scanRecord?.serviceData.orEmpty()
            .mapKeys { (uuid, _) -> (uuid.uuid.mostSignificantBits ushr 32).toInt() and 0xFFFF }
        return FoundDevice(name, result.device.address, result.rssi, result.device, productId, serviceData)
    }

    /** MiBeacon: 서비스 데이터 0xFE95 = 프레임 제어(2바이트) + 제품 번호(2바이트, 리틀 엔디언) + … */
    private fun miBeaconProductId(result: ScanResult): Int? {
        val data = result.scanRecord?.getServiceData(MI_SERVICE) ?: return null
        if (data.size < 4) return null
        return (data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)
    }

    companion object {
        private val MI_SERVICE: ParcelUuid = ParcelUuid.fromString("0000fe95-0000-1000-8000-00805f9b34fb")
    }
}
