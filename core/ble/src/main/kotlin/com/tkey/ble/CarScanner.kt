package com.tkey.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "TKey.Scan"

/**
 * Discovers the Tesla matching `VinHash.localName(vin)` over BLE.
 *
 * Uses [BluetoothAdapter.startDiscovery] (the Settings-style API) rather than
 * `BluetoothLeScanner.startScan` — on some OEM Bluetooth stacks the latter
 * silently drops Tesla advertisements while the former finds them fine.
 *
 * `startDiscovery` runs in ~12 s bursts and stops itself; we restart automatically
 * until the flow is cancelled.
 */
class CarScanner(private val ctx: Context) {

    sealed class Event {
        data class Match(val beacon: Beacon) : Event()
        data class Other(val beacon: Beacon) : Event()
    }

    /**
     * Emits every BLE device the adapter discovers, regardless of local-name. Use this when a
     * single scan loop needs to feed multiple consumers (e.g. proximity service watching N VINs).
     * Each consumer is responsible for filtering against its own [VinHash.localName] target.
     */
    @SuppressLint("MissingPermission")
    fun discoverRaw(): Flow<Beacon> = callbackFlow {
        val adapter: BluetoothAdapter? = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            close(IllegalStateException("Bluetooth disabled"))
            return@callbackFlow
        }
        Log.i(TAG, "Raw discovery starting")

        val finished = Channel<Unit>(capacity = Channel.UNLIMITED)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                        )
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                        if (device != null) {
                            trySend(Beacon(device.address, name ?: "(no name)", rssi.toInt()))
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        finished.trySend(Unit)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)

        val loop = launch {
            while (isActive) {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                val started = adapter.startDiscovery()
                if (!started) {
                    close(IllegalStateException("startDiscovery returned false"))
                    return@launch
                }
                finished.receive()
                Log.i(TAG, "Pass finished; restarting")
            }
        }

        awaitClose {
            Log.i(TAG, "Stopping discovery")
            loop.cancel()
            runCatching { adapter.cancelDiscovery() }
            runCatching { ctx.unregisterReceiver(receiver) }
        }
    }

    fun discover(vin: String): Flow<Event> {
        val target = VinHash.localName(vin)
        Log.i(TAG, "Discovering target=$target")
        return discoverRaw().map { beacon ->
            Log.i(TAG, "FOUND ${beacon.address} name='${beacon.localName}' rssi=${beacon.rssi}")
            if (beacon.localName == target) Event.Match(beacon) else Event.Other(beacon)
        }
    }
}
