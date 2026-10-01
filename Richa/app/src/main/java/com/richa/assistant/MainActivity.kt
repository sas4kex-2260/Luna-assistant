package com.richa.assistant

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import com.richa.assistant.voice.VoiceModelInstaller
import com.richa.assistant.voice.VoiceManager
import com.richa.assistant.companion.CompanionAssetStore

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var response: TextView
    private lateinit var micState: TextView
    private lateinit var orbLabel: TextView
    private lateinit var chatList: LinearLayout
    private lateinit var chatInput: EditText
    private lateinit var chatScroll: ScrollView
    private lateinit var companionPanel: View
    private lateinit var chatPanel: View
    private lateinit var toolsPanel: View
    private lateinit var historyPanel: View
    private lateinit var settingsPanel: View

    private val chatService by lazy { ChatService(this) }
    private val conversationStore by lazy { ConversationStore(this) }
    private var messages = mutableListOf<ChatMessage>()
    private var receiverRegistered = false
    private val companionAssetStore by lazy { CompanionAssetStore(this) }

    private val companionPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open model." }
                companionAssetStore.modelFile().outputStream().use { output -> input.copyTo(output) }
            }
            response.text = "Waguri GLB imported. The companion renderer can load it in the next renderer step."
            orbLabel.text = "MODEL READY"
        }.onFailure { response.text = "I couldn't import that model safely." }
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status.text = if (granted) "READY • Voice input available" else "MICROPHONE PERMISSION NEEDED"
    }

    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WakeWordService.ACTION_STATE -> {
                    val state = intent.getStringExtra("state") ?: return
                    status.text = state.uppercase(Locale.US)
                    micState.text = if (state.contains("listening", true)) "● LISTENING" else "○ IDLE"
                    orbLabel.text = when {
                        state.contains("thinking", true) -> "THINKING"
                        state.contains("command", true) -> "LISTENING"
                        state.contains("wake", true) -> "WAITING"
                        else -> "READY"
                    }
                }
                WakeWordService.ACTION_RESPONSE -> {
                    val text = intent.getStringExtra("text") ?: ""
                    response.text = text
                    addAssistantMessage(text)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        messages = conversationStore.load()
        renderMessages()

        status.text = if (SecureStore.hasGeminiKey(this)) "ONLINE • GEMINI READY" else "OFFLINE • ADD GEMINI FOR SMART MODE"
        response.text = "Hey. I'm Richa. Ask me something."

        findViewById<Button>(R.id.importCompanionButton).setOnClickListener { companionPicker.launch(arrayOf("model/gltf-binary", "model/gltf+json", "application/octet-stream")) }
        findViewById<Button>(R.id.talkButton).setOnClickListener {
            if (hasMicPermission()) startServiceCompat(Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_TALK_NOW))
            else requestMicPermission()
        }
        findViewById<Button>(R.id.wakeButton).setOnClickListener {
            if (hasMicPermission()) startServiceCompat(Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_ENABLE))
            else requestMicPermission()
        }
        findViewById<Button>(R.id.aiButton).setOnClickListener { showAiSetup() }
        findViewById<Button>(R.id.overlayButton).setOnClickListener { toggleOverlay() }
        findViewById<Button>(R.id.settingsAiButton).setOnClickListener { showAiSetup() }
        findViewById<Button>(R.id.installVoiceButton).setOnClickListener { installNeuralVoice() }
        findViewById<Button>(R.id.sendButton).setOnClickListener { sendChat() }
        findViewById<Button>(R.id.newChatButton).setOnClickListener {
            messages.clear()
            conversationStore.clear()
            renderMessages()
            showPage("chat")
        }
        findViewById<Button>(R.id.clearHistoryButton).setOnClickListener {
            messages.clear()
            conversationStore.clear()
            renderMessages()
            response.text = "Conversation history cleared."
        }

        findViewById<Button>(R.id.navChat).setOnClickListener { showPage("chat") }
        findViewById<Button>(R.id.navCompanion).setOnClickListener { showPage("companion") }
        findViewById<Button>(R.id.navTools).setOnClickListener { showPage("tools") }
        findViewById<Button>(R.id.navHistory).setOnClickListener { showPage("history") }
        findViewById<Button>(R.id.navSettings).setOnClickListener { showPage("settings") }

        showPage("chat")
    }

    private fun bindViews() {
        status = findViewById(R.id.statusText)
        response = findViewById(R.id.responseText)
        micState = findViewById(R.id.micState)
        orbLabel = findViewById(R.id.orbLabel)
        chatList = findViewById(R.id.chatList)
        chatInput = findViewById(R.id.chatInput)
        chatScroll = findViewById(R.id.chatScroll)
        companionPanel = findViewById(R.id.companionPanel)
        chatPanel = findViewById(R.id.chatPanel)
        toolsPanel = findViewById(R.id.toolsPanel)
        historyPanel = findViewById(R.id.historyPanel)
        settingsPanel = findViewById(R.id.settingsPanel)
    }

    private fun showPage(page: String) {
        companionPanel.visibility = if (page == "companion") View.VISIBLE else View.GONE
        chatPanel.visibility = if (page == "chat") View.VISIBLE else View.GONE
        toolsPanel.visibility = if (page == "tools") View.VISIBLE else View.GONE
        historyPanel.visibility = if (page == "history") View.VISIBLE else View.GONE
        settingsPanel.visibility = if (page == "settings") View.VISIBLE else View.GONE
    }

    private fun sendChat() {
        val text = chatInput.text.toString().trim()
        if (text.isEmpty()) return
        addUserMessage(text)
        chatInput.setText("")
        response.text = "Thinking..."
        status.text = "THINKING • ONLINE AI"

        chatService.send(text) { answer ->
            runOnUiThread {
                addAssistantMessage(answer)
                response.text = answer
                status.text = if (SecureStore.hasGeminiKey(this)) "ONLINE • GEMINI READY" else "OFFLINE • ADD GEMINI"
            }
        }
    }

    private fun addUserMessage(text: String) {
        messages.add(ChatMessage(role = ChatMessage.Role.USER, text = text))
        conversationStore.save(messages)
        renderMessages()
    }

    private fun addAssistantMessage(text: String) {
        if (text.isBlank()) return
        messages.add(ChatMessage(role = ChatMessage.Role.ASSISTANT, text = text))
        conversationStore.save(messages)
        renderMessages()
    }

    private fun renderMessages() {
        chatList.removeAllViews()
        if (messages.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Start a conversation with Richa."
                setTextColor(getColor(R.color.richa_muted))
                textSize = 15f
                setPadding(8, 20, 8, 20)
            }
            chatList.addView(empty)
        } else {
            messages.takeLast(100).forEach { message ->
                val bubble = TextView(this).apply {
                    text = message.text
                    textSize = 15f
                    setTextColor(getColor(R.color.richa_text))
                    setPadding(16, 13, 16, 13)
                    setBackgroundResource(if (message.role == ChatMessage.Role.USER) R.drawable/user_bubble else R.drawable/panel_bg)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 6, 0, 6)
                    }
                }
                chatList.addView(bubble)
            }
        }
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun installNeuralVoice() {
        if (VoiceManager(this).hasLocalNeuralVoice()) {
            response.text = "Offline neural voice is already installed."
            return
        }
        status.text = "DOWNLOADING • KOKORO VOICE"
        response.text = "Downloading the verified ~86 MB neural voice model. This is a one-time setup; speech stays local afterward."
        VoiceModelInstaller.install(this,
            onProgress = { p -> runOnUiThread { response.text = "Installing offline neural voice… $p%" } },
            onDone = { ok, message -> runOnUiThread {
                status.text = if (ok) "OFFLINE • NEURAL VOICE READY" else "VOICE INSTALL FAILED"
                response.text = message
            } }
        )
    }
    private fun showAiSetup() {
        val input = EditText(this).apply {
            hint = "Paste Gemini API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Richa Online AI")
            .setMessage("Enter your key once. Richa stores it locally using Android Keystore and does not write it to GitHub.")
            .setView(input)
            .setNegativeButton("Disable") { _, _ ->
                SecureStore.saveGeminiKey(this, "")
                status.text = "OFFLINE • ONLINE AI DISABLED"
            }
            .setPositiveButton("Save") { _, _ ->
                SecureStore.saveGeminiKey(this, input.text.toString())
                status.text = if (SecureStore.hasGeminiKey(this)) "ONLINE • GEMINI READY" else "OFFLINE"
            }.show()
    }

    private fun toggleOverlay() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startService(Intent(this, OverlayService::class.java))
                response.text = "Floating Richa bubble enabled."
            }
        } catch (_: Throwable) {
            response.text = "I couldn't enable the floating bubble safely."
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() = permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

    private fun startServiceCompat(intent: Intent) {
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (_: Throwable) {
            status.text = "VOICE SERVICE BLOCKED"
            response.text = "Android did not allow the voice service to start. Try again while Richa is open."
        }
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this, events,
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
