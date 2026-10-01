package com.richa.assistant
import android.content.Context

class MemoryService(context: Context) {
    private val memory = LocalMemory(context.applicationContext)
    fun remember(note: String) = memory.remember(note)
    fun recall(): String = memory.recall().ifBlank { "No saved memories." }
    fun clear() = memory.clear()
}
