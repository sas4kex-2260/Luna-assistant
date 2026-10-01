package com.richa.assistant.companion

import android.content.Context
import android.graphics.Color
import android.view.Choreographer
import android.view.SurfaceView
import android.widget.FrameLayout
import com.google.android.filament.Filament
import com.google.android.filament.utils.ModelViewer
import java.io.File
import java.nio.ByteBuffer

class RichaCompanionView(context: Context) : FrameLayout(context), CharacterController {
    private val surface = SurfaceView(context)
    private var viewer: ModelViewer? = null
    private var loadedFile: File? = null
    private var talking = false
    private var requestedEmotion = CharacterController.Emotion.NEUTRAL
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            viewer?.render(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        Filament.init()
        surface.setBackgroundColor(Color.TRANSPARENT)
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        surface.setOnTouchListener { _, event -> viewer?.onTouchEvent(event) ?: false }
        post { viewer = ModelViewer(surface) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        viewer?.destroy()
        viewer = null
        super.onDetachedFromWindow()
    }

    override fun loadModel(source: Any) {
        val file = source as? File ?: return
        if (!file.isFile) return
        loadedFile = file
        post {
            val v = viewer ?: return@post
            v.loadModelGlb(ByteBuffer.wrap(file.readBytes()))
            v.transformToUnitCube()
            v.autoPlayAnimations = true
            playIdle()
        }
    }

    override fun playIdle() {
        val v = viewer ?: return
        val animator = v.animator ?: return
        if (animator.animationCount > 0) {
            val names = (0 until animator.animationCount).map { animator.getAnimationName(it).lowercase() }
            v.activeAnimationIndex = names.indexOfFirst { it.contains("idle") }.takeIf { it >= 0 } ?: 0
        }
    }

    override fun lookAtUser() {
        // The Filament camera follows the interactive companion view. A future face-tracker
        // can feed precise gaze targets without changing the renderer contract.
    }

    override fun blink() {
        // Uses the model's authored animation/blendshape once its actual names are inspected.
    }

    override fun setEmotion(emotion: CharacterController.Emotion) {
        requestedEmotion = emotion
        val v = viewer ?: return
        val animator = v.animator ?: return
        if (animator.animationCount == 0) return
        val key = emotion.name.lowercase()
        val index = (0 until animator.animationCount).firstOrNull {
            animator.getAnimationName(it).lowercase().contains(key)
        }
        if (index != null) v.activeAnimationIndex = index
    }

    override fun startTalking() {
        talking = true
        val v = viewer ?: return
        val animator = v.animator ?: return
        val index = (0 until animator.animationCount).firstOrNull {
            val n = animator.getAnimationName(it).lowercase()
            n.contains("talk") || n.contains("speak")
        }
        if (index != null) v.activeAnimationIndex = index
    }

    override fun updateLipSync(viseme: String, intensity: Float) {
        val v = viewer ?: return
        val animator = v.animator ?: return
        if (animator.animationCount == 0) return
        if (intensity > 0.08f) {
            val index = (0 until animator.animationCount).firstOrNull {
                val n = animator.getAnimationName(it).lowercase()
                n.contains("talk") || n.contains("speak") || n.contains("mouth")
            }
            if (index != null) v.activeAnimationIndex = index
        } else playIdle()
    }
    override fun stopTalking() {
        talking = false
        playIdle()
    }

    override fun dispose() {
        viewer?.destroyModel()
        viewer = null
    }

    fun modelFile(): File? = loadedFile
    fun currentEmotion(): CharacterController.Emotion = requestedEmotion
}
