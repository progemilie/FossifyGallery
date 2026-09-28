package org.fossify.gallery.helpers

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Two fingers pinching a grid, followed as they move. Replaces commons' `MyZoomListener`, which lets
 * the grid scroll on the same events, ignores pinches for a second after any finger lifts, and steps
 * only once per gesture - none of it configurable from here.
 *
 * The fingers' separation is followed directly rather than through `ScaleGestureDetector`, which
 * will not start below `config_minScalingSpan` - 27mm by default, a third of a phone's width.
 */
class GridPinchZoom(
    private val recyclerView: RecyclerView,
    private val listener: Listener,
) : RecyclerView.SimpleOnItemTouchListener() {

    interface Listener {
        /**
         * The fingers have moved far enough apart or together to be pinching, around [focusX],
         * [focusY] in the grid's own coordinates. False is not yet, asked again as they move on.
         */
        fun onPinchStart(focusX: Float, focusY: Float): Boolean

        /** How far apart the fingers are against where the pinch started: over 1 is spreading. */
        fun onPinch(spread: Float)

        /** A finger has lifted, or the gesture was taken away. */
        fun onPinchEnd()
    }

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

    init {
        recyclerView.addOnItemTouchListener(this)
    }

    // events arrive through onInterceptTouchEvent until a listener claims the stream and through
    // onTouchEvent afterwards, so both have to feed the tracker. The stream is claimed the moment a
    // second finger lands rather than once the fingers have moved: a pinch starting slowly would
    // otherwise sit still long enough for the tile under the first finger to take a long press
    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        track(e)
        return isPinching || (isEnabled && secondPointerId != MotionEvent.INVALID_POINTER_ID)
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

        // else the SwipeRefreshLayout around the grid takes a downwards pinch as a pull to refresh
        recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun follow(e: MotionEvent) {
        if (!readPointers(e) || baselineSpan <= 0f || span <= 0f) {
            return
        }

        if (!isPinching) {
            if (abs(span - baselineSpan) < touchSlop || !beginPinch()) {
                return
            }
        }

        listener.onPinch(span / baselineSpan)
    }

    private fun beginPinch(): Boolean {
        // a grid still catching up on a layout, as one is for a frame after a zoom lands, is asked
        // again on the next move rather than lost to the whole gesture
        if (!listener.onPinchStart(focusX, focusY)) {
            return false
        }

        // the slop taken off the baseline, or the zoom would open with a jump by it - and only the
        // slop: movement a busy main thread delivers all in one event still counts
        baselineSpan = (baselineSpan + if (span > baselineSpan) touchSlop else -touchSlop).coerceAtLeast(1f)
        isPinching = true
        return true
    }

    private fun endPinch() {
        firstPointerId = MotionEvent.INVALID_POINTER_ID
        secondPointerId = MotionEvent.INVALID_POINTER_ID
        baselineSpan = 0f
        if (isPinching) {
            isPinching = false
            listener.onPinchEnd()
        }
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

/**
 * A pinch taken one count at a time, for a grid with nothing to show between its counts - the
 * folder grid, whose covers carry names that cannot be drawn at any size but their own.
 */
class PinchSteps(
    /** One count fewer - bigger tiles. */
    private val onZoomIn: () -> Unit,
    /** One count more - smaller tiles. */
    private val onZoomOut: () -> Unit,
) : GridPinchZoom.Listener {

    private var baseline = 1f

    override fun onPinchStart(focusX: Float, focusY: Float): Boolean {
        baseline = 1f
        return true
    }

    // re-baselining at each step lets one gesture walk any distance up or down the counts, and
    // turn around, without ever crossing two on one small movement
    override fun onPinch(spread: Float) {
        when {
            spread / baseline >= STEP_SPREAD -> {
                baseline = spread
                onZoomIn()
            }

            baseline / spread >= STEP_SPREAD -> {
                baseline = spread
                onZoomOut()
            }
        }
    }

    override fun onPinchEnd() = Unit

    private companion object {
        /** How much the fingers' separation has to change for the next count. */
        const val STEP_SPREAD = 1.5f
    }
}
