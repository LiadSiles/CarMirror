package com.liad.carmirror.car

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.liad.carmirror.MirrorController
import com.liad.carmirror.R
import com.liad.carmirror.TouchService
import kotlin.math.abs

/** The car-screen side: hands the car's drawing surface to MirrorController and forwards touches. */
class MirrorScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val touch = TouchForwarder()
    private val stateListener: () -> Unit = { invalidate() }
    private var speedListener: OnCarDataAvailableListener<Speed>? = null

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(container: SurfaceContainer) {
            val s = container.surface ?: return
            MirrorController.attachSurface(carContext, s, container.width, container.height, container.dpi)
        }

        override fun onSurfaceDestroyed(container: SurfaceContainer) {
            container.surface?.let { MirrorController.detachSurface(it) }
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) = Unit
        override fun onStableAreaChanged(stableArea: Rect) = Unit

        override fun onClick(x: Float, y: Float) {
            if (!checkTouch()) return
            touch.click(x, y)
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            if (TouchService.isEnabled) touch.scroll(distanceX, distanceY)
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            if (TouchService.isEnabled) touch.fling(velocityX, velocityY)
        }

        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) = Unit
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
        MirrorController.addListener(stateListener)
        startSpeedWatch()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        MirrorController.removeListener(stateListener)
        stopSpeedWatch()
    }

    override fun onGetTemplate(): Template {
        val keys = ActionStrip.Builder()
            .addAction(key("Back", AccessibilityService.GLOBAL_ACTION_BACK))
            .addAction(key("Home", AccessibilityService.GLOBAL_ACTION_HOME))
            .addAction(key("Apps", AccessibilityService.GLOBAL_ACTION_RECENTS))
            .build()
        val mapKeys = ActionStrip.Builder().addAction(Action.PAN).build()
        return NavigationTemplate.Builder()
            .setActionStrip(keys)
            .setMapActionStrip(mapKeys)
            .build()
    }

    private fun key(title: String, globalAction: Int): Action =
        Action.Builder()
            .setTitle(title)
            .setOnClickListener {
                if (checkTouch()) TouchService.global(globalAction)
            }
            .build()

    private fun checkTouch(): Boolean {
        if (TouchService.isEnabled) return true
        val hasTouch = carContext.resources.getBoolean(R.bool.has_touch)
        CarToast.makeText(
            carContext,
            if (hasTouch) "Turn on touch control in Car Mirror on your phone"
            else "Car Mirror Lite has no touch control. Use your phone to control it.",
            CarToast.LENGTH_LONG
        ).show()
        return false
    }

    // ------------------------------------------------ parked-only safety

    private fun startSpeedWatch() {
        try {
            val info = carContext.getCarService(CarHardwareManager::class.java).carInfo
            val listener = OnCarDataAvailableListener<Speed> { speed ->
                val value = speed.rawSpeedMetersPerSecond
                if (value.status == CarValue.STATUS_SUCCESS) {
                    val mps = abs(value.value ?: 0f)
                    // Pause above ~7 km/h, resume below ~2 km/h.
                    val pause = if (MirrorController.drivingPause) mps > 0.5f else mps > 2f
                    MirrorController.setDrivingPause(pause)
                }
            }
            info.addSpeedListener(ContextCompat.getMainExecutor(carContext), listener)
            speedListener = listener
        } catch (e: Exception) {
            Log.w("MirrorScreen", "Vehicle speed not available", e)
        }
    }

    private fun stopSpeedWatch() {
        val l = speedListener ?: return
        runCatching {
            carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(l)
        }
        speedListener = null
    }
}
