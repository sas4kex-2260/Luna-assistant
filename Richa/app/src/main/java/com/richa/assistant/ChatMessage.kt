package com.richa.assistant

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val role: Role,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role { USER, ASSISTANT }
}
