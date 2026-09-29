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

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var response: TextView
    private lateinit var micState: TextView
    private lateinit var orbLabel: TextView
    private lateinit var memoryStatus: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            status.text = "Offline • Microphone ready"
        } else {
            status.text = "Microphone permission is required for voice features"
        }
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

        requestPermissionsIfNeeded()
        memoryStatus.text = "Local memory • On-device only"

        talk.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                val i = Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_TALK_NOW)
                startServiceCompat(i)
            } else requestPermissionsIfNeeded()
        }

        wake.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startServiceCompat(Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_ENABLE))
            } else requestPermissionsIfNeeded()
        }

        overlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startService(Intent(this, OverlayService::class.java))
                response.text = "Floating Richa bubble enabled."
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.RECORD_AUDIO
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.POST_NOTIFICATIONS
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startServiceCompat(intent: Intent) {
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
    }

    override fun onStart() {
        super.onStart()
        androidx.core.content.ContextCompat.registerReceiver(
            this, events, IntentFilter().apply {
                addAction(WakeWordService.ACTION_STATE)
                addAction(WakeWordService.ACTION_RESPONSE)
            }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(events)
        super.onStop()
    }
}
