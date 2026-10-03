package org.fossify.gallery.views

import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import org.fossify.gallery.R

/** How long a skip hint stays up after the last tap, and its fade. */
private const val SEEK_HINT_MS = 700L
private const val SEEK_HINT_FADE_MS = 150L
private const val SEEK_HINT_POP = 0.85f

private const val CHROME_AUTO_HIDE_MS = 5000L

/** A run of double tap skips added up on its side: "- 10s", then "- 20s". */
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
 * Hides the chrome over a playing video once the screen goes untouched for a while. A finger down
 * stops the wait and lifting it starts it over; [hideIfIdle] decides whether to hide.
 */
class ChromeAutoHide(private val view: View, hideIfIdle: () -> Unit) {
    private val run = Runnable(hideIfIdle)

    /** Fed every touch on the screen. */
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
