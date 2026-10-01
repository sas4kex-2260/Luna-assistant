package com.richa.assistant.voice

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

object VoiceModelInstaller {
    const val MODEL_URL = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_q8f16.onnx"
    const val MODEL_SHA256 = "04c658aec1b6008857c2ad10f8c589d4180d0ec427e7e6118ceb487e215c3cd0"

    fun install(context: Context, onProgress: (Int) -> Unit, onDone: (Boolean, String) -> Unit) {
        val target = File(context.filesDir, KokoroVoiceProvider.MODEL_FILE)
        Executors.newSingleThreadExecutor().execute {
            try {
                val tmp = File(context.filesDir, "kokoro.onnx.part")
                val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    requestMethod = "GET"
                }
                connection.connect()
                if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP " + connection.responseCode)
                val total = connection.contentLengthLong
                var read = 0L
                connection.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(1024 * 256)
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            output.write(buffer, 0, n)
                            read += n
                            if (total > 0) onProgress(((read * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
                val digest = MessageDigest.getInstance("SHA-256").digest(tmp.readBytes()).joinToString("") { "%02x".format(it) }
                if (!digest.equals(MODEL_SHA256, ignoreCase = true)) {
                    tmp.delete()
                    throw IllegalStateException("Model checksum mismatch")
                }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                onProgress(100)
                onDone(true, "Richa neural voice installed.")
            } catch (t: Throwable) {
                onDone(false, t.message ?: "Voice model installation failed.")
            }
        }
    }
}