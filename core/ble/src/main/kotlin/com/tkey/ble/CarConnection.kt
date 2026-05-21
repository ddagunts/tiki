package com.tkey.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.util.UUID

private const val TAG = "TKey.Conn"

/** Tesla BLE GATT service UUID — discoverable only after connecting. */
val TESLA_SERVICE_UUID: UUID = UUID.fromString("00000211-b2d1-43f0-9b88-960cebf8b91e")
/** "To-vehicle" characteristic — write commands here. */
val TESLA_TX_UUID: UUID = UUID.fromString("00000212-b2d1-43f0-9b88-960cebf8b91e")
/** "From-vehicle" characteristic — subscribe for notifications. */
val TESLA_RX_UUID: UUID = UUID.fromString("00000213-b2d1-43f0-9b88-960cebf8b91e")
/** Client Characteristic Configuration Descriptor (standard BLE). */
private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/** Per Tesla's Go reference: cap messages at 1024 bytes and fragment by MTU − 3. */
private const val MAX_MESSAGE_SIZE = 1024
private const val TARGET_MTU = 517 // BLE 4.2+ extended MTU max
private const val MAX_RETRIES = 3
private const val RETRY_DELAY_MS = 400L
/** Generic GATT error from the Android stack — transient on first contact, retry. */
private const val GATT_ERROR_133 = 133

/**
 * Owns a single GATT connection to one Tesla and the length-prefixed framing
 * on top of the [TESLA_TX_UUID]/[TESLA_RX_UUID] characteristics.
 *
 * Frame: 2-byte big-endian length, followed by `length` bytes of payload.
 * Outbound payloads are fragmented to (MTU − 3); inbound chunks are reassembled.
 */
class CarConnection(
    private val ctx: Context,
    private val device: BluetoothDevice,
) {

    sealed class State {
        data object Connecting : State()
        data object DiscoveringServices : State()
        data object EnablingNotifications : State()
        data class Ready(val mtu: Int) : State()
        data class Failed(val reason: String) : State()
        data object Disconnected : State()
    }

    private val scope: CoroutineScope = MainScope()

    private val _state = MutableStateFlow<State>(State.Connecting)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Full reassembled messages from the vehicle. */
    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incoming: SharedFlow<ByteArray> = _incoming.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var blockSize: Int = 20 // default ATT MTU 23 − 3 header
    private var retriesLeft: Int = MAX_RETRIES

    /** Serializes writes; each write waits for onCharacteristicWrite before the next. */
    private val writeMutex = Mutex()
    // @Volatile because the GATT callback (binder thread) reads it after the caller
    // (MainScope coroutine thread) sets it. Always written inside writeMutex.withLock,
    // so the only race is callback-vs-caller visibility, which @Volatile resolves.
    @Volatile private var writeAck: CompletableDeferred<Int>? = null
    private val inboundBuffer = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun connect() {
        if (gatt != null) {
            Log.w(TAG, "connect() called while already connected/connecting; ignoring")
            return
        }
        Log.i(TAG, "connectGatt to ${device.address}")
        retriesLeft = MAX_RETRIES
        openGatt()
    }

    @SuppressLint("MissingPermission")
    private fun openGatt() {
        gatt = device.connectGatt(ctx, /* autoConnect = */ false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.i(TAG, "disconnect")
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        // Force the terminal state ourselves so consumers awaiting state.first { it !is Ready }
        // don't hang if disconnect() was called mid-handshake or mid-retry, where the GATT
        // callback may never fire a STATE_DISCONNECTED for us.
        _state.value = State.Disconnected
        scope.cancel()
    }

    /**
     * Send a single protocol message. Wraps with 2-byte big-endian length and
     * fragments to (MTU − 3) byte chunks, awaiting the write ack between chunks.
     */
    @SuppressLint("MissingPermission")
    suspend fun send(payload: ByteArray) {
        require(payload.size <= MAX_MESSAGE_SIZE) { "payload too large: ${payload.size}" }
        val g = gatt ?: error("Not connected")
        val tx = txChar ?: error("TX characteristic not bound")
        if (_state.value !is State.Ready) error("Connection not ready: ${_state.value}")

        val framed = ByteArray(2 + payload.size).apply {
            this[0] = ((payload.size ushr 8) and 0xff).toByte()
            this[1] = (payload.size and 0xff).toByte()
            payload.copyInto(this, destinationOffset = 2)
        }

        writeMutex.withLock {
            var off = 0
            while (off < framed.size) {
                val end = minOf(off + blockSize, framed.size)
                val chunk = framed.copyOfRange(off, end)
                val ack = CompletableDeferred<Int>()
                writeAck = ack
                writeChunk(g, tx, chunk)
                val status = ack.await() // blocks until onCharacteristicWrite
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    error("BLE write failed at offset $off/${framed.size}: status=$status")
                }
                off = end
            }
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    @Suppress("DEPRECATION")
    private fun writeChunk(g: BluetoothGatt, char: BluetoothGattCharacteristic, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            char.value = bytes
            g.writeCharacteristic(char)
        }
    }

    private val callback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "onConnectionStateChange status=$status newState=$newState retriesLeft=$retriesLeft")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = State.DiscoveringServices
                    g.requestMtu(TARGET_MTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    runCatching { g.close() }
                    if (status == GATT_ERROR_133 && retriesLeft > 0) {
                        retriesLeft--
                        Log.w(TAG, "Got status 133, retrying connect in ${RETRY_DELAY_MS}ms (left=$retriesLeft)")
                        scope.launch {
                            kotlinx.coroutines.delay(RETRY_DELAY_MS)
                            openGatt()
                        }
                    } else {
                        _state.value = State.Disconnected
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            val effective = minOf(mtu, MAX_MESSAGE_SIZE) - 3
            blockSize = effective.coerceAtLeast(20)
            Log.i(TAG, "MTU=$mtu blockSize=$blockSize status=$status")
            g.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.i(TAG, "onServicesDiscovered status=$status services=${g.services.size}")
            val service = g.getService(TESLA_SERVICE_UUID)
            if (service == null) {
                _state.value = State.Failed("Tesla service not present")
                return
            }
            txChar = service.getCharacteristic(TESLA_TX_UUID)
            rxChar = service.getCharacteristic(TESLA_RX_UUID)
            if (txChar == null || rxChar == null) {
                _state.value = State.Failed("Tesla TX/RX characteristic missing")
                return
            }
            _state.value = State.EnablingNotifications
            g.setCharacteristicNotification(rxChar, true)
            val cccd = rxChar!!.getDescriptor(CCCD_UUID)
            if (cccd == null) {
                _state.value = State.Failed("CCCD descriptor missing on RX char")
                return
            }
            writeCccd(g, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }

        @SuppressLint("MissingPermission", "NewApi")
        @Suppress("DEPRECATION")
        private fun writeCccd(g: BluetoothGatt, desc: BluetoothGattDescriptor, value: ByteArray) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(desc, value)
            } else {
                desc.value = value
                g.writeDescriptor(desc)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, desc: BluetoothGattDescriptor, status: Int) {
            Log.i(TAG, "onDescriptorWrite uuid=${desc.uuid} status=$status")
            if (desc.uuid == CCCD_UUID) {
                _state.value = if (status == BluetoothGatt.GATT_SUCCESS) {
                    State.Ready(blockSize + 3)
                } else {
                    State.Failed("CCCD write failed: $status")
                }
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic) {
            handleInbound(char.value ?: return)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            char: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleInbound(value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicWrite(g: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
            writeAck?.complete(status)
            writeAck = null
        }
    }

    private fun handleInbound(chunk: ByteArray) {
        inboundBuffer.write(chunk)
        // Pop as many complete messages as the buffer contains.
        while (true) {
            val buf = inboundBuffer.toByteArray()
            if (buf.size < 2) return
            val length = ((buf[0].toInt() and 0xff) shl 8) or (buf[1].toInt() and 0xff)
            if (buf.size < 2 + length) return
            val message = buf.copyOfRange(2, 2 + length)
            val rest = buf.copyOfRange(2 + length, buf.size)
            inboundBuffer.reset()
            inboundBuffer.write(rest)
            Log.i(TAG, "RX message len=$length")
            scope.launch(Dispatchers.Default) {
                _incoming.emit(message)
            }
        }
    }

    companion object {
        fun fromMac(ctx: Context, macAddress: String): CarConnection {
            val adapter = ctx.getSystemService(BluetoothManager::class.java).adapter
            return CarConnection(ctx, adapter.getRemoteDevice(macAddress))
        }
    }
}
