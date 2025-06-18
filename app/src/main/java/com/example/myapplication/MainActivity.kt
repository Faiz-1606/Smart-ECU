package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent

import android.content.pm.PackageManager
import android.os.*
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.nio.charset.Charset

class MainActivity : ComponentActivity() {

    private lateinit var ignitionStatusText: TextView
    private lateinit var bluetoothStatusText: TextView
    private lateinit var logText: TextView
    private lateinit var ignitionToggleBtn: Button
    private lateinit var testCallBtn: Button

    private val bluetoothDeviceAddress = "00:23:10:01:24:19"
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        var engineState: EngineState = EngineState.UNKNOWN
    }

    private var isReaderRunning = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            log("All permissions granted")
            initializeApp()
        } else {
            log("Some permissions denied")
            Toast.makeText(this, "Bluetooth and call permissions are required", Toast.LENGTH_LONG).show()
            bluetoothStatusText.text = "Permissions missing"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ignitionStatusText = findViewById(R.id.ignitionStatus)
        bluetoothStatusText = findViewById(R.id.bluetoothStatus)
        logText = findViewById(R.id.logText)
        ignitionToggleBtn = findViewById(R.id.toggleIgnitionButton)
        testCallBtn = findViewById(R.id.testCallButton)

        ignitionToggleBtn.setOnClickListener { toggleIgnition() }
        testCallBtn.setOnClickListener { sendCommand("CALL_INCOMING\n") }

        checkPermissionsAndStart()
        requestDefaultDialer()

    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            log("Requesting permissions")
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            log("Permissions already granted")
            initializeApp()
        }
    }
    private fun requestDefaultDialer() {
        val telecomManager = getSystemService(TELECOM_SERVICE) as android.telecom.TelecomManager
        if (telecomManager.defaultDialerPackage != packageName) {
            val intent = Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            intent.putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            startActivity(intent)
            log("🔔 Requested to become default dialer")
        } else {
            log("✅ Already default dialer")
        }
    }

    private fun initializeApp() {
        log("Initializing Bluetooth...")
        if (!BluetoothHelper.initialize(this)) {
            bluetoothStatusText.text = "Bluetooth init failed"
            return
        }

        bluetoothStatusText.text = "Connecting..."

        Thread {
            val connected = BluetoothHelper.connect(this, bluetoothDeviceAddress)
            runOnUiThread {
                if (connected) {
                    bluetoothStatusText.text = "Connected: $bluetoothDeviceAddress"
                    log("Bluetooth connected")
                    startBluetoothReader()
                    handler.postDelayed({ sendCommand("STATUS\n") }, 1000)
                } else {
                    bluetoothStatusText.text = "Connection failed"
                }
            }
        }.start()
    }

    private fun toggleIgnition() {
        if (!BluetoothHelper.isConnected) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            return
        }

        val command = when (engineState) {
            EngineState.ON -> "IGNITION_OFF\n"
            EngineState.OFF, EngineState.UNKNOWN -> "IGNITION_ON\n"
        }
        sendCommand(command)
    }

    private fun sendCommand(cmd: String) {
        log("Sending: $cmd")
        if (BluetoothHelper.sendCommand(cmd)) {
            log("Command sent successfully")
        } else {
            log("Failed to send command")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothReader() {
        if (isReaderRunning || !BluetoothHelper.isConnected) return
        isReaderRunning = true

        Thread {
            val input: InputStream? = BluetoothHelper.btSocket?.inputStream
            if (input == null) {
                log("Input stream is null")
                return@Thread
            }

            val buffer = ByteArray(1024)
            while (BluetoothHelper.isConnected) {
                val bytes = input.read(buffer)
                if (bytes > 0) {
                    val message = String(buffer, 0, bytes, Charset.defaultCharset()).trim()
                    runOnUiThread { handleMessage(message) }
                }
            }
        }.start()
    }

    private fun handleMessage(msg: String) {
        log("Received: $msg")
        when (msg.uppercase()) {
            "ON", "IGNITION_ON" -> {
                engineState = EngineState.ON
                ignitionStatusText.text = "Engine: ON"
            }
            "OFF", "IGNITION_OFF" -> {
                engineState = EngineState.OFF
                ignitionStatusText.text = "Engine: OFF"
            }
            "CALL_REJECTED" -> Toast.makeText(this, "Call Rejected", Toast.LENGTH_SHORT).show()
            "CALL_ACCEPTED" -> Toast.makeText(this, "Call Accepted", Toast.LENGTH_SHORT).show()
            else -> log("Unhandled message: $msg")
        }
    }

    private fun log(message: String) {
        runOnUiThread {
            logText.append("$message\n")
            logText.post {
                val layout = logText.layout
                if (layout != null) {
                    val scrollAmount = layout.getLineTop(logText.lineCount) - logText.height
                    if (scrollAmount > 0) logText.scrollTo(0, scrollAmount)
                }
            }
        }
        Log.d("MainActivity", message)
    }

    override fun onDestroy() {
        super.onDestroy()
        isReaderRunning = false
        BluetoothHelper.close()
        handler.removeCallbacksAndMessages(null)
    }
}
