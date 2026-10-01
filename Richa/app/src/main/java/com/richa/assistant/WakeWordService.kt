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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class WakeWordService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_ENABLE = "com.richa.assistant.ENABLE_WAKE"
        const val ACTION_TALK_NOW = "com.richa.assistant.TALK_NOW"
        const val ACTION_STATE = "com.richa.assistant.STATE"
        const val ACTION_RESPONSE = "com.richa.assistant.RESPONSE"
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
    private var commandMode = false
    private var commandDeadline = 0L
    private lateinit var memory: LocalMemory
    @Volatile private var forceCommand = false

    override fun onCreate() {
        super.onCreate()
        memory = LocalMemory(this)
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Richa is ready")
            .setContentText("Offline wake-word listener is active")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        tts = TextToSpeech(this, this)
        StorageService.unpack(this, "model-en-us", "model", { m ->
            model = m
            sendState("Offline • Wake-word model ready")
        }, { e ->
            sendResponse("I couldn't load my offline speech model: ${e.message ?: "unknown error"}")
            sendState("Model error")
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENABLE -> startListening()
            ACTION_TALK_NOW -> startCommandMode()
        }
        return START_NOT_STICKY
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            sendResponse("Please allow microphone access first.")
            return
        }
        if (running.getAndSet(true)) return
        executor.execute {
            try {
                val m = model ?: run {
                    sendState("Loading offline voice model…")
                    waitForModel()
                    model
                } ?: throw IllegalStateException("model unavailable")
                wakeRecognizer?.close()
                wakeRecognizer = Recognizer(m, SAMPLE_RATE.toFloat(), "[\"hey richa\"]")
                val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val size = maxOf(min * 2, 4096)
                audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
                audioRecord!!.startRecording()
                if (forceCommand) {
                    forceCommand = false
                    startCommandModeInternal()
                    return@execute
                }
                sendState("Listening for ‘Hey Richa’ • offline")
                val buffer = ByteArray(2048)
                while (running.get()) {
                    if (forceCommand) {
                        forceCommand = false
                        startCommandModeInternal()
                        break
                    }
                    val n = audioRecord!!.read(buffer, 0, buffer.size)
                    if (n <= 0) continue
                    val recognized = wakeRecognizer!!.acceptWaveForm(buffer, n)
                    val text = if (recognized) wakeRecognizer!!.result else wakeRecognizer!!.partialResult
                    if (text.lowercase(Locale.US).contains("hey richa")) {
                        startCommandModeInternal()
                        break
                    }
                }
            } catch (t: Throwable) {
                sendResponse("Voice service stopped: ${t.message ?: "unknown error"}")
                sendState("Voice service error")
            } finally {
                stopRecording()
                running.set(false)
            }
        }
    }

    private fun startCommandMode() {
        forceCommand = true
        if (!running.get()) startListening()
    }

    private fun startCommandModeInternal() {
        val m = model ?: return
        commandMode = true
        commandDeadline = System.currentTimeMillis() + 7000
        speak("Yes? I'm listening.")
        sendState("Listening for your command…")
        commandRecognizer?.close()
        commandRecognizer = Recognizer(m, SAMPLE_RATE.toFloat())
        val buffer = ByteArray(4096)
        while (running.get() && System.currentTimeMillis() < commandDeadline) {
            val n = audioRecord?.read(buffer, 0, buffer.size) ?: break
            if (n > 0 && commandRecognizer!!.acceptWaveForm(buffer, n)) {
                val text = extractText(commandRecognizer!!.result)
                if (text.isNotBlank()) {
                    handleCommand(text)
                    break
                }
            }
        }
        commandMode = false
        wakeRecognizer?.reset()
        sendState("Listening for ‘Hey Richa’ • offline")
    }

    private fun handleCommand(command: String) {
        sendResponse("You: $command")
        val lower = command.lowercase(Locale.US).trim()
        when {
            lower == "stop listening" || lower == "stop" -> {
                speak("Okay. I'll stay quiet.")
                running.set(false)
            }
            lower.contains("what time") || lower == "time" -> speak("It is ${java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())}.")
            lower.contains("what date") || lower.contains("today's date") || lower == "date" -> speak("Today is ${java.text.SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(java.util.Date())}.")
            lower.contains("battery") -> {
                val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                speak("Your battery is ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent.")
            }
            lower.contains("storage") || lower.contains("free space") -> {
                val stat = android.os.StatFs(filesDir.absolutePath)
                val free = stat.availableBytes / (1024L * 1024L * 1024L)
                speak("You have about $free gigabytes of free storage.")
            }
            lower.contains("ram") || lower.contains("memory usage") -> {
                val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                val info = android.app.ActivityManager.MemoryInfo()
                am.getMemoryInfo(info)
                val free = info.availMem / (1024L * 1024L)
                speak("About $free megabytes of RAM are currently available.")
            }
            lower.startsWith("remember ") || lower.startsWith("remember that ") -> {
                val note = lower.removePrefix("remember that ").removePrefix("remember ").trim()
                if (note.isNotBlank()) { memory.remember(note); speak("Okay, I'll remember that offline.") }
            }
            lower.contains("what do you remember") || lower.contains("what did you remember") -> {
                val note = memory.recall()
                speak(if (note.isNullOrBlank()) "I don't have anything saved yet." else "You asked me to remember: $note")
            }
            lower == "open settings" || lower == "settings" -> {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                speak("Opening settings.")
            }
            lower.contains("wifi settings") -> {
                startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                speak("Opening Wi-Fi settings.")
            }
            lower.contains("bluetooth settings") -> {
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                speak("Opening Bluetooth settings.")
            }
            lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ") -> {
                var target = when {
                    lower.startsWith("open ") -> lower.removePrefix("open ")
                    lower.startsWith("launch ") -> lower.removePrefix("launch ")
                    else -> lower.removePrefix("start ")
                }.trim()

                target = target
                    .removePrefix("the ")
                    .removeSuffix(" application")
                    .removeSuffix(" app")
                    .trim()

                if (target.isBlank() || target == "app") {
                    speak("Tell me which app to open, for example, open WhatsApp.")
                } else if (AppLauncher.openByName(this, target)) {
                    speak("Opening $target.")
                } else {
                    speak("I couldn't find an app called $target in the launcher.")
                }
            }
            lower.startsWith("set a timer") || lower.startsWith("set timer") -> {
                val seconds = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toLongOrNull()
                if (seconds != null) {
                    val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds.toInt())
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(i); speak("Opening the timer for $seconds seconds.")
                } else speak("Tell me the timer length, for example, set a timer for ten minutes.")
            }
            lower.startsWith("call ") -> {
                val name = command.substringAfter("call ").trim()
                val i = Intent(Intent.ACTION_DIAL).apply { data = android.net.Uri.parse("tel:"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                startActivity(i); speak("Opening the dialer for $name. I won't place the call without your confirmation.")
            }
            else -> speak("I heard you say $command. My offline command library doesn't know that command yet.")
        }
    }

    private fun extractText(json: String): String = Regex("\\\"text\\\"\\s*:\\s*\\\"([^\"]*)\\\"").find(json)?.groupValues?.get(1).orEmpty()

    private fun waitForModel() {
        repeat(60) {
            if (model != null) return
            Thread.sleep(100)
        }
    }

    private fun stopRecording() {
        try { audioRecord?.stop() } catch (_: Throwable) {}
        audioRecord?.release(); audioRecord = null
    }

    private fun speak(text: String) {
        sendResponse(text)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "richa-${System.currentTimeMillis()}")
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts?.language = Locale.US
        tts?.setPitch(1.12f)
        tts?.setSpeechRate(0.94f)
        // Android does not guarantee a gender flag for TTS voices. Prefer a voice whose provider name explicitly says female when one exists.
        tts?.voices?.firstOrNull { it.locale.language == "en" && it.name.contains("female", true) }?.let { tts?.voice = it }
    }

    private fun sendState(state: String) = sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra("state", state))
    private fun sendResponse(text: String) = sendBroadcast(Intent(ACTION_RESPONSE).setPackage(packageName).putExtra("text", text))

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Richa voice", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        running.set(false)
        stopRecording()
        wakeRecognizer?.close(); commandRecognizer?.close(); model?.close()
        tts?.stop(); tts?.shutdown()
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
