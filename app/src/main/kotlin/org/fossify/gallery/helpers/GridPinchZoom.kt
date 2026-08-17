package org.fossify.gallery.helpers

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pinching a grid to change how many columns it draws. Replaces commons' `MyZoomListener`, which
 * lets the grid scroll on the same events, ignores pinches for a second after any finger lifts, and
 * steps only once per gesture - none of it configurable from here.
 *
 * The fingers' separation is followed directly rather than through `ScaleGestureDetector`, which
 * will not start below `config_minScalingSpan` - 27mm by default, a third of a phone's width.
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
         * How much the fingers' separation has to change to ask for the next count. Matches
         * `GridZoom`'s spacing between the zoomed-out counts, so a tile grows at the fingers' rate.
         */
        const val STEP_RATIO = 1.4f
    }

    init {
        recyclerView.addOnItemTouchListener(this)
    }

    // events arrive through onInterceptTouchEvent until a listener claims the stream and through
    // onTouchEvent afterwards, so both have to feed the tracker
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

        // re-baselining at each step lets one gesture walk any distance up or down the counts, and
        // turn around, without ever crossing two on one small movement
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
        // re-baselined here, or the first step comes early by whatever the slop cost
        baselineSpan = span
        isPinching = true
        // else the SwipeRefreshLayout around the grid takes a downwards pinch as a pull to refresh
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
