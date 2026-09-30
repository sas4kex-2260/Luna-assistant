package com.richa.assistant

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var response: TextView
    private lateinit var micState: TextView
    private lateinit var orbLabel: TextView
    private lateinit var memoryStatus: TextView
    private var receiverRegistered = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status.text = if (granted) "Offline • Microphone ready"
        else "Microphone permission is required for voice features"
    }

    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WakeWordService.ACTION_STATE -> {
                    val state = intent.getStringExtra("state") ?: return
                    status.text = state
                    micState.text = if (state.contains("listening", true)) "MIC ON" else "MIC OFF"
                    orbLabel.text = when {
                        state.contains("wake", true) -> "Listening"
                        state.contains("command", true) -> "Your turn"
                        else -> "Tap to talk"
                    }
                }
                WakeWordService.ACTION_RESPONSE -> {
                    response.text = intent.getStringExtra("text") ?: ""
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.statusText)
        response = findViewById(R.id.responseText)
        micState = findViewById(R.id.micState)
        orbLabel = findViewById(R.id.orbLabel)
        memoryStatus = findViewById(R.id.memoryStatus)

        val talk = findViewById<Button>(R.id.talkButton)
        val wake = findViewById<Button>(R.id.wakeButton)
        val overlay = findViewById<Button>(R.id.overlayButton)

        memoryStatus.text = "Local memory • On-device only"

        talk.setOnClickListener {
            if (hasMicPermission()) {
                startServiceCompat(Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_TALK_NOW))
            } else requestMicPermission()
        }

        wake.setOnClickListener {
            if (hasMicPermission()) {
                startServiceCompat(Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_ENABLE))
            } else requestMicPermission()
        }

        overlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startService(Intent(this, OverlayService::class.java))
                response.text = "Floating Richa bubble enabled."
            }
        }

        if (!hasMicPermission()) {
            status.text = "Offline • Ready — tap TALK to allow microphone"
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startServiceCompat(intent: Intent) {
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                events,
                IntentFilter().apply {
                    addAction(WakeWordService.ACTION_STATE)
                    addAction(WakeWordService.ACTION_RESPONSE)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(events)
            receiverRegistered = false
        }
        super.onStop()
    }
}
