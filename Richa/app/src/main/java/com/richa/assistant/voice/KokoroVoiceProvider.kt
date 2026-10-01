package com.richa.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.jokobee.tts.free.Tts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local Kokoro-82M provider through the published JokobeeTTS free AAR.
 * The model and official voices are bundled in the AAR, so no model download is required.
 */
class KokoroVoiceProvider : VoiceProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var audioTrack: AudioTrack? = null
    private var tts: Tts? = null
    private val stopped = AtomicBoolean(false)

    override fun isAvailable(context: Context): Boolean = true

    override fun speak(
        context: Context,
        text: String,
        onStarted: (() -> Unit)?,
        onAudioLevel: ((Float) -> Unit)?,
        onFinished: (() -> Unit)?
    ) {
        stop()
        stopped.set(false)
        job = scope.launch {
            try {
                val engine = tts ?: Tts.create(context.applicationContext).also { tts = it }
                val pcm = engine.synthesize(text, lang = "en")
                if (stopped.get()) return@launch
                val shorts = ShortArray(pcm.size) { i -> (pcm[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
                val min = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(min, 4096))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack = track
                onStarted?.invoke()
                track.play()
                var offset = 0
                while (offset < shorts.size && !stopped.get()) {
                    val count = minOf(2048, shorts.size - offset)
                    track.write(shorts, offset, count)
                    var sum = 0.0
                    for (i in offset until offset + count) {
                        val v = shorts[i].toDouble() / 32768.0
                        sum += v * v
                    }
                    onAudioLevel?.invoke(kotlin.math.sqrt(sum / count).toFloat().coerceIn(0f, 1f))
                    offset += count
                }
                if (!stopped.get()) {
                    while (track.playbackHeadPosition < shorts.size && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        kotlinx.coroutines.delay(20)
                    }
                }
                runCatching { track.stop() }
                runCatching { track.release() }
                audioTrack = null
                if (!stopped.get()) onFinished?.invoke()
            } catch (_: Throwable) {
                runCatching { audioTrack?.release() }
                audioTrack = null
                if (!stopped.get()) onFinished?.invoke()
            }
        }
    }

    override fun stop() {
        stopped.set(true)
        job?.cancel()
        job = null
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
    }

    override fun release() {
        stop()
        tts?.close()
        tts = null
        scope.coroutineContext[Job]?.cancel()
    }
}
