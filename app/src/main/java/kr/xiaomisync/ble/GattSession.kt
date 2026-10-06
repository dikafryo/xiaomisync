package kr.xiaomisync.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** 사용자에게 그대로 보여줄 수 있는 한국어 메시지를 담는 블루투스 오류 */
class BleException(message: String) : Exception(message)

/** 기기가 보내 준 알림(notify) 한 건 */
class BleNotification(val uuid: UUID, val value: ByteArray)

/**
 * 블루투스 기기 하나와의 연결.
 *
 * 안드로이드 GATT는 "요청 → 콜백"이 짝을 이루고, 앞 요청이 끝나기 전에 다음 요청을 보내면
 * 조용히 무시된다. 그래서 모든 요청을 Mutex로 한 줄 세우고, 콜백이 올 때까지 기다리는
 * suspend 함수로 감쌌다. 기기별 코드(예: Lywsd02Client)는 이 클래스만 쓰면 된다.
 */
@SuppressLint("MissingPermission") // 권한은 화면에서 미리 확인한 뒤에만 이 클래스를 쓴다
class GattSession(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    private var gatt: BluetoothGatt? = null
    private val operationLock = Mutex()

    @Volatile
    private var pending: CompletableDeferred<ByteArray>? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _notifications = MutableSharedFlow<BleNotification>(extraBufferCapacity = 32)
    val notifications: SharedFlow<BleNotification> = _notifications.asSharedFlow()

    /* ===================== 공개 기능 ===================== */

    /**
     * 연결 + 기능 목록 읽기. 안드로이드는 첫 연결이 오류 133 으로 바로 끊기는 일이 흔해서
     * 실패하면 연결을 정리하고 잠시 뒤 한 번 더 시도한다.
     */
    suspend fun connect() {
        try {
            connectOnce()
        } catch (first: BleException) {
            releaseGatt()
            delay(RETRY_DELAY_MS)
            connectOnce()
        }
    }

    private suspend fun connectOnce() {
        runOperation(CONNECT_TIMEOUT_MS, "기기에 연결하지 못했습니다.") {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            gatt != null
        }
    }

    private fun releaseGatt() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connected.value = false
    }

    suspend fun read(uuid: UUID): ByteArray {
        val characteristic = findCharacteristic(uuid)
        return runOperation(OPERATION_TIMEOUT_MS, "기기에서 값을 읽지 못했습니다.") {
            requireGatt().readCharacteristic(characteristic)
        }
    }

    suspend fun write(uuid: UUID, data: ByteArray) {
        val characteristic = findCharacteristic(uuid)
        runOperation(OPERATION_TIMEOUT_MS, "기기에 값을 쓰지 못했습니다.") {
            writeCharacteristicCompat(characteristic, data)
        }
    }

    /** 기기가 값을 바뀔 때마다 보내도록(notify) 켠다. 값은 [notifications]로 들어온다. */
    suspend fun enableNotifications(uuid: UUID) {
        val characteristic = findCharacteristic(uuid)
        val descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID)
            ?: throw BleException("이 기기는 실시간 값 전송을 지원하지 않습니다.")

        if (!requireGatt().setCharacteristicNotification(characteristic, true)) {
            throw BleException("실시간 값 전송을 켜지 못했습니다.")
        }
        runOperation(OPERATION_TIMEOUT_MS, "실시간 값 전송을 켜지 못했습니다.") {
            writeDescriptorCompat(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }
    }

    fun close() {
        pending?.completeExceptionally(BleException("연결을 끊었습니다."))
        releaseGatt()
    }

    /* ===================== 내부 처리 ===================== */

    /** 요청 하나를 보내고, 짝이 되는 콜백이 [pending]을 채울 때까지 기다린다. */
    private suspend fun runOperation(
        timeoutMs: Long,
        failMessage: String,
        start: () -> Boolean,
    ): ByteArray = operationLock.withLock {
        val result = CompletableDeferred<ByteArray>()
        pending = result
        try {
            if (!start()) throw BleException(failMessage)
            withTimeoutOrNull(timeoutMs) { result.await() }
                ?: throw BleException("기기가 응답하지 않습니다. 기기를 휴대폰 가까이 두고 다시 시도해 주세요.")
        } finally {
            pending = null
        }
    }

    private fun requireGatt(): BluetoothGatt =
        gatt ?: throw BleException("기기와 연결되어 있지 않습니다.")

    private fun findCharacteristic(uuid: UUID): BluetoothGattCharacteristic =
        requireGatt().services
            .flatMap { it.characteristics }
            .firstOrNull { it.uuid == uuid }
            ?: throw BleException("이 기기에서 필요한 기능을 찾지 못했습니다. 선택한 기기 종류가 맞는지 확인해 주세요.")

    // 안드로이드 13(API 33)부터 쓰기 함수가 바뀌어서 버전에 따라 나눠 호출한다
    @Suppress("DEPRECATION")
    private fun writeCharacteristicCompat(ch: BluetoothGattCharacteristic, data: ByteArray): Boolean {
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireGatt().writeCharacteristic(ch, data, type) == BluetoothStatusCodes.SUCCESS
        } else {
            ch.writeType = type
            ch.value = data
            requireGatt().writeCharacteristic(ch)
        }
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptorCompat(descriptor: BluetoothGattDescriptor, data: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireGatt().writeDescriptor(descriptor, data) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = data
            requireGatt().writeDescriptor(descriptor)
        }

    private fun finishPending(status: Int, value: ByteArray, failMessage: String) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            pending?.complete(value)
        } else {
            pending?.completeExceptionally(BleException(failMessage))
        }
    }

    /* ===================== 블루투스 콜백 ===================== */

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                // 연결만으로는 기능 목록을 모르므로, 기능 목록까지 받은 뒤에 '연결 완료'로 본다
                if (!gatt.discoverServices()) {
                    pending?.completeExceptionally(BleException("기기의 기능 목록을 읽지 못했습니다."))
                }
                return
            }
            _connected.value = false
            pending?.completeExceptionally(
                BleException("기기와 연결이 끊어졌습니다. 기기를 휴대폰 가까이 두고 다시 시도해 주세요.")
            )
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) _connected.value = true
            finishPending(status, ByteArray(0), "기기의 기능 목록을 읽지 못했습니다.")
        }

        // 안드로이드 13 이상
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            finishPending(status, value, "기기에서 값을 읽지 못했습니다.")
        }

        // 안드로이드 12 이하
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            finishPending(status, characteristic.value ?: ByteArray(0), "기기에서 값을 읽지 못했습니다.")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            finishPending(status, ByteArray(0), "기기에 값을 쓰지 못했습니다.")
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            finishPending(status, ByteArray(0), "실시간 값 전송을 켜지 못했습니다.")
        }

        // 안드로이드 13 이상
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            _notifications.tryEmit(BleNotification(characteristic.uuid, value))
        }

        // 안드로이드 12 이하
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = characteristic.value ?: return
            _notifications.tryEmit(BleNotification(characteristic.uuid, value.copyOf()))
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val OPERATION_TIMEOUT_MS = 8_000L
        private const val RETRY_DELAY_MS = 800L

        // 블루투스 표준: 알림 켜기/끄기 설정 칸(Client Characteristic Configuration)
        private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
