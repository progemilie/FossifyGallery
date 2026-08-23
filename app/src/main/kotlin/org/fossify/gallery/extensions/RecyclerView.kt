package org.fossify.gallery.extensions

import android.util.DisplayMetrics
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

/**
 * How far down the grid the animated part of the trip starts. Anything above this is jumped over
 * first: a smooth scroll from a few thousand items down would either crawl or tear past every one
 * of them, and nobody is looking at the rows in between.
 */
private const val SMOOTH_SCROLL_ROWS = 8

/** How long a scrolled inch takes. RecyclerView's own 25f reads as a jump rather than a trip. */
private const val MS_PER_INCH = 45f

/**
 * Back to the top of the grid, over the last few rows. A finger landing on the grid stops it -
 * RecyclerView drops a smooth scroll the moment a touch turns into a drag - so the trip can be
 * taken over rather than waited out.
 */
fun RecyclerView.smoothScrollToTop() {
    val manager = layoutManager as? GridLayoutManager ?: run {
        scrollToPosition(0)
        return
    }

    // whatever fling is still in flight would otherwise carry on underneath this
    stopScroll()
    val runUp = manager.spanCount * SMOOTH_SCROLL_ROWS
    if (manager.findFirstVisibleItemPosition() > runUp) {
        scrollToPosition(runUp)
    }

    val scroller = object : LinearSmoothScroller(context) {
        override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics) =
            MS_PER_INCH / displayMetrics.densityDpi
    }

    scroller.targetPosition = 0
    manager.startSmoothScroll(scroller)
}
