package org.fossify.gallery.helpers

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pinching a grid to change how many columns it draws.
 *
 * Commons' `MyRecyclerView` answers a pinch of its own, but not usefully: it hands the gesture to a
 * `ScaleGestureDetector` while letting the grid scroll on the same events, ignores every pinch for a
 * whole second after any finger leaves the grid, and then steps once per gesture behind a 40%
 * spread. Between the three a pinch mostly scrolls the grid instead of zooming it, and none of it is
 * reachable from here - so the grids leave that listener unset and use this instead.
 *
 * The two fingers' separation is followed directly rather than through `ScaleGestureDetector`, which
 * will not begin a gesture until they are further apart than `config_minScalingSpan` - 27mm by
 * default, better than a third of the width of a phone.
 *
 * Steps one count at a time, from a baseline reset at each step: a gesture can walk as far up or
 * down the counts as it likes without lifting, and can turn around and come back, but nothing short
 * of a whole [STEP_RATIO] of finger movement will carry it past a count. Once a pinch is recognised
 * the grid holds still under it, which is the whole point of claiming the touch stream -
 * [RecyclerView] cancels its own scroll as soon as an item touch listener does.
 */
class GridPinchZoom(
    private val recyclerView: RecyclerView,
    /** One count fewer - bigger tiles. */
    private val onZoomIn: () -> Unit,
    /** One count more - smaller tiles. */
    private val onZoomOut: () -> Unit,
    /** The middle of the two fingers, for a grid that keeps whatever is under them in place. */
    private val onPinchStart: (focusX: Float, focusY: Float) -> Unit = { _, _ -> },
) : RecyclerView.SimpleOnItemTouchListener() {

    var isEnabled = true
        set(value) {
            field = value
            if (!value) {
                endPinch()
            }
        }

    private val touchSlop = ViewConfiguration.get(recyclerView.context).scaledTouchSlop
    private var isPinching = false
    private var firstPointerId = MotionEvent.INVALID_POINTER_ID
    private var secondPointerId = MotionEvent.INVALID_POINTER_ID
    private var span = 0f
    private var focusX = 0f
    private var focusY = 0f
    private var baselineSpan = 0f

    private companion object {
        /**
         * How much the fingers' separation has to change to be asking for the next count. Also
         * `GridZoom`'s spacing between the zoomed-out counts, so up there a tile grows at very
         * nearly the rate the fingers do.
         */
        const val STEP_RATIO = 1.4f
    }

    init {
        recyclerView.addOnItemTouchListener(this)
    }

    // both of these: RecyclerView asks its item touch listeners from onInterceptTouchEvent until one
    // claims the stream and from onTouchEvent afterwards, and which of the two an event arrives
    // through also depends on whether a child took the press that started it
    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        track(e)
        return isPinching
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        track(e)
    }

    private fun track(e: MotionEvent) {
        if (!isEnabled) {
            return
        }

        when (e.actionMasked) {
            // a third finger joining changes nothing - the gesture keeps to the two it began with
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2) {
                beginTracking(e)
            }

            MotionEvent.ACTION_MOVE -> follow(e)

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = e.getPointerId(e.actionIndex)
                if (pointerId == firstPointerId || pointerId == secondPointerId) {
                    endPinch()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endPinch()
        }
    }

    private fun beginTracking(e: MotionEvent) {
        firstPointerId = e.getPointerId(0)
        secondPointerId = e.getPointerId(1)
        if (readPointers(e)) {
            baselineSpan = span
        }
    }

    private fun follow(e: MotionEvent) {
        if (!readPointers(e) || baselineSpan <= 0f || span <= 0f) {
            return
        }

        if (!isPinching) {
            if (abs(span - baselineSpan) < touchSlop) {
                return
            }

            beginPinch()
        }

        // the baseline moves with each step, so a gesture can keep going - or turn around - but
        // never crosses two counts on one small movement
        when {
            span / baselineSpan >= STEP_RATIO -> {
                baselineSpan = span
                onZoomIn()
            }

            baselineSpan / span >= STEP_RATIO -> {
                baselineSpan = span
                onZoomOut()
            }
        }
    }

    private fun beginPinch() {
        // measured from here rather than from where the fingers landed, so the first step does not
        // come early by whatever the gesture spent getting past the slop
        baselineSpan = span
        isPinching = true
        // the grid sits inside a SwipeRefreshLayout, which would otherwise take a pinch downwards at
        // the top of the list as a pull to refresh
        recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
        onPinchStart(focusX, focusY)
    }

    private fun endPinch() {
        firstPointerId = MotionEvent.INVALID_POINTER_ID
        secondPointerId = MotionEvent.INVALID_POINTER_ID
        baselineSpan = 0f
        isPinching = false
    }

    /** The two fingers' separation and middle, or false once either of them has gone. */
    private fun readPointers(e: MotionEvent): Boolean {
        val firstIndex = e.findPointerIndex(firstPointerId)
        val secondIndex = e.findPointerIndex(secondPointerId)
        if (firstIndex < 0 || secondIndex < 0) {
            return false
        }

        val dx = e.getX(firstIndex) - e.getX(secondIndex)
        val dy = e.getY(firstIndex) - e.getY(secondIndex)
        span = sqrt(dx * dx + dy * dy)
        focusX = (e.getX(firstIndex) + e.getX(secondIndex)) / 2
        focusY = (e.getY(firstIndex) + e.getY(secondIndex)) / 2
        return true
    }
}
