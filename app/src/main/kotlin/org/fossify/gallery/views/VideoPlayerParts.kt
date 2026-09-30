package org.fossify.gallery.views

import android.os.SystemClock
import android.view.MotionEvent
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

/** How long a playing video is left alone before the chrome goes. */
private const val CHROME_AUTO_HIDE_MS = 5000L

/**
 * What a run of double taps has added up to, on the side it went: "- 10s", then "- 20s" while the
 * taps keep coming.
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
 * slow to show every step still winds back at the same speed, only in fewer frames.
 */
class RewindScan(private val view: View, private val seekTo: (ms: Long) -> Unit) {
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
        view.removeCallbacks(step)
        view.post(step)
    }

    fun stop() {
        view.removeCallbacks(step)
    }
}

/**
 * Takes the viewer's chrome away from a playing video once nobody has touched the screen for a moment,
 * the way a phone's own player does. A finger down stops the wait and lifting it starts it over, so a
 * drag, a hold or a chooser held open never has the chrome go from under it. [hideIfIdle] is called
 * when the wait runs out, and it is for the host to say whether a video still plays and whether any of
 * its chrome is in use.
 */
class ChromeAutoHide(private val view: View, hideIfIdle: () -> Unit) {
    private val run = Runnable(hideIfIdle)

    /** Every touch anywhere on the screen, as the host dispatches it. */
    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.removeCallbacks(run)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> restart()
        }
    }

    fun restart() {
        view.removeCallbacks(run)
        view.postDelayed(run, CHROME_AUTO_HIDE_MS)
    }
}
