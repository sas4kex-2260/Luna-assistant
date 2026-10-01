package com.richa.assistant.companion

import android.content.Context
import java.io.File

/**
 * Keeps the character asset outside the Git repository.
 *
 * Put a user-owned/licensed Waguri GLB at:
 *   <app-private-files>/companion/waguri.glb
 *
 * This avoids silently redistributing a third-party character file while still
 * making the runtime ready for a real model.
 */
class CompanionAssetStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "companion")

    init { root.mkdirs() }

    fun modelFile(): File = File(root, "waguri.glb")

    fun hasModel(): Boolean = modelFile().isFile && modelFile().length() > 0

    fun deleteModel(): Boolean = !modelFile().exists() || modelFile().delete()

    fun copyFrom(source: File): File {
        source.copyTo(modelFile(), overwrite = true)
        return modelFile()
    }
}
