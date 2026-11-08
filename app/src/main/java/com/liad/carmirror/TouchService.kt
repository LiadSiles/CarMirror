package com.liad.carmirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/** Turns taps and swipes on the car screen into real touches on the phone. */
class TouchService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: TouchService? = null
        val isEnabled: Boolean get() = instance != null

        fun tap(x: Float, y: Float): Boolean {
            val path = Path().apply { moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f)) }
            return instance?.gesture(path, 60) ?: false
        }

        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
            val path = Path().apply {
                moveTo(x1.coerceAtLeast(0f), y1.coerceAtLeast(0f))
                lineTo(x2.coerceAtLeast(0f), y2.coerceAtLeast(0f))
            }
            return instance?.gesture(path, durationMs) ?: false
        }

        fun global(action: Int): Boolean = instance?.performGlobalAction(action) ?: false
    }

    override fun onServiceConnected() {
        instance = this
        MirrorController.notifyChanged()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        MirrorController.notifyChanged()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private fun gesture(path: Path, durationMs: Long): Boolean {
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
