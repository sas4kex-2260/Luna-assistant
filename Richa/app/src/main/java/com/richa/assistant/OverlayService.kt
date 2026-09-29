package com.richa.assistant

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var view: View? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = layoutInflater.inflate(R.layout.overlay_richa, FrameLayout(this), false)
        view = root
        val params = WindowManager.LayoutParams(
            64.dp(), 64.dp(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        params.x = 16.dp()
        root.setOnClickListener {
            androidx.core.content.ContextCompat.startForegroundService(
                this, Intent(this, WakeWordService::class.java).setAction(WakeWordService.ACTION_TALK_NOW)
            )
        }
        windowManager?.addView(root, params)
    }

    override fun onDestroy() {
        view?.let { runCatching { windowManager?.removeView(it) } }
        super.onDestroy()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    override fun onBind(intent: Intent?): IBinder? = null
}
