package com.richa.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dev.ffmpegkit.kokoro.KokoroTTS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Local Kokoro provider.
 *
 * The ONNX model is intentionally not committed to GitHub. The app can install it
 * into its private files directory in a later model-manager step.
 */
class KokoroVoiceProvider : VoiceProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioTrack: AudioTrack? = null

    override fun isAvailable(context: Context): Boolean =
        File(context.filesDir, MODEL_FILE).exists()

    override fun speak(context: Context, text: String, onStarted: (() -> Unit)?, onFinished: (() -> Unit)?) {
        if (!isAvailable(context)) return
        scope.launch {
            try {
                KokoroTTS.initialize(context, File(context.filesDir, MODEL_FILE).absolutePath)
                val result = KokoroTTS.speak(text)
                val wav = result.audioData
                val headerSize = findPcmDataOffset(wav)
                val data = wav.copyOfRange(headerSize, wav.size)
                val min = AudioTrack.getMinBufferSize(
                    24000,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(24000)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(min, data.size))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                audioTrack = track
                track.write(data, 0, data.size)
                onStarted?.invoke()
                track.play()
                while (track.playbackHeadPosition < data.size / 2 && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    Thread.sleep(25)
                }
                track.stop()
                track.release()
                audioTrack = null
                onFinished?.invoke()
            } catch (_: Throwable) {
                audioTrack = null
                onFinished?.invoke()
            }
        }
    }

    private fun findPcmDataOffset(wav: ByteArray): Int {
        if (wav.size < 44) return 0
        var p = 12
        while (p + 8 <= wav.size) {
            val id = String(wav, p, 4, Charsets.US_ASCII)
            val size = (wav[p + 4].toInt() and 0xff) or
                ((wav[p + 5].toInt() and 0xff) shl 8) or
                ((wav[p + 6].toInt() and 0xff) shl 16) or
                ((wav[p + 7].toInt() and 0xff) shl 24)
            if (id == "data") return (p + 8).coerceAtMost(wav.size)
            if (size < 0 || p + 8 + size > wav.size) break
            p += 8 + size
        }
        return 44
    }

    override fun stop() {
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
    }

    override fun release() {
        stop()
        runCatching { KokoroTTS.release() }
        scope.coroutineContext.cancel()
    }

    companion object {
        const val MODEL_FILE = "kokoro.onnx"
    }
}
