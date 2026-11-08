package com.liad.carmirror.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.liad.carmirror.MainActivity
import com.liad.carmirror.R
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * "Install as from Google Play", built in.
 *
 * Android Auto only lists apps whose installer is recorded as the Play Store. Only the shell
 * (adb) may set that, so Car Mirror connects to the phone's own Wireless debugging and runs
 * `cmd package install -i com.android.vending` on itself, exactly like `adb install -i` from a PC.
 */
object PlayInstaller {

    private const val TAG = "PlayInstaller"
    const val PLAY = "com.android.vending"
    const val KEY_CODE = "pairing_code"
    private const val CHANNEL = "setup"
    private const val NOTIFICATION_ID = 7

    /** Wireless debugging exists from Android 11. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 30

    fun installedFromPlay(context: Context): Boolean {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName == PLAY
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName) == PLAY
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Opens Developer options, scrolled to Wireless debugging where the phone supports it. */
    fun openWirelessDebugging(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ------------------------------------------------------------------ pairing

    /**
     * Shows a notification you can type the 6-digit pairing code into.
     * (The pairing dialog in Settings closes if you switch apps, so the code is typed in the notification.)
     */
    fun showPairingNotification(context: Context, text: String = "Open \"Pair device with pairing code\", then type the code here") {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Setup", NotificationManager.IMPORTANCE_HIGH)
        )
        val input = RemoteInput.Builder(KEY_CODE).setLabel("6-digit pairing code").build()
        val reply = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, PairReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val action = Notification.Action.Builder(null, "Enter code", reply)
            .addRemoteInput(input)
            .build()
        nm.notify(NOTIFICATION_ID, base(context, "Pair Car Mirror", text).addAction(action).build())
    }

    fun showResultNotification(context: Context, title: String, text: String) {
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, base(context, title, text).setAutoCancel(true).build())
    }

    private fun base(context: Context, title: String, text: String): Notification.Builder {
        val open = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
    }

    /** Finds the pairing port (shown only while the pairing dialog is open) and pairs with [code]. */
    fun pair(context: Context, code: String): Result<Unit> = runCatching {
        val port = findLocalService(context, "_adb-tls-pairing._tcp", 8_000)
            ?: error("Couldn't find the pairing dialog. Keep \"Pair device with pairing code\" open and try again.")
        val ok = AdbManager.get(context).pair("127.0.0.1", port, code)
        if (!ok) error("Pairing failed. Check the code and try again.")
    }

    // ------------------------------------------------------------------ install

    /** Reinstalls Car Mirror itself, marked as installed from Google Play. The app restarts afterwards. */
    fun reinstallSelf(context: Context): Result<String> =
        install(context, File(context.applicationInfo.sourceDir))

    /** Installs [apk] (e.g. a downloaded Car Mirror update), marked as installed from Google Play. */
    fun install(context: Context, apk: File): Result<String> = runCatching {
        val adb = AdbManager.get(context)
        val connected = runCatching { adb.isConnected }.getOrDefault(false) ||
            adb.autoConnect(context.applicationContext, 10_000)
        if (!connected) {
            error("Couldn't reach Wireless debugging. Make sure it's on, the phone is on Wi-Fi, and you've paired.")
        }
        val stream = adb.openStream("exec:cmd package install -r -i $PLAY -S ${apk.length()}")
        val out = stream.openOutputStream()
        apk.inputStream().use { it.copyTo(out) }
        out.flush()
        // If this was Car Mirror itself, Android stops the app here once the install finishes.
        val result = stream.openInputStream().bufferedReader().readText().trim()
        runCatching { stream.close() }
        if (!result.contains("Success")) error(result.ifBlank { "Install failed" })
        result
    }

    // ------------------------------------------------------------------ mDNS

    /** Discovers an adb mDNS service advertised by this phone and returns its port. */
    private fun findLocalService(context: Context, type: String, timeoutMs: Long): Int? {
        val nsd = context.getSystemService(NsdManager::class.java)
        val local = localAddresses()
        val done = CountDownLatch(1)
        val found = AtomicInteger(-1)
        val resolving = AtomicBoolean(false)

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                // NsdManager resolves one service at a time.
                if (found.get() != -1 || !resolving.compareAndSet(false, true)) return
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = resolved.host
                        if (host == null || host.isLoopbackAddress || local.contains(host)) {
                            found.compareAndSet(-1, resolved.port)
                            done.countDown()
                        }
                        resolving.set(false)
                    }

                    override fun onResolveFailed(i: NsdServiceInfo, errorCode: Int) {
                        resolving.set(false)
                    }
                })
            }

            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { done.countDown() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery)
        done.await(timeoutMs, TimeUnit.MILLISECONDS)
        runCatching { nsd.stopServiceDiscovery(discovery) }
        val port = found.get().takeIf { it > 0 }
        Log.i(TAG, "$type -> port $port")
        return port
    }

    private fun localAddresses(): Set<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .toSet()
    }.getOrDefault(emptySet())
}
