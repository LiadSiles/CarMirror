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
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.liad.carmirror.adb.PlayInstaller
import java.io.File
import java.util.concurrent.Executors

/** Phone-side setup screen: permissions, touch control and the Start/Stop button. */
class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var notifButton: Button
    private lateinit var touchButton: Button
    private lateinit var mirrorButton: Button
    private lateinit var appInfoButton: Button
    private val hasTouch by lazy { resources.getBoolean(R.bool.has_touch) }

    // "Show in Android Auto" section
    private lateinit var aaStatus: TextView
    private lateinit var aaButtons: List<Button>
    private lateinit var aaProgress: TextView
    private val worker = Executors.newSingleThreadExecutor()

    private val apkPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) installFromFile(uri)
        }

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
        appInfoButton = button("Open app info (if touch control is greyed out)") {
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

        val aaTitle = TextView(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            text = "\nShow in Android Auto"
        }
        aaStatus = TextView(this).apply { textSize = 15f }
        aaProgress = TextView(this).apply { textSize = 15f }
        aaButtons = listOf(
            button("1. Turn on Wireless debugging") {
                PlayInstaller.openWirelessDebugging(this)
            },
            button("2. Pair (type the code in the notification)") {
                PlayInstaller.showPairingNotification(this)
                PlayInstaller.openWirelessDebugging(this)
            },
            button("3. Make visible in Android Auto") {
                runInstall("Reinstalling Car Mirror… the app will close by itself when it's done. Open it again after.") {
                    PlayInstaller.reinstallSelf(this)
                }
            },
            button("Install an update from a file…") {
                apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
            },
        )
        val aaHelp = TextView(this).apply {
            textSize = 14f
            text = """
                Android Auto only shows apps installed from Google Play. These steps reinstall Car Mirror so Android records it that way, using your phone's own Wireless debugging (no other apps, no computer).
                • Once: Settings → About phone → tap "OS version" (or "Build number") 7 times to turn on Developer options.
                • The phone must be on Wi-Fi (internet not needed).
                • In step 2, tap "Pair device with pairing code" in Wireless debugging, then type the 6 digits into Car Mirror's notification.
                • Install future updates with "Install an update from a file…" so Android Auto keeps showing the app.
                • You can turn Wireless debugging off when you're done.
            """.trimIndent()
        }

        val help = TextView(this).apply {
            textSize = 15f
            text = """
                How to use
                1. Connect your phone to the car and open Car Mirror on the car screen.
                   (First time only: do "Show in Android Auto" above, then in Android Auto settings → Customize launcher, check Car Mirror.)
                2. Tap "Start mirroring" here and choose "Entire screen".
                3. Taps and swipes on the car screen control the phone. Back / Home / Apps are on the car screen's side buttons.

                Good to know
                • The picture is hidden whenever the car is moving and comes back when you stop.
                • Keep the phone unlocked while mirroring. Turn it sideways for full-width video.
                • Netflix, Disney+ and other protected apps show a black screen: they block screen capture.
                • If "Car Mirror touch control" is greyed out in Accessibility: Open app info → ⋮ (top right) → Allow restricted settings, then try again.
            """.trimIndent()
        }

        val aaViews: List<View> =
            if (PlayInstaller.supported) listOf(aaTitle, aaStatus) + aaButtons + listOf(aaProgress, aaHelp)
            else listOf(aaTitle, aaStatus)
        (listOf(status, notifButton, touchButton, appInfoButton, mirrorButton) + aaViews + help).forEach {
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
            appendLine("Touch control: " + when {
                !hasTouch -> "not in Lite version"
                touchOk -> "on ✓"
                else -> "off"
            })
            append("Mirroring: " + when {
                mirroring && MirrorController.drivingPause -> "paused (car moving)"
                mirroring -> "on ✓"
                else -> "off"
            })
        }
        if (!hasTouch) {
            touchButton.visibility = View.GONE
            appInfoButton.visibility = View.GONE
        }
        notifButton.isEnabled = !notifOk
        notifButton.text = if (notifOk) "Notifications allowed ✓" else "Allow notifications"
        touchButton.text = if (touchOk) "Touch control on ✓" else "Turn on touch control"
        mirrorButton.text = if (mirroring) "Stop mirroring" else "Start mirroring"
        aaStatus.text = when {
            !PlayInstaller.supported -> "Needs Android 11 or newer. On this phone, install Car Mirror from a computer: adb install -r -i com.android.vending CarMirror.apk"
            PlayInstaller.installedFromPlay(this) -> "Android Auto can see Car Mirror ✓"
            else -> "Android Auto is hiding Car Mirror right now, because it wasn't installed from Google Play. Do steps 1–3 below."
        }
    }

    private fun runInstall(startMessage: String, action: () -> Result<String>) {
        aaProgress.text = startMessage
        aaButtons.forEach { it.isEnabled = false }
        worker.execute {
            val result = action()
            runOnUiThread {
                aaButtons.forEach { it.isEnabled = true }
                aaProgress.text = result.fold(
                    onSuccess = { "Done ✓ Android Auto can now see Car Mirror. Force stop Android Auto (or reconnect the car), then check it in Customize launcher." },
                    onFailure = { "Didn't work: ${it.message}" }
                )
                refresh()
            }
        }
    }

    private fun installFromFile(uri: Uri) {
        runInstall("Installing the update…") {
            runCatching {
                val file = File(cacheDir, "update.apk")
                contentResolver.openInputStream(uri)!!.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                file
            }.mapCatching { file ->
                try {
                    PlayInstaller.install(this, file).getOrThrow()
                } finally {
                    file.delete()
                }
            }
        }
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }
}
