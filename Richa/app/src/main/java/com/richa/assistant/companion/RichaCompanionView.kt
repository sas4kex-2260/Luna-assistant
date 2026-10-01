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
    private var requestedEmotion = CharacterController.Emotion.NEUTRAL
    private var activeAnimation = 0
    private var animationStartNanos = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val v = viewer
            if (v != null) {
                val animator = v.animator
                if (animator != null && animator.animationCount > 0) {
                    if (animationStartNanos == 0L) animationStartNanos = frameTimeNanos
                    val elapsed = (frameTimeNanos - animationStartNanos) / 1_000_000_000f
                    animator.applyAnimation(activeAnimation.coerceIn(0, animator.animationCount - 1), elapsed)
                    animator.updateBoneMatrices()
                }
                v.render(frameTimeNanos)
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        Filament.init()
        surface.setBackgroundColor(Color.TRANSPARENT)
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        surface.setOnTouchListener { _, event ->
            viewer?.onTouchEvent(event)
            true
        }
        post { if (isAttachedToWindow && viewer == null) viewer = ModelViewer(surface) }
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
        if (!file.isFile || file.length() == 0L) return
        loadedFile = file
        post {
            if (!isAttachedToWindow) return@post
            val v = viewer ?: return@post
            runCatching {
                v.loadModelGlb(ByteBuffer.wrap(file.readBytes()))
                v.transformToUnitCube()
                activeAnimation = 0
                animationStartNanos = 0L
                playIdle()
            }
        }
    }

    override fun playIdle() {
        val v = viewer ?: return
        val animator = v.animator ?: return
        if (animator.animationCount > 0) {
            val names = (0 until animator.animationCount).map { animator.getAnimationName(it).lowercase() }
            activeAnimation = names.indexOfFirst { it.contains("idle") }.takeIf { it >= 0 } ?: 0
            animationStartNanos = 0L
        }
    }

    override fun lookAtUser() {
        // Actual eye-bone/blendshape targeting will be mapped after the supplied GLB is inspected.
    }

    override fun blink() {
        // Actual blink control will be mapped after the supplied GLB is inspected.
    }

    override fun setEmotion(emotion: CharacterController.Emotion) {
        requestedEmotion = emotion
        val v = viewer ?: return
        val animator = v.animator ?: return
        val key = emotion.name.lowercase()
        val index = (0 until animator.animationCount).firstOrNull {
            animator.getAnimationName(it).lowercase().contains(key)
        }
        if (index != null) {
            activeAnimation = index
            animationStartNanos = 0L
        }
    }

    override fun startTalking() {
        val v = viewer ?: return
        val animator = v.animator ?: return
        val index = (0 until animator.animationCount).firstOrNull {
            val n = animator.getAnimationName(it).lowercase()
            n.contains("talk") || n.contains("speak")
        }
        if (index != null) {
            activeAnimation = index
            animationStartNanos = 0L
        }
    }

    override fun updateLipSync(viseme: String, intensity: Float) {
        if (intensity > 0.08f) startTalking() else playIdle()
    }

    override fun stopTalking() = playIdle()

    override fun dispose() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        viewer?.destroy()
        viewer = null
    }

    fun modelFile(): File? = loadedFile
    fun currentEmotion(): CharacterController.Emotion = requestedEmotion
}
