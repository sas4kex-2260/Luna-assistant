package com.richa.assistant

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

class ChatService(private val context: Context) {
    private val streaming = GeminiStreamingService(context)
    private val cancelled = AtomicBoolean(false)

    fun send(userText: String, callback: (String) -> Unit) {
        sendStreaming(emptyList(), userText, false, { }, callback)
    }

    fun sendStreaming(history: List<ChatMessage>, userText: String, useWeb: Boolean, onChunk: (String) -> Unit, onDone: (String) -> Unit) {
        cancelled.set(false)
        streaming.stream(history, userText, useWeb,
            onChunk = { if (!cancelled.get()) onChunk(it) },
            onDone = { if (!cancelled.get()) onDone(it) }
        )
    }

    fun cancel() {
        cancelled.set(true)
    }
}
