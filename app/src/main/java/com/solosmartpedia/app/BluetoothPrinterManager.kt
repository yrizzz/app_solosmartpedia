package com.solosmartpedia.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothPrinterManager(private val context: Context) {

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val btAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var socket: BluetoothSocket? = null
    private var connectedDevice: BluetoothDevice? = null

    val isConnected: Boolean get() = socket?.isConnected == true

    val connectedDeviceName: String get() =
        if (isConnected) connectedDevice?.name ?: "Printer" else ""

    // ── Device discovery ───────────────────────────────────────────────────

    fun isBluetoothAvailable(): Boolean = btAdapter != null

    fun isBluetoothEnabled(): Boolean = btAdapter?.isEnabled == true

    /** Returns list of paired Bluetooth devices (name + address) */
    fun getPairedDevices(): List<Pair<String, String>> {
        val adapter = btAdapter ?: return emptyList()
        return adapter.bondedDevices
            .map { Pair(it.name ?: "Unknown", it.address) }
            .sortedBy { it.first }
    }

    // ── Connection ─────────────────────────────────────────────────────────

    fun connect(
        address: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                disconnect()
                val device = btAdapter?.getRemoteDevice(address)
                    ?: return@launch withContext(Dispatchers.Main) { onError("Device not found") }

                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                btAdapter?.cancelDiscovery()
                s.connect()
                socket = s
                connectedDevice = device

                withContext(Dispatchers.Main) { onSuccess(device.name ?: address) }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Connection failed") }
            }
        }
    }

    fun disconnect() {
        runCatching { socket?.close() }
        socket = null
        connectedDevice = null
    }

    // ── Printing ───────────────────────────────────────────────────────────

    fun print(
        data: ByteArray,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val s = socket
        if (s == null || !s.isConnected) {
            onError("Printer tidak terhubung")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                s.outputStream.write(data)
                s.outputStream.flush()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: IOException) {
                socket = null
                connectedDevice = null
                withContext(Dispatchers.Main) { onError(e.message ?: "Print failed") }
            }
        }
    }

    fun printJson(
        json: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = print(EscPosHelper.buildFromJson(json), onSuccess, onError)

    fun printTest(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = print(EscPosHelper.buildTestPage(), onSuccess, onError)
}
