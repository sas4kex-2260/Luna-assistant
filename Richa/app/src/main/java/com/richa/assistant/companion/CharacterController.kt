package com.richa.assistant.companion

/**
 * Provider-neutral contract for Richa's future 3D/VRM/Live2D companion.
 * The actual renderer is selected only after the supplied model format is inspected.
 */
interface CharacterController {
    fun loadModel(source: Any)
    fun playIdle()
    fun lookAtUser()
    fun blink()
    fun setEmotion(emotion: Emotion)
    fun startTalking()
    fun updateLipSync(viseme: String, intensity: Float)
    fun stopTalking()
    fun dispose()

    enum class Emotion { NEUTRAL, HAPPY, SAD, SURPRISED, CONFUSED, THINKING, EXCITED, EMBARRASSED, CONCERNED, SLEEPY, CURIOUS }
}
