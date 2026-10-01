package com.richa.assistant.voice

import android.content.Context

/**
 * Provider-neutral speech output contract.
 * Providers must not clone a real person's voice without authorization.
 */
interface VoiceProvider {
    fun isAvailable(context: Context): Boolean
    fun speak(context: Context, text: String, onStarted: (() -> Unit)? = null, onAudioLevel: ((Float) -> Unit)? = null, onFinished: (() -> Unit)? = null)
    fun stop()
    fun release()
}
