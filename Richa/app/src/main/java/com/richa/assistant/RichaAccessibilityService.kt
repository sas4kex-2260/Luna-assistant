package com.richa.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RichaAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun clickVisibleText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }
}
