package com.richa.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class GeminiStreamingService(private val context: Context) {
    fun stream(history: List<ChatMessage>, userText: String, useWeb: Boolean, onChunk: (String) -> Unit, onDone: (String) -> Unit) {
        Thread {
            val key = SecureStore.getGeminiKey(context)
            if (key.isNullOrBlank()) { onDone("Add a Gemini API key in Richa settings to enable smart mode."); return@Thread }
            var connection: HttpURLConnection? = null
            val full = StringBuilder()
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:streamGenerateContent?alt=sse"
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 12000; readTimeout = 60000; doOutput = true
                    setRequestProperty("Content-Type", "application/json"); setRequestProperty("x-goog-api-key", key)
                }
                val contents = JSONArray()
                history.takeLast(40).forEach { m ->
                    contents.put(JSONObject().put("role", if (m.role == ChatMessage.Role.USER) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", m.text))))
                }
                contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userText))))
                val body = JSONObject().put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "You are Richa, a personal Android AI companion. Be natural. Never claim an Android action happened unless the app executed it."))))
                    .put("contents", contents).put("generationConfig", JSONObject().put("maxOutputTokens", 1200))
                if (useWeb) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) { onDone("Online AI failed (" + code + ")."); return@Thread }
                connection.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (!line.startsWith("data:")) return@forEach
                        val raw = line.removePrefix("data:").trim()
                        if (raw.isBlank() || raw == "[DONE]") return@forEach
                        runCatching {
                            val text = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
                            if (text.isNotEmpty()) { full.append(text); onChunk(text) }
                        }
                    }
                }
                onDone(full.toString())
            } catch (_: Throwable) { onDone(if (full.isNotEmpty()) full.toString() else "I couldn't reach the online AI.") }
            finally { connection?.disconnect() }
        }.start()
    }
}