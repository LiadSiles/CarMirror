package com.liad.carmirror.adb

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** Receives the pairing code typed into the setup notification and pairs in the background. */
class PairReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(PlayInstaller.KEY_CODE)
            ?.filter { it.isDigit() }
            ?.toString()
            .orEmpty()
        if (code.length != 6) {
            PlayInstaller.showPairingNotification(context, "The code has 6 digits. Try again.")
            return
        }
        PlayInstaller.showResultNotification(context, "Pairing…", "One moment")
        val pending = goAsync()
        thread {
            try {
                PlayInstaller.pair(context, code)
                    .onSuccess {
                        PlayInstaller.showResultNotification(
                            context, "Paired ✓",
                            "Go back to Car Mirror and tap \"Make visible in Android Auto\"."
                        )
                    }
                    .onFailure {
                        PlayInstaller.showPairingNotification(context, it.message ?: "Pairing failed. Try again.")
                    }
            } finally {
                pending.finish()
            }
        }
    }
}
