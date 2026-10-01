package com.richa.assistant.companion

object EmotionEngine {
    fun detect(text: String): CharacterController.Emotion {
        val t = text.lowercase()
        return when {
            listOf("wow", "amazing", "awesome", "yay", "excited").any { t.contains(it) } -> CharacterController.Emotion.EXCITED
            listOf("sorry", "sad", "unfortunately", "miss").any { t.contains(it) } -> CharacterController.Emotion.SAD
            listOf("why", "confused", "not sure", "what do you mean").any { t.contains(it) } -> CharacterController.Emotion.CONFUSED
            listOf("haha", "lol", "glad", "great").any { t.contains(it) } -> CharacterController.Emotion.HAPPY
            listOf("careful", "warning", "danger", "concern").any { t.contains(it) } -> CharacterController.Emotion.CONCERNED
            listOf("thinking", "let me think").any { t.contains(it) } -> CharacterController.Emotion.THINKING
            listOf("curious", "interesting").any { t.contains(it) } -> CharacterController.Emotion.CURIOUS
            else -> CharacterController.Emotion.NEUTRAL
        }
    }
}
