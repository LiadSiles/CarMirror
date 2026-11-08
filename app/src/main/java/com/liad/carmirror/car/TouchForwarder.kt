package com.liad.carmirror.car

import android.os.Handler
import android.os.Looper
import com.liad.carmirror.MirrorController
import com.liad.carmirror.TouchService

/**
 * Android Auto reports taps with a position, but drags only as distances.
 * Drags are collected and replayed on the phone as one swipe through the middle of the screen.
 */
class TouchForwarder {

    private val handler = Handler(Looper.getMainLooper())
    private var fingerDx = 0f
    private var fingerDy = 0f
    private val flush = Runnable { dispatchSwipe(220L) }

    fun click(x: Float, y: Float) {
        val p = MirrorController.carToPhone(x, y) ?: return
        TouchService.tap(p.x, p.y)
    }

    fun scroll(distanceX: Float, distanceY: Float) {
        // distance = previous - current position, so the finger moved the opposite way.
        fingerDx -= distanceX
        fingerDy -= distanceY
        handler.removeCallbacks(flush)
        handler.postDelayed(flush, 70)
    }

    fun fling(velocityX: Float, velocityY: Float) {
        handler.removeCallbacks(flush)
        fingerDx += velocityX * 0.05f
        fingerDy += velocityY * 0.05f
        dispatchSwipe(80L) // a fast swipe makes the phone fling too
    }

    private fun dispatchSwipe(durationMs: Long) {
        val dx = fingerDx
        val dy = fingerDy
        fingerDx = 0f
        fingerDy = 0f
        if (dx * dx + dy * dy < 16f) return

        val phone = MirrorController.phoneSize()
        val s = MirrorController.scale()
        val startX = phone.x / 2f
        val startY = phone.y / 2f
        val endX = (startX + dx / s).coerceIn(1f, phone.x - 1f)
        val endY = (startY + dy / s).coerceIn(1f, phone.y - 1f)
        TouchService.swipe(startX, startY, endX, endY, durationMs)
    }
}
