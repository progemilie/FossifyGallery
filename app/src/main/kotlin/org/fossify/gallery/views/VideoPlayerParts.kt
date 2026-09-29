package org.fossify.gallery.views

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import org.fossify.gallery.R

/** How long a skip hint stays up after the last tap of a run, and how a new one pops in. */
private const val SEEK_HINT_MS = 700L
private const val SEEK_HINT_FADE_MS = 150L
private const val SEEK_HINT_POP = 0.85f

/** A held rewind winds back twice as fast as the video plays, asking for a new frame this often. */
private const val REWIND_SPEED = 2
private const val REWIND_INTERVAL_MS = 100L

/** How long a playing video is left alone before its chrome goes. */
private const val CHROME_AUTO_HIDE_MS = 3000L

/**
 * What a double tap on either side of a video has skipped, on that side: "- 10s", then "- 20s" if the
 * taps keep coming, the way the players people know count a run of them up.
 */
class SeekHints(private val back: TextView, private val forward: TextView) {
    private var shownForward: Boolean? = null
    private var seconds = 0

    private val hide = Runnable {
        shownForward = null
        listOf(back, forward).forEach { it.animate().alpha(0f).setDuration(SEEK_HINT_FADE_MS).start() }
    }

    fun show(isForward: Boolean, stepSeconds: Int) {
        seconds = if (shownForward == isForward) seconds + stepSeconds else stepSeconds
        shownForward = isForward

        val hint = if (isForward) forward else back
        val other = if (isForward) back else forward
        other.animate().alpha(0f).setDuration(SEEK_HINT_FADE_MS).start()

        hint.text = hint.context.getString(
            if (isForward) R.string.video_seek_forward_display_text else R.string.video_seek_back_display_text,
            seconds
        )

        hint.animate().cancel()
        hint.scaleX = SEEK_HINT_POP
        hint.scaleY = SEEK_HINT_POP
        hint.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(SEEK_HINT_FADE_MS).start()

        hint.removeCallbacks(hide)
        hint.postDelayed(hide, SEEK_HINT_MS)
    }
}

/**
 * Winds a video back for as long as a finger is held on its left side. ExoPlayer plays nothing
 * backwards, so it seeks, each time to wherever the clock says the wind has got to: a decoder too
 * slow to show every step still winds back at the same speed, only in fewer frames. The player
 * should be in scrubbing mode meanwhile, or each seek cancels the last before it has drawn.
 */
class RewindScan(private val view: View, private val seekTo: (ms: Long) -> Unit) {
    var isRunning = false
        private set

    private var from = 0L
    private var startedAt = 0L

    private val step = object : Runnable {
        override fun run() {
            val position = (from - (SystemClock.uptimeMillis() - startedAt) * REWIND_SPEED).coerceAtLeast(0L)
            seekTo(position)
            if (position > 0L) {
                view.postDelayed(this, REWIND_INTERVAL_MS)
            }
        }
    }

    fun start(from: Long) {
        this.from = from
        startedAt = SystemClock.uptimeMillis()
        isRunning = true
        view.removeCallbacks(step)
        view.post(step)
    }

    fun stop() {
        isRunning = false
        view.removeCallbacks(step)
    }
}

/**
 * Takes the chrome away from a playing video once it has been left alone for a moment, the way a
 * phone's own player does: every touch of a control starts the wait again, and pausing ends it.
 */
class ChromeAutoHide(private val view: View, hide: () -> Unit) {
    private val run = Runnable(hide)

    fun restart() {
        view.removeCallbacks(run)
        view.postDelayed(run, CHROME_AUTO_HIDE_MS)
    }

    fun cancel() {
        view.removeCallbacks(run)
    }
}
