package com.richa.assistant

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug

data class Diagnostics(
    val android: String,
    val ramUsedMb: Int,
    val ramTotalMb: Int,
    val appHeapMb: Int,
    val gpu: String,
    val ai: String,
    val voice: String,
    val companion: String
)

object DiagnosticsService {
    fun snapshot(context: Context): Diagnostics {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return Diagnostics(
            "Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT,
            ((info.totalMem - info.availMem) / 1048576L).toInt(),
            (info.totalMem / 1048576L).toInt(),
            (Debug.getNativeHeapAllocatedSize() / 1048576L).toInt(),
            "Filament",
            if (SecureStore.hasGeminiKey(context)) "configured" else "not configured",
            "Kokoro / Android TTS",
            "GLB / Filament"
        )
    }
}
