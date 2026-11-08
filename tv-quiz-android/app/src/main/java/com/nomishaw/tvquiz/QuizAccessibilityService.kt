package com.nomishaw.tvquiz

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.view.accessibility.AccessibilityEvent

/**
 * Turned on by the parent in Settings → Accessibility. Tells [Engine] which
 * app is in front, and lets it show the quiz on top of other apps — no adb needed.
 */
class QuizAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Engine.start(this)
        try { WatchService.start(this) } catch (e: Exception) { /* not required */ }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        // Only count real activities, not keyboards, toasts or volume pop-ups.
        val isActivity = try {
            packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
            true
        } catch (e: Exception) {
            false
        }
        if (isActivity) Engine.a11yForeground = pkg
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: QuizAccessibilityService? = null
    }
}
