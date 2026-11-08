package com.liad.carmirror

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** Phone-side setup screen: permissions, touch control and the Start/Stop button. */
class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var notifButton: Button
    private lateinit var touchButton: Button
    private lateinit var mirrorButton: Button

    private val listener: () -> Unit = { refresh() }

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                MirrorService.start(this, result.resultCode, data)
            }
            refresh()
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        }
        notifButton = button("Allow notifications") {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        touchButton = button("Turn on touch control") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        val appInfoButton = button("Open app info (if touch control is greyed out)") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        mirrorButton = button("Start mirroring") {
            if (MirrorController.isMirroring) {
                MirrorService.stop(this)
            } else {
                val mpm = getSystemService(MediaProjectionManager::class.java)
                captureLauncher.launch(mpm.createScreenCaptureIntent())
            }
        }

        val help = TextView(this).apply {
            textSize = 15f
            text = """
                How to use
                1. Connect your phone to the car and open Car Mirror on the car screen.
                   (First time only: Android Auto → tap "Version" 10 times → ⋮ → Developer settings → turn on "Unknown sources", then add Car Mirror in Customize launcher.)
                2. Tap "Start mirroring" here and choose "Entire screen".
                3. Taps and swipes on the car screen control the phone. Back / Home / Apps are on the car screen's side buttons.

                Good to know
                • The picture is hidden whenever the car is moving and comes back when you stop.
                • Keep the phone unlocked while mirroring. Turn it sideways for full-width video.
                • Netflix, Disney+ and other protected apps show a black screen: they block screen capture.
                • If "Car Mirror touch control" is greyed out in Accessibility: Open app info → ⋮ (top right) → Allow restricted settings, then try again.
            """.trimIndent()
        }

        listOf(status, notifButton, touchButton, appInfoButton, mirrorButton, help).forEach {
            column.addView(it, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = pad / 2 })
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        MirrorController.addListener(listener)
        refresh()
    }

    override fun onPause() {
        MirrorController.removeListener(listener)
        super.onPause()
    }

    private fun refresh() {
        val notifOk = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val touchOk = TouchService.isEnabled
        val mirroring = MirrorController.isMirroring
        val car = MirrorController.isCarConnected

        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        status.text = buildString {
            appendLine("Car Mirror v$version")
            appendLine("Car screen: " + if (car) "connected ✓" else "not open yet")
            appendLine("Touch control: " + if (touchOk) "on ✓" else "off")
            append("Mirroring: " + when {
                mirroring && MirrorController.drivingPause -> "paused (car moving)"
                mirroring -> "on ✓"
                else -> "off"
            })
        }
        notifButton.isEnabled = !notifOk
        notifButton.text = if (notifOk) "Notifications allowed ✓" else "Allow notifications"
        touchButton.text = if (touchOk) "Touch control on ✓" else "Turn on touch control"
        mirrorButton.text = if (mirroring) "Stop mirroring" else "Start mirroring"
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }
}
