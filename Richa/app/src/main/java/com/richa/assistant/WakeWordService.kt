package com.richa.assistant

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import android.provider.AlarmClock
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.util.Locale
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import com.richa.assistant.voice.VoiceManager

class WakeWordService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_ENABLE = "com.richa.assistant.ENABLE_WAKE"
        const val ACTION_TALK_NOW = "com.richa.assistant.TALK_NOW"
        const val ACTION_INTERRUPT = "com.richa.assistant.INTERRUPT"
        const val ACTION_STATE = "com.richa.assistant.STATE"
        const val ACTION_RESPONSE = "com.richa.assistant.RESPONSE"
        const val ACTION_VOICE_LEVEL = "com.richa.assistant.VOICE_LEVEL"
        private const val CHANNEL_ID = "richa_voice"
        private const val NOTIFICATION_ID = 4101
        private const val SAMPLE_RATE = 16000
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var model: Model? = null
    private var wakeRecognizer: Recognizer? = null
    private var commandRecognizer: Recognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var memory: LocalMemory
    private lateinit var voiceManager: VoiceManager
    @Volatile private var forceCommand = false

    override fun onCreate() {
        super.onCreate()
        memory = LocalMemory(this)
        voiceManager = VoiceManager(this)
        createChannel()
        tts = runCatching { TextToSpeech(applicationContext, this) }.getOrNull()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            sendState("Microphone permission required")
            sendResponse("Please allow microphone access before using voice features.")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        if (!ensureForeground()) {
            sendState("Voice service error")
            sendResponse("Android did not allow Richa to start its microphone service.")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        if (model == null) {
            StorageService.unpack(
                this,
                "model-en-us",
                "model",
                { m -> model = m; sendState("Ready • offline voice + online AI") },
                { e ->
                    sendResponse("I couldn't load my speech model: ${e.message ?: "unknown error"}")
                    sendState("Voice model error")
                }
            )
        }

        when (intent?.action) {
            ACTION_ENABLE -> startListening()
            ACTION_TALK_NOW -> startCommandMode()
            ACTION_INTERRUPT -> { voiceManager.provider()?.stop(); tts?.stop(); sendState("Voice interrupted") }
        }
        return START_NOT_STICKY
    }

    private fun ensureForeground(): Boolean {
        return try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Richa voice active")
                .setContentText("Richa voice mode is enabled.")
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { sendResponse("Please allow microphone access first."); return }
        if (running.getAndSet(true)) return
        executor.execute {
            try {
                val m = model ?: run { sendState("Loading voice model…"); waitForModel(); model } ?: throw IllegalStateException("model unavailable")
                wakeRecognizer?.close(); wakeRecognizer = Recognizer(m, SAMPLE_RATE.toFloat(), "[\"hey richa\"]")
                val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 4096))
                audioRecord!!.startRecording()
                if (forceCommand) {
                    forceCommand = false
                    startCommandModeInternal()
                    if (!running.get()) return@execute
                }
                sendState("Listening for ‘Hey Richa’ • offline")
                val buffer = ByteArray(2048)
                while (running.get()) {
                    if (forceCommand) {
                        forceCommand = false
                        startCommandModeInternal()
                        if (!running.get()) break
                        continue
                    }
                    val n = audioRecord!!.read(buffer, 0, buffer.size); if (n <= 0) continue
                    val recognized = wakeRecognizer!!.acceptWaveForm(buffer, n)
                    val text = if (recognized) wakeRecognizer!!.result else wakeRecognizer!!.partialResult
                    if (text.lowercase(Locale.US).contains("hey richa")) {
                        startCommandModeInternal()
                        if (!running.get()) break
                        sendState("Listening for ‘Hey Richa’ • offline")
                    }
                }
            } catch (t: Throwable) { sendResponse("Voice service stopped: ${t.message ?: "unknown error"}"); sendState("Voice service error") }
            finally { stopRecording(); running.set(false) }
        }
    }

    private fun startCommandMode() { voiceManager.provider()?.stop(); tts?.stop(); forceCommand = true; if (!running.get()) startListening() }

    private fun startCommandModeInternal() {
        val m = model ?: return
        speak("Yes? I'm listening.")
        sendState("Listening for your command…")
        commandRecognizer?.close(); commandRecognizer = Recognizer(m, SAMPLE_RATE.toFloat())
        val deadline = System.currentTimeMillis() + 8000
        val buffer = ByteArray(4096)
        while (running.get() && System.currentTimeMillis() < deadline) {
            val n = audioRecord?.read(buffer, 0, buffer.size) ?: break
            if (n > 0 && commandRecognizer!!.acceptWaveForm(buffer, n)) {
                val text = extractText(commandRecognizer!!.result)
                if (text.isNotBlank()) { handleCommand(text); break }
            }
        }
        wakeRecognizer?.reset(); sendState("Listening for ‘Hey Richa’ • offline")
    }

    private fun handleCommand(command: String) {
        sendResponse("You: $command")
        val lower = command.lowercase(Locale.US).trim()
        try {
            when {
                lower == "stop listening" || lower == "stop" -> { speak("Okay. I'll stay quiet."); running.set(false) }
            lower.contains("what time") || lower == "time" -> speak("It is ${java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())}.")
            lower.contains("what date") || lower.contains("today's date") || lower == "date" -> speak("Today is ${java.text.SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(java.util.Date())}.")
            lower.contains("battery") -> { val bm = getSystemService(BATTERY_SERVICE) as BatteryManager; speak("Your battery is ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent.") }
            lower.contains("storage") || lower.contains("free space") -> { val stat = android.os.StatFs(filesDir.absolutePath); speak("You have about ${stat.availableBytes / (1024L * 1024L * 1024L)} gigabytes of free storage.") }
            lower.contains("ram") || lower.contains("memory usage") -> { val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager; val info = ActivityManager.MemoryInfo(); am.getMemoryInfo(info); speak("About ${info.availMem / (1024L * 1024L)} megabytes of RAM are currently available.") }
            lower.startsWith("remember ") || lower.startsWith("remember that ") -> { val note = lower.removePrefix("remember that ").removePrefix("remember ").trim(); if (note.isNotBlank()) { memory.remember(note); speak("Okay, I'll remember that offline.") } }
            lower.contains("what do you remember") || lower.contains("what did you remember") -> { val note = memory.recall(); speak(if (note.isNullOrBlank()) "I don't have anything saved yet." else "You asked me to remember: $note") }
            lower == "open settings" || lower == "settings" -> { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); speak("Opening settings.") }
            lower.contains("wifi settings") -> { startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); speak("Opening Wi-Fi settings.") }
            lower.contains("bluetooth settings") -> { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); speak("Opening Bluetooth settings.") }
            lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ") -> openRequestedApp(command)
            lower.startsWith("set a timer") || lower.startsWith("set timer") -> {
                val seconds = parseSeconds(lower)
                if (seconds != null) { val i = Intent(AlarmClock.ACTION_SET_TIMER).apply { putExtra(AlarmClock.EXTRA_LENGTH, seconds.toInt()); putExtra(AlarmClock.EXTRA_SKIP_UI, false); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }; startActivity(i); speak("Opening the timer.") }
                else askOnline(command)
            }
            lower.startsWith("call ") -> { startActivity(Intent(Intent.ACTION_DIAL).apply { data = android.net.Uri.parse("tel:"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); speak("Opening the dialer. I won't place the call without your confirmation.") }
                else -> askOnline(command)
            }
        } catch (_: Throwable) {
            sendResponse("I couldn't safely complete that command.")
        }
    }

    private fun openRequestedApp(command: String) {
        var target = command.substringAfter(" ").trim().removePrefix("the ").removeSuffix(" application").removeSuffix(" app").trim()
        if (target.isBlank()) { speak("Tell me which app to open."); return }
        if (AppLauncher.openByName(this, target)) { speak("Opening $target."); return }
        if (SecureStore.hasGeminiKey(this)) {
            val decision = GeminiBrain.decide(this, command, AppLauncher.installedLabels(this))
            if (decision.type == "open_app" && decision.value.isNotBlank() && AppLauncher.openByName(this, decision.value)) { speak("Opening ${decision.value}."); return }
        }
        speak("I couldn't find $target among the apps currently available to launch.")
    }

    private fun askOnline(command: String) {
        if (!SecureStore.hasGeminiKey(this)) { speak("I heard you, but that isn't one of my offline commands. Add a Gemini key in Richa settings for online AI."); return }
        sendState("Thinking with online AI…")
        val decision = GeminiBrain.decide(this, command, AppLauncher.installedLabels(this))
        executeDecision(decision)
    }

    private fun executeDecision(d: BrainDecision) {
        when (d.type) {
            "chat" -> speak(d.value.ifBlank { "I don't have an answer for that yet." })
            "open_app" -> if (AppLauncher.openByName(this, d.value)) speak("Opening ${d.value}.") else speak("That app isn't installed or doesn't expose a launcher entry.")
            "timer" -> { val sec = d.value.toLongOrNull(); if (sec != null && sec in 1..86400) { startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply { putExtra(AlarmClock.EXTRA_LENGTH, sec.toInt()); putExtra(AlarmClock.EXTRA_SKIP_UI, false); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); speak("Opening the timer.") } else speak("I couldn't safely parse that timer.") }
            "settings" -> { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); speak("Opening settings.") }
            "wifi" -> { startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); speak("Opening Wi-Fi settings.") }
            "bluetooth" -> { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); speak("Opening Bluetooth settings.") }
            "remember" -> { if (d.value.isNotBlank()) { memory.remember(d.value); speak("Saved locally. I won't send that memory to the AI.") } }
            else -> speak("I understood the request, but I won't execute an unknown action.")
        }
    }

    private fun parseSeconds(text: String): Long? {
        val n = Regex("(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return when { text.contains("hour") -> n * 3600; text.contains("minute") || text.contains("min") -> n * 60; else -> n }
    }
    private fun extractText(json: String): String =
    runCatching { JSONObject(json).optString("text").trim() }.getOrDefault("")
    private fun waitForModel() {
    repeat(120) {
        if (model != null) return
        if (Thread.currentThread().isInterrupted) return
        Thread.sleep(100)
    }
}
    private fun stopRecording() { try { audioRecord?.stop() } catch (_: Throwable) {}; audioRecord?.release(); audioRecord = null }
    private fun speak(text: String) {
        voiceManager.provider()?.stop()
        tts?.stop()
        sendResponse(text)
        if (voiceManager.hasLocalNeuralVoice()) {
            voiceManager.provider()?.speak(this, text, onAudioLevel = { level -> sendBroadcast(Intent(ACTION_VOICE_LEVEL).setPackage(packageName).putExtra("level", level)) })
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "richa-" + System.currentTimeMillis())
        }
    }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) { tts?.language = Locale.US; tts?.setPitch(1.12f); tts?.setSpeechRate(0.94f); tts?.voices?.firstOrNull { it.locale.language == "en" && it.name.contains("female", true) }?.let { tts?.voice = it } } }
    private fun sendState(state: String) = sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra("state", state))
    private fun sendResponse(text: String) = sendBroadcast(Intent(ACTION_RESPONSE).setPackage(packageName).putExtra("text", text))
    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Richa voice", NotificationManager.IMPORTANCE_LOW)) }
    override fun onDestroy() { running.set(false); stopRecording(); wakeRecognizer?.close(); commandRecognizer?.close(); model?.close(); tts?.stop(); tts?.shutdown(); voiceManager.release(); executor.shutdownNow(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}