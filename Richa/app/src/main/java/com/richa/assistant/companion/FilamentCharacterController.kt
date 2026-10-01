package com.richa.assistant.companion

import android.content.Context
import android.view.SurfaceView
import com.google.android.filament.Engine
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.EntityManager

/**
 * Filament-backed character runtime foundation.
 *
 * This deliberately owns lifecycle/model loading only in this step. Rendering,
 * animation clips, blendshapes and visemes are attached after the supplied GLB
 * is inspected, rather than guessing at node names or morph targets.
 */
class FilamentCharacterController(
    private val context: Context,
    private val surfaceView: SurfaceView
) : CharacterController {

    private var engine: Engine? = null
    private var assetLoader: AssetLoader? = null
    private var materialProvider: UbershaderProvider? = null
    private var resourceLoader: ResourceLoader? = null
    private var asset: FilamentAsset? = null

    override fun loadModel(source: Any) {
        val file = source as? java.io.File ?: return
        if (!file.exists()) return
        dispose()
        engine = Engine.create()
        val e = engine ?: return
        materialProvider = UbershaderProvider(e)
        assetLoader = AssetLoader(e, materialProvider, EntityManager.get())
        val bytes = file.readBytes()
        asset = assetLoader?.createAsset(bytes)
        resourceLoader = ResourceLoader(e)
        asset?.let { resourceLoader?.loadResources(it) }
    }

    override fun playIdle() {}
    override fun lookAtUser() {}
    override fun blink() {}
    override fun setEmotion(emotion: CharacterController.Emotion) {}
    override fun startTalking() {}
    override fun updateLipSync(viseme: String, intensity: Float) {}
    override fun stopTalking() {}

    override fun dispose() {
        resourceLoader?.destroy()
        resourceLoader = null
        asset?.let { assetLoader?.destroyAsset(it) }
        asset = null
        assetLoader?.destroy()
        assetLoader = null
        materialProvider?.destroyMaterials()
        materialProvider = null
        engine?.destroy()
        engine = null
    }
}
