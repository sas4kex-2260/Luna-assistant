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
import com.richa.assistant.companion.RichaCompanionView
import android.graphics.Bitmap
import java.io.File

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
    private lateinit var companionHost: FrameLayout
    private var companionView: RichaCompanionView? = null
    private val faceGazeService = FaceGazeService()

    private val chatService by lazy { ChatService(this) }
    private val conversationStore by lazy { ConversationStore(this) }
    private var messages = mutableListOf<ChatMessage>()
    private var receiverRegistered = false
    private val companionAssetStore by lazy { CompanionAssetStore(this) }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val fileText = FileService(this).readText(uri)
        chatInput.setText("Please analyze this file:\n\n" + fileText.take(10000))
        chatInput.setSelection(chatInput.text.length)
    }

    private val cameraPicker = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap == null) return@registerForActivityResult
        val file = File(cacheDir, "camera_" + System.currentTimeMillis() + ".jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        faceGazeService.detect(bitmap) { gaze ->
            runOnUiThread {
                orbLabel.text = if (gaze == null) "FACE NOT FOUND" else "FACE " + (gaze.x * 100).toInt() + "% / " + (gaze.y * 100).toInt() + "%"
            }
        }
        VisionService(this).analyzeFile(file, "Describe this image carefully and help me understand what I am looking at.") { answer ->
            runOnUiThread { chatInput.setText(answer); response.text = answer }
        }
    }

    private val companionPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open model." }
                companionAssetStore.modelFile().outputStream().use { output -> input.copyTo(output) }
            }
            response.text = "Waguri 3D model loaded."
            orbLabel.text = "3D READY"
            loadCompanionModel()
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
                WakeWordService.ACTION_VOICE_LEVEL -> {
                    companionView?.updateLipSync("A", intent.getFloatExtra("level", 0f))
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

        findViewById<Button>(R.id.fileButton).setOnClickListener { filePicker.launch(arrayOf("text/*", "application/pdf", "application/json", "text/csv")) }
        findViewById<Button>(R.id.cameraButton).setOnClickListener { launchCamera() }
        findViewById<Button>(R.id.importCompanionButton).setOnClickListener { companionPicker.launch(arrayOf("model/gltf-binary", "model/gltf+json", "application/octet-stream")) }
        findViewById<Button>(R.id.talkButton).setOnClickListener {
            chatService.cancel()
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
        findViewById<Button>(R.id.devDiagnosticsButton).setOnClickListener { showDiagnostics() }
        findViewById<Button>(R.id.accessibilityButton).setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
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
        companionHost = findViewById(R.id.companionHost)
    }

    private fun loadCompanionModel() {
        if (!companionAssetStore.hasModel()) return
        if (companionView == null) {
            companionView = RichaCompanionView(this)
            companionHost.removeAllViews()
            companionHost.addView(companionView, FrameLayout.LayoutParams(-1, -1))
            findViewById<ImageView>(R.id.companionPlaceholder).visibility = View.GONE
        }
        companionView?.loadModel(companionAssetStore.modelFile())
    }

    private fun showPage(page: String) {
        if (page == "companion") loadCompanionModel()
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

        val useWeb = text.contains("latest", true) || text.contains("today", true) || text.contains("current", true) || text.contains("search web", true) || text.contains("news", true)
        val draft = StringBuilder()
        chatService.sendStreaming(messages.dropLast(1), text, useWeb,
            onChunk = { chunk -> runOnUiThread { draft.append(chunk); response.text = draft.toString(); status.text = if (useWeb) "SEARCHING • WEB" else "GENERATING • STREAMING" } },
            onDone = { answer -> runOnUiThread { addAssistantMessage(answer); response.text = answer; status.text = if (SecureStore.hasGeminiKey(this)) "ONLINE • GEMINI READY" else "OFFLINE • ADD GEMINI" } }
        )
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

    private fun showDiagnostics() {
        val d = DiagnosticsService.snapshot(this)
        AlertDialog.Builder(this).setTitle("Richa Developer Diagnostics")
            .setMessage("${d.android}\nRAM: ${d.ramUsedMb}/${d.ramTotalMb} MB\nApp native heap: ${d.appHeapMb} MB\nGPU: ${d.gpu}\nAI: ${d.ai}\nVoice: ${d.voice}\nCompanion: ${d.companion}")
            .setPositiveButton("OK", null).show()
    }

    private fun installNeuralVoice() {
        status.text = "OFFLINE • KOKORO READY"
        response.text = "Kokoro-82M neural voice is bundled locally with Richa. No voice-model download is required."
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

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) cameraPicker.launch(null) else response.text = "Camera permission denied." }

    private fun requestMicPermission() = permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraPicker.launch(null)
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

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
                    addAction(WakeWordService.ACTION_VOICE_LEVEL)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
    }

    override fun onDestroy() {
        faceGazeService.close()
        companionView?.dispose()
        super.onDestroy()
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(events)
            receiverRegistered = false
        }
        super.onStop()
    }
}
