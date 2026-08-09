package org.fossify.gallery.views

import android.widget.ScrollView

/**
 * Scrolls a [ScrollView] on while a finger is held near its top or bottom edge, for lists that are
 * driven by a drag starting somewhere else and so can never be flung by the finger itself.
 *
 * Splitting this off [FolderChooser] is what keeps that class under detekt's function-count
 * threshold - check `./gradlew detekt` before folding the two back together.
 */
class EdgeAutoScroller(
    private val scroller: ScrollView,
    private val edgeZone: Int,
    private val maxSpeed: Float,
    private val onScrolled: () -> Unit,
) {
    private var direction = 0
    private var lastFrameTime = 0L
    private var touchY = 0f

    private val step = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            val elapsed = (now - lastFrameTime).coerceAtMost(MAX_FRAME_NANOS) / NANOS_PER_SECOND
            lastFrameTime = now

            // proportional to how deep into the edge zone the finger is, so the very edge races and
            // the near side of the zone creeps
            scroller.scrollBy(0, (direction * depthIntoEdgeZone() * maxSpeed * elapsed).toInt())
            onScrolled()

            if (hasFurtherToGo()) {
                scroller.postOnAnimation(this)
            } else {
                direction = 0
            }
        }
    }

    /** Starts, redirects or stops the scroll to suit a finger now at [rawY]. */
    fun update(rawY: Float, isActive: Boolean) {
        touchY = rawY
        val viewportTop = viewportTop()
        val wanted = when {
            !isActive -> 0
            rawY < viewportTop + edgeZone -> -1
            rawY > viewportTop + scroller.height - edgeZone -> 1
            else -> 0
        }

        if (wanted == direction) {
            return
        }

        scroller.removeCallbacks(step)
        direction = wanted
        if (hasFurtherToGo()) {
            lastFrameTime = System.nanoTime()
            scroller.postOnAnimation(step)
        } else {
            direction = 0
        }
    }

    fun stop() {
        direction = 0
        scroller.removeCallbacks(step)
    }

    fun canScrollUp() = scroller.scrollY > 0

    fun canScrollDown() = scroller.scrollY < contentHeight() - scroller.height

    private fun hasFurtherToGo() = when {
        direction < 0 -> canScrollUp()
        direction > 0 -> canScrollDown()
        else -> false
    }

    private fun depthIntoEdgeZone(): Float {
        val viewportTop = viewportTop()
        val past = if (direction < 0) {
            viewportTop + edgeZone - touchY
        } else {
            touchY - (viewportTop + scroller.height - edgeZone)
        }

        return (past / edgeZone).coerceIn(MIN_SPEED_FRACTION, 1f)
    }

    private fun contentHeight() = scroller.getChildAt(0)?.height ?: 0

    private fun viewportTop(): Float {
        val location = IntArray(2)
        scroller.getLocationOnScreen(location)
        return location[1].toFloat()
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f

        /** Keeps the list moving even right at the inner boundary of the edge zone. */
        const val MIN_SPEED_FRACTION = 0.15f

        /** Caps the step a dropped frame can produce, so a stutter cannot fling the list. */
        const val MAX_FRAME_NANOS = 50_000_000L
    }
}
