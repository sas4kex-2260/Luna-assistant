package com.richa.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class BrainDecision(val type: String, val value: String = "", val extra: String = "")

object GeminiBrain {
    private const val MODEL = "gemini-3.8-flash"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    fun decide(context: Context, userText: String, installedApps: List<String>): BrainDecision {
        val key = SecureStore.getGeminiKey(context) ?: return BrainDecision("chat", "Add a Gemini API key in Richa settings to enable online AI.")
        val appList = installedApps.take(120).joinToString(", ")
        val system = """You are Richa, a careful Android phone assistant.
Return ONLY one compact JSON object. Never return markdown.
Allowed type values: chat, open_app, timer, settings, wifi, bluetooth, remember.
For open_app, value must be an exact visible app label from the supplied list.
For timer, value is seconds. For remember, value is the note.
For chat, value is the spoken answer.
Do not invent installed apps. Do not request passwords, OTPs, authentication codes, or security bypasses.
Do not claim an action happened unless Richa can perform it.
Available launcher apps: $appList
User request: $userText"""
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userText)))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 300))
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 12000; readTimeout = 20000; doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", key)
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) BrainDecision("chat", "The online AI request failed ($code). Check your Gemini key or internet connection.") else parseResponse(raw)
        } catch (t: Throwable) {
            BrainDecision("chat", "I couldn't reach the online AI. ${t.message ?: "Please check your connection."}")
        } finally { connection?.disconnect() }
    }

    private fun parseResponse(raw: String): BrainDecision {
        return try {
            val text = JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).optString("text")
            val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = JSONObject(cleaned)
            BrainDecision(json.optString("type", "chat"), json.optString("value", ""), json.optString("extra", ""))
        } catch (_: Throwable) { BrainDecision("chat", "I received an invalid AI response. I didn't execute any action.") }
    }
}