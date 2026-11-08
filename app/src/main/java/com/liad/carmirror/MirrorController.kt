package com.liad.carmirror

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.widget.TextView
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Connects the phone's screen (MediaProjection) to the car's drawing surface (Android Auto).
 *
 * Either side can show up first: the car surface appears when you open the app on the car
 * screen, the projection appears when you tap "Start mirroring" on the phone.
 * All state is touched on the main thread only.
 */
object MirrorController {

    private const val TAG = "MirrorController"
    private val main = Handler(Looper.getMainLooper())

    // Car side
    private var carContext: Context? = null
    private var surface: Surface? = null
    var surfaceWidth = 0
        private set
    var surfaceHeight = 0
        private set
    private var surfaceDpi = 160

    // Phone side
    private var projection: MediaProjection? = null
    private var onProjectionEnded: (() -> Unit)? = null
    private var mirrorDisplay: VirtualDisplay? = null

    // Message shown on the car screen when not mirroring
    private var messageDisplay: VirtualDisplay? = null
    private var messagePresentation: Presentation? = null
    private var messageView: TextView? = null

    /** True while the car is moving; the mirror is hidden. */
    var drivingPause = false
        private set

    private var appContext: Context? = null
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    val isMirroring: Boolean get() = projection != null
    val isCarConnected: Boolean get() = surface != null

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    fun notifyChanged() = onMain { listeners.forEach { it() } }

    // ---------------------------------------------------------------- phone side

    fun startProjection(context: Context, p: MediaProjection, onEnded: () -> Unit) = onMain {
        appContext = context.applicationContext
        stopProjection()
        projection = p
        onProjectionEnded = onEnded
        // Android 14+ requires a callback to be registered before creating a display.
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = onMain { if (projection === p) releaseProjection(stop = false) }
        }, main)
        update()
        notifyChanged()
    }

    fun stopProjection() = onMain { releaseProjection(stop = true) }

    private fun releaseProjection(stop: Boolean) {
        val p = projection ?: return
        projection = null
        mirrorDisplay?.release()
        mirrorDisplay = null
        if (stop) runCatching { p.stop() }
        val ended = onProjectionEnded
        onProjectionEnded = null
        ended?.invoke()
        update()
        notifyChanged()
    }

    // ---------------------------------------------------------------- car side

    fun attachSurface(context: Context, s: Surface, width: Int, height: Int, dpi: Int) = onMain {
        if (surface != null && surface !== s) detachSurface(surface!!)
        carContext = context
        appContext = context.applicationContext
        surface = s
        surfaceWidth = width
        surfaceHeight = height
        surfaceDpi = if (dpi > 0) dpi else 160
        update()
        notifyChanged()
    }

    fun detachSurface(s: Surface) = onMain {
        if (surface !== s) return@onMain
        mirrorDisplay?.surface = null
        hideMessage()
        surface = null
        notifyChanged()
    }

    fun setDrivingPause(paused: Boolean) = onMain {
        if (drivingPause == paused) return@onMain
        drivingPause = paused
        update()
        notifyChanged()
    }

    // ---------------------------------------------------------------- rendering

    private fun update() {
        val s = surface ?: return
        val p = projection
        val message = when {
            p == null -> "Open Car Mirror on your phone\nand tap \"Start mirroring\""
            drivingPause -> "Paused while driving\nThe picture comes back when you stop"
            else -> null
        }
        if (message != null) {
            mirrorDisplay?.surface = null
            showMessage(s, message)
            return
        }
        hideMessage()
        val d = mirrorDisplay
        try {
            if (d == null) {
                // AUTO_MIRROR: the phone screen is scaled to fit and centred (letterboxed).
                mirrorDisplay = p!!.createVirtualDisplay(
                    "CarMirror", surfaceWidth, surfaceHeight, surfaceDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, s, null, main
                )
            } else {
                // A projection may only create one display (Android 14+), so reuse it.
                d.resize(surfaceWidth, surfaceHeight, surfaceDpi)
                d.surface = s
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not start mirror display", e)
            showMessage(s, "Couldn't start mirroring.\nTap Stop, then Start on your phone.")
        }
    }

    private fun showMessage(s: Surface, text: String) {
        val ctx = carContext ?: return
        if (messageDisplay == null) {
            val dm = ctx.getSystemService(DisplayManager::class.java)
            // A private, app-owned display: draws only our own Presentation (black otherwise).
            messageDisplay = dm.createVirtualDisplay(
                "CarMirrorMessage", surfaceWidth, surfaceHeight, surfaceDpi, s,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            )
            val display = messageDisplay!!.display
            val tv = TextView(ctx).apply {
                setBackgroundColor(Color.BLACK)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            }
            messageView = tv
            messagePresentation = Presentation(ctx, display).apply {
                setContentView(tv)
                try { show() } catch (e: Exception) { Log.w(TAG, "Message not shown", e) }
            }
        }
        messageView?.text = text
    }

    private fun hideMessage() {
        runCatching { messagePresentation?.dismiss() }
        messagePresentation = null
        messageView = null
        messageDisplay?.release()
        messageDisplay = null
    }

    // ---------------------------------------------------------------- touch mapping

    /** Current phone screen size in pixels, in its current rotation. */
    fun phoneSize(): Point {
        val ctx = appContext ?: return Point(1080, 2400)
        val dm = ctx.getSystemService(DisplayManager::class.java)
        val p = Point()
        @Suppress("DEPRECATION")
        dm.getDisplay(Display.DEFAULT_DISPLAY).getRealSize(p)
        return p
    }

    /** Car pixels per phone pixel for the letterboxed mirror. */
    fun scale(): Float {
        val ph = phoneSize()
        if (surfaceWidth == 0 || surfaceHeight == 0) return 1f
        return minOf(surfaceWidth / ph.x.toFloat(), surfaceHeight / ph.y.toFloat())
    }

    /** Converts a point on the car screen to the matching point on the phone, or null if it's in the black bars. */
    fun carToPhone(x: Float, y: Float): PointF? {
        val ph = phoneSize()
        val s = scale()
        val offX = (surfaceWidth - ph.x * s) / 2f
        val offY = (surfaceHeight - ph.y * s) / 2f
        val px = (x - offX) / s
        val py = (y - offY) / s
        if (px < 0 || py < 0 || px >= ph.x || py >= ph.y) return null
        return PointF(px, py)
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }
}
