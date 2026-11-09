package com.maar.shield

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** While protection is ON, bounces the user to Home if they open a Settings / uninstall screen showing "MAAR Shield". */
class GuardService : AccessibilityService() {
    private val watched = listOf("settings", "packageinstaller", "permissioncontroller")

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || !Unlock.enabled(this)) return
        val p = e.packageName?.toString() ?: return
        if (p == packageName || watched.none { p.contains(it) }) return
        if (System.currentTimeMillis() < Unlock.graceUntil(this)) return
        val root = rootInActiveWindow ?: return
        if (hasName(root, 0)) performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun hasName(n: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (n == null || depth > 30) return false
        val t = n.text?.toString()
        if (t != null && t.contains("MAAR Shield", ignoreCase = true)) return true
        for (i in 0 until n.childCount) if (hasName(n.getChild(i), depth + 1)) return true
        return false
    }

    override fun onInterrupt() {}
}
