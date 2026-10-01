package com.richa.assistant.voice

import android.content.Context

/**
 * Chooses the best locally installed voice and falls back to Android TTS.
 * The existing WakeWordService remains the compatibility fallback for this step.
 */
class VoiceManager(context: Context) {
    private val appContext = context.applicationContext
    private val kokoro = KokoroVoiceProvider()

    fun hasLocalNeuralVoice(): Boolean = kokoro.isAvailable(appContext)

    fun provider(): VoiceProvider? = if (hasLocalNeuralVoice()) kokoro else null

    fun release() = kokoro.release()
}
