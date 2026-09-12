package org.fossify.gallery.helpers

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Lets one tap open a fullscreen screen, and turns every other away until the screen that opened it
 * is back on top. A viewer takes a few hundred milliseconds to cover the grid, and a second tile
 * tapped in that time would open a second viewer over the first.
 */
class ViewerLaunchGuard(owner: LifecycleOwner) {
    private var launchedAt = 0L
    private var wasPaused = false

    init {
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                wasPaused = launchedAt != 0L
            }

            override fun onResume(owner: LifecycleOwner) {
                launchedAt = 0L
                wasPaused = false
            }
        })
    }

    /** Whether a launch may go ahead, claiming the screen for it if so. */
    fun tryClaim(): Boolean {
        val now = SystemClock.uptimeMillis()
        // a launch that never covered the screen - nothing to open the file with - lets go on its own
        if (launchedAt != 0L && (wasPaused || now - launchedAt < UNCOVERED_LAUNCH_TIMEOUT_MS)) {
            return false
        }

        launchedAt = now
        wasPaused = false
        return true
    }
}

private const val UNCOVERED_LAUNCH_TIMEOUT_MS = 1000L
