package org.fossify.gallery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.fossify.gallery.helpers.Perf

/**
 * Reads [Perf] out to logcat on request, so `perf.py counters` can print the table without the app
 * needing a screen for it. Debug builds only: this whole source set is left out of a release.
 */
class PerfReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action?.substringAfterLast('.')) {
            "PERF_RESET" -> {
                Perf.reset()
                Log.d(TAG, "counters reset")
            }

            else -> Perf.report().lineSequence().forEach { Log.d(TAG, it) }
        }
    }

    private companion object {
        const val TAG = "Perf"
    }
}
