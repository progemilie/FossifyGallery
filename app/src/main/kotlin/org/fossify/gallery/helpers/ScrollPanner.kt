package org.fossify.gallery.helpers

import android.content.res.Resources
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.R

/**
 * Pans a piece of chrome floating over a grid out of the way while that grid is being dragged
 * along, and brings it back the moment the drag turns around or settles.
 *
 * Shared by the search pill at the top of a browsing screen ([FloatingTopBar]) and the navigation
 * pill at the bottom of it: the two have to agree about what counts as scrolling away, or they
 * would leave and return at different moments over the one gesture.
 */
class ScrollPanner(
    resources: Resources,
    private val onHide: () -> Unit,
    private val onShow: () -> Unit,
) : RecyclerView.OnScrollListener() {
    private val hideThreshold = resources.getDimensionPixelSize(R.dimen.chrome_hide_threshold)
    private val showThreshold = resources.getDimensionPixelSize(R.dimen.chrome_show_threshold)

    // how far the grid has been dragged the same way in a row, reset whenever it turns around
    private var travelledSinceTurn = 0

    private var grid: RecyclerView? = null

    /**
     * Stops the chrome from panning away and brings it back if it had. The grid is not the only
     * thing that can be going on - a search or a selection puts the chrome to work.
     */
    var isPanningEnabled = true
        set(value) {
            field = value
            if (!value) {
                onShow()
            }
        }

    /**
     * Starts panning with [grid], letting go of whichever one it was on before. A screen that swaps
     * one pane of content for another leaves both grids on screen for the length of the swap, and
     * chrome still listening to the one that left would pan away with it.
     */
    fun panWith(grid: RecyclerView) {
        this.grid?.removeOnScrollListener(this)
        this.grid = grid
        travelledSinceTurn = 0
        grid.removeOnScrollListener(this)
        grid.addOnScrollListener(this)
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        if (!isPanningEnabled) {
            return
        }

        // the top of the list is where the chrome belongs, whatever the last gesture was
        if (!recyclerView.canScrollVertically(-1)) {
            travelledSinceTurn = 0
            onShow()
            return
        }

        if (dy == 0) {
            return
        }

        if ((dy > 0) != (travelledSinceTurn > 0)) {
            travelledSinceTurn = 0
        }

        travelledSinceTurn += dy
        if (travelledSinceTurn > hideThreshold) {
            onHide()
        } else if (travelledSinceTurn < -showThreshold) {
            onShow()
        }
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            travelledSinceTurn = 0
            onShow()
        }
    }
}
