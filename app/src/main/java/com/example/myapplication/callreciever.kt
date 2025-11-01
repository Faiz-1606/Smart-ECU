package com.example.myapplication

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat

class CallReceiver : BroadcastReceiver() {

    companion object {
        // This is updated externally from MainActivity
        var engineState: EngineState = EngineState.UNKNOWN
    }

    enum class EngineState {
        ON, OFF, UNKNOWN
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            Log.d("CallReceiver", "Incoming call detected. Engine state: $engineState")

            if (engineState == EngineState.ON) {
                rejectCall(context)
            } else {
                Log.d("CallReceiver", "Call allowed. Engine OFF.")
            }
        }
    }

    private fun rejectCall(context: Context) {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

            // Check permissions
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
               
                if (telecomManager.defaultDialerPackage == context.packageName) {
                    telecomManager.endCall()
                    Log.d("CallReceiver", "Call rejected via TelecomManager")
                    Toast.makeText(context, "Call Rejected (Engine ON)", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w("CallReceiver", "App is not default dialer. Cannot reject call.")
                    Toast.makeText(context, "Make app default dialer to reject calls", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.w("CallReceiver", "Permission ANSWER_PHONE_CALLS not granted.")
                Toast.makeText(context, "Missing permission to reject call", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("CallReceiver", "Failed to reject call: ${e.message}", e)
        }
    }
}
