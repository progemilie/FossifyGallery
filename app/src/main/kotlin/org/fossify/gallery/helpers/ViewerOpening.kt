package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.fossify.gallery.extensions.config
import kotlin.math.abs

/** How long after a tap opens a viewer the grid's window can still be sent gestures meant for it. */
private const val OPENING_TOUCH_WINDOW_MS = 1000L

/** A quick flick down, recognised from a gesture's events. */
class DownFlick(context: Context) {
    private val minDistance = OPENING_FLICK_DP * context.resources.displayMetrics.density
    private var down: PointF? = null
    private var downAt = 0L

    /** Feeds [event] in, answering true on the ACTION_UP that ends a flick down. */
    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = PointF(event.rawX, event.rawY)
                downAt = event.eventTime
            }

            // two fingers are a pinch, never a flick
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> down = null
            MotionEvent.ACTION_UP -> {
                val start = down ?: return false
                down = null
                val travelled = event.rawY - start.y
                return travelled > minDistance && travelled > abs(event.rawX - start.x) &&
                    event.eventTime - downAt < MAX_CLOSE_DOWN_GESTURE_DURATION
            }
        }

        return false
    }

    fun forget() {
        down = null
    }
}

/**
 * A photo flicked away while it is still opening, which is when someone who opened the wrong one
 * flicks it. For the first few hundred milliseconds of the flight the viewer's window takes no
 * touches at all, and a gesture made then is sent to the grid's window under the photo
 * ([watchGrid]); after that it reaches a viewer with nothing built yet to take it ([watchViewer]).
 * Either way a flick down closes the viewer, or has it close the moment it is up.
 */
object ViewerOpening {
    private var openedAt = 0L
    private var closeAsked = false
    private var isTakingGesture = false
    private var flick: DownFlick? = null
    private var isWatchingViewerGesture = false
    private var viewerFlick: DownFlick? = null

    /** How the viewer opening closes itself, from the moment it has one. */
    private var closer: (() -> Unit)? = null

    /** A tile has just opened a viewer that flies. */
    fun began() {
        openedAt = SystemClock.uptimeMillis()
        closeAsked = false
        // the last viewer is already closing, but only destroyed once the grid has gone idle - a
        // flick before the new one is up would be sent to it and lost
        closer = null
    }

    /** The viewer has taken the screen over, or gone again: new gestures are the grid's own. */
    fun ended() {
        openedAt = 0L
        closeAsked = false
    }

    /**
     * Has a flick made on the grid close [activity], the viewer opening over it - see [TileFlight].
     * Held until the viewer is destroyed or the next one opens, rather than until it has landed: a
     * gesture that began on the grid stays the grid's to its end, however soon after its start the
     * viewer is up.
     */
    fun closeOnFlick(activity: Activity) {
        val close = { activity.finish() }
        closer = close
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (closer === close) {
                    closer = null
                }
            }
        })
    }

    /** Whether a flick made on the grid asked the viewer to close before it was there to be told. */
    fun takeCloseAsked() = closeAsked.also { closeAsked = false }

    /**
     * For the window of a grid that opens viewers, ahead of its own dispatch: whether [event] was
     * meant for a viewer opening over it, which the grid is then not to see. Decided per gesture, at
     * its ACTION_DOWN, so a gesture is never split between the two.
     */
    fun watchGrid(context: Context, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            isTakingGesture = openedAt != 0L && SystemClock.uptimeMillis() - openedAt < OPENING_TOUCH_WINDOW_MS
            if (!isTakingGesture) {
                flick?.forget()
            }
        }

        if (!isTakingGesture) {
            return false
        }

        val flick = flick ?: DownFlick(context.applicationContext).also { flick = it }
        if (flick.onTouch(event) && context.config.allowDownGesture) {
            closer?.invoke() ?: run { closeAsked = true }
        }

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            isTakingGesture = false
        }

        return true
    }

    /**
     * For the viewer's own window, ahead of its dispatch: a flick down made while [flight] is still
     * opening closes the viewer, turning the flight round from wherever it has got to. Nothing under
     * the flight can take one - the pager is only built once the flight lands, and a photo still
     * loading turns touches down - so a flick made the moment the wrong photo opened used to be lost.
     *
     * Only watches: every event still goes on to the screen, which by the end of a gesture begun
     * during the flight may well be up. Should the media answer the flick too, the second close finds
     * the first under way.
     */
    fun watchViewer(context: Context, event: MotionEvent, flight: TileFlight) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            isWatchingViewerGesture = flight.isOpening
        }

        if (!isWatchingViewerGesture) {
            return
        }

        val flick = viewerFlick ?: DownFlick(context.applicationContext).also { viewerFlick = it }
        if (flick.onTouch(event) && context.config.allowDownGesture) {
            closer?.invoke()
        }
    }
}
