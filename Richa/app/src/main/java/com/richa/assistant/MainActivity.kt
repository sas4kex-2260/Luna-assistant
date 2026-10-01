package com.richa.assistant

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var response: TextView
    private lateinit var micState: TextView
    private lateinit var orbLabel: TextView
    private var receiverRegistered = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status.text = if (granted) "READY • Voice input available" else "Microphone permission is required"
    }

    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WakeWordService.ACTION_STATE -> {
                    val state = intent.getStringExtra("state") ?: return
                    status.text = state
                    micState.text = if (state.contains("listening", true)) "● LISTENING" else "○ IDLE"
                    orbLabel.text = when { state.contains("thinking", true) -> "THINKING"; state.contains("command", true) -> "LISTENING"; state.contains("wake", true) -> "WAITING"; else -> "READY" }
                }
                WakeWordService.ACTION_RESPONSE -> response.text = intent.getStringExtra("text") ?: ""
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText); response = findViewById(R.id.responseText); micState = findViewById(R.id.micState); orbLabel = findViewById(R.id.orbLabel)

        findViewById<Button>(R.id.talkButton).setOnClickListener {
            if (hasMicPermission()) startServiceCompat(Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_TALK_NOW)) else requestMicPermission()
        }
        findViewById<Button>(R.id.wakeButton).setOnClickListener {
            if (hasMicPermission()) startServiceCompat(Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_ENABLE)) else requestMicPermission()
        }
        findViewById<Button>(R.id.aiButton).setOnClickListener { showAiSetup() }
        findViewById<Button>(R.id.overlayButton).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else { startService(Intent(this, OverlayService::class.java)); response.text = "Floating Richa bubble enabled." }
        }
        status.text = if (SecureStore.hasGeminiKey(this)) "ONLINE AI • Gemini ready" else "OFFLINE CORE • Add Gemini for smart mode"
        response.text = "I'm Richa. Try: ‘open YouTube’, ‘set a timer for 10 minutes’, or ask me anything after adding online AI."
    }

    private fun showAiSetup() {
        val input = EditText(this).apply {
            hint = "Paste your Gemini API key"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(if (SecureStore.hasGeminiKey(this@MainActivity)) "" else "")
        }
        AlertDialog.Builder(this).setTitle("Richa Online AI")
            .setMessage("Your key is encrypted with Android Keystore and is not written to GitHub. For a production app, a backend proxy is safer.")
            .setView(input)
            .setNegativeButton("Disable") { _, _ -> SecureStore.saveGeminiKey(this, ""); status.text = "OFFLINE CORE • Online AI disabled" }
            .setPositiveButton("Save") { _, _ ->
                SecureStore.saveGeminiKey(this, input.text.toString())
                status.text = if (SecureStore.hasGeminiKey(this)) "ONLINE AI • Gemini ready" else "OFFLINE CORE"
                response.text = "Online AI is ${if (SecureStore.hasGeminiKey(this)) "enabled" else "disabled"}."
            }.show()
    }

    private fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun requestMicPermission() = permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    private fun startServiceCompat(intent: Intent) = ContextCompat.startForegroundService(this, intent)

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(this, events, IntentFilter().apply { addAction(WakeWordService.ACTION_STATE); addAction(WakeWordService.ACTION_RESPONSE) }, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
    }
    override fun onStop() { if (receiverRegistered) { unregisterReceiver(events); receiverRegistered = false }; super.onStop() }
}