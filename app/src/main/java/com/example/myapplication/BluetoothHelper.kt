package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID

object BluetoothHelper {

    private const val TAG = "BluetoothHelper"

    // Standard SPP UUID - this is correct for HC-05
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var bluetoothAdapter: BluetoothAdapter? = null
    var btSocket: BluetoothSocket? = null
        private set

    val isConnected: Boolean
        get() = btSocket?.isConnected == true

    @SuppressLint("MissingPermission")
    fun initialize(context: Context): Boolean {
        Log.d(TAG, "Initializing Bluetooth...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ BLUETOOTH_CONNECT permission not granted")
                return false
            }
        }

        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
            if (bluetoothManager == null) {
                Log.e(TAG, "❌ BluetoothManager not available")
                return false
            }

            bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter == null) {
                Log.e(TAG, "❌ BluetoothAdapter not available")
                return false
            }

            if (!bluetoothAdapter!!.isEnabled) {
                Log.e(TAG, "❌ Bluetooth is not enabled")
                return false
            }

            Log.i(TAG, "✅ Bluetooth initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing Bluetooth: ${e.message}", e)
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(context: Context, deviceAddress: String): Boolean {
        Log.d(TAG, "Attempting to connect to device: $deviceAddress")

        if (bluetoothAdapter == null) {
            Log.e(TAG, "❌ Bluetooth not initialized")
            return false
        }

        // Close existing connection if any
        if (btSocket?.isConnected == true) {
            Log.i(TAG, "Closing existing connection before creating new one")
            close()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ BLUETOOTH_CONNECT permission not granted")
                return false
            }
        }

        // Check if device is paired
        val pairedDevices = bluetoothAdapter?.bondedDevices
        val isPaired = pairedDevices?.any { it.address == deviceAddress } ?: false

        if (!isPaired) {
            Log.e(TAG, "❌ Device $deviceAddress is not paired. Please pair in Bluetooth settings first")
            return false
        }

        val device: BluetoothDevice = try {
            bluetoothAdapter!!.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Invalid Bluetooth address: $deviceAddress", e)
            return false
        }

        try {
            // Cancel discovery to improve connection reliability
            if (bluetoothAdapter!!.isDiscovering) {
                bluetoothAdapter!!.cancelDiscovery()
                Thread.sleep(100) // Small delay after canceling discovery
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel discovery: ${e.message}")
        }

        Log.d(TAG, "Creating socket and attempting connection...")

        return try {
            Log.i(TAG, "Attempting to connect to ${device.name ?: deviceAddress}")
            val tempSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)

            // Set socket timeout
            tempSocket.connect()
            btSocket = tempSocket

            Log.i(TAG, "✅ Bluetooth connection established with ${device.name ?: deviceAddress}")
            true
        } catch (e: IOException) {
            Log.e(TAG, "❌ Connection failed to $deviceAddress: ${e.message}")

            // Try fallback connection method
            try {
                Log.i(TAG, "Trying fallback connection method...")
                val fallbackSocket = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
                fallbackSocket.connect()
                btSocket = fallbackSocket
                Log.i(TAG, "✅ Fallback connection successful")
                true
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "❌ Fallback connection also failed: ${fallbackEx.message}")
                cleanup()
                false
            }
        }
    }

    fun sendCommand(command: String): Boolean {
        if (!isConnected) {
            Log.w(TAG, "Cannot send command: Not connected")
            return false
        }
        btSocket?.outputStream?.write((command.trim() + "\n").toByteArray())

        return try {
            btSocket?.outputStream?.write(command.toByteArray())
            btSocket?.outputStream?.flush()
            Log.d(TAG, "✅ Command sent: ${command.trim()}")
            true
        } catch (e: IOException) {
            Log.e(TAG, "❌ Failed to send command '${command.trim()}': ${e.message}")
            false
        }
    }

    private fun cleanup() {
        try {
            btSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        } finally {
            btSocket = null
        }
    }

    fun close() {
        Log.i(TAG, "Closing Bluetooth connection...")
        cleanup()
        Log.i(TAG, "Bluetooth connection closed.")
    }
}
