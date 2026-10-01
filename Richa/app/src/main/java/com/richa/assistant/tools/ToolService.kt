package com.richa.assistant.tools

/**
 * Safe tool boundary for future web search, files, camera and Android actions.
 * Tools must validate inputs independently of the AI model.
 */
interface ToolService {
    val name: String
    fun canHandle(request: String): Boolean
}
