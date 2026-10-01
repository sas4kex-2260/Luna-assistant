package com.richa.assistant

import android.content.Context

class ChatService(private val context: Context) {
    fun send(userText: String, callback: (String) -> Unit) {
        Thread {
            val decision = GeminiBrain.decide(
                context,
                userText,
                AppLauncher.installedLabels(context)
            )
            val answer = if (decision.type == "chat") {
                decision.value
            } else {
                "I can handle that as a phone action, but this chat screen currently supports conversation responses. The action router remains available through voice commands."
            }
            callback(answer.ifBlank { "I didn't receive a usable response." })
        }.start()
    }
}
