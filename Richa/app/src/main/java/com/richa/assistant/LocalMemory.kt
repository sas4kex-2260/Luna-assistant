package com.richa.assistant

import android.content.Context

class LocalMemory(context: Context) {
    private val prefs = context.getSharedPreferences("richa_memory", Context.MODE_PRIVATE)

    fun remember(value: String) {
        prefs.edit().putString("note", value.trim()).apply()
    }

    fun recall(): String? = prefs.getString("note", null)
}
