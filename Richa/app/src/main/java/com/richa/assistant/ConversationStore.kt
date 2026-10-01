package com.richa.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ConversationStore(context: Context) {
    private val prefs = context.getSharedPreferences("richa_conversations", Context.MODE_PRIVATE)

    fun load(): MutableList<ChatMessage> {
        val raw = prefs.getString("messages", null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            MutableList(array.length()) { i ->
                val item = array.getJSONObject(i)
                ChatMessage(
                    id = item.optLong("id"),
                    role = if (item.optString("role") == "USER") ChatMessage.Role.USER else ChatMessage.Role.ASSISTANT,
                    text = item.optString("text"),
                    timestamp = item.optLong("timestamp")
                )
            }
        } catch (_: Throwable) {
            mutableListOf()
        }
    }

    fun save(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.takeLast(100).forEach { message ->
            array.put(JSONObject()
                .put("id", message.id)
                .put("role", message.role.name)
                .put("text", message.text)
                .put("timestamp", message.timestamp))
        }
        prefs.edit().putString("messages", array.toString()).apply()
    }

    fun clear() = prefs.edit().remove("messages").apply()
}
