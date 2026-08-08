package org.fossify.gallery.helpers

import android.graphics.Color
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R

private const val ANIMATION_DURATION = 200L

// solid enough to read the hint and icons against, sheer enough to tell there is a photo behind it
private const val PILL_ALPHA = 0.92f

/**
 * Lifts a screen's search bar off its band of colour so the grid runs underneath it, and pans it
 * out of the way while that grid is being scrolled down - dragging back up a little brings it
 * straight back, the way a browser's address bar behaves.
 *
 * Commons paints the bar again on every resume ([MySearchMenu.updateColors]), so [makeFloating]
 * has to run after each of those calls rather than once at startup.
 */
class FloatingTopBar(private val topBar: MySearchMenu) : RecyclerView.OnScrollListener() {
    private val hideThreshold = topBar.resources.getDimensionPixelSize(R.dimen.top_bar_hide_threshold)

    // how far the grid has been dragged the same way in a row, reset whenever it turns around, so
    // that a wobble in the middle of a fling does not flip the bar back and forth
    private var travelledSinceTurn = 0
    private var isHidden = false

    /**
     * Stops the bar from panning away and brings it back down if it had. The grid is not the only
     * thing that can be going on - a search or a selection puts the bar to work, and it is no use
     * up off the screen then.
     */
    var isPanningEnabled = true
        set(value) {
            field = value
            if (!value) {
                show()
            }
        }

    /**
     * Called whenever the bar ends up a different height, for whatever has to keep clear of it.
     * A rotation or a change to the system bar insets moves it, so this cannot be worked out once.
     */
    var onHeightChanged: (() -> Unit)? = null

    init {
        topBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                onHeightChanged?.invoke()
            }
        }
    }

    fun makeFloating() {
        topBar.setBackgroundColor(Color.TRANSPARENT)
        topBar.requireToolbar().setBackgroundColor(Color.TRANSPARENT)
        // an app bar draws a shadow the width of the screen off its elevation, which over a photo
        // reads as a grey smear rather than as depth
        topBar.stateListAnimator = null
        topBar.elevation = 0f

        // commons leaves the search pill at a quarter of the accent colour, which was legible over
        // the band of background colour that used to be behind it and is not over a photo. Give the
        // pill itself the surface the bar has given up, and its own shadow to lift it off the grid.
        topBar.binding.toolbarContainer.apply {
            background?.applyColorFilter(context.getProperBackgroundColor().adjustAlpha(PILL_ALPHA))
            elevation = resources.getDimension(R.dimen.floating_top_bar_elevation)
        }
    }

    fun attachTo(recyclerView: RecyclerView) {
        recyclerView.removeOnScrollListener(this)
        recyclerView.addOnScrollListener(this)
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        if (!isPanningEnabled) {
            return
        }

        // the top of the list is where the bar belongs, whatever the last gesture was
        if (!recyclerView.canScrollVertically(-1)) {
            travelledSinceTurn = 0
            show()
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
            hide()
        } else if (travelledSinceTurn < -hideThreshold) {
            show()
        }
    }

    fun show() {
        if (!isHidden && topBar.translationY == 0f) {
            return
        }

        isHidden = false
        topBar.animate().translationY(0f).setDuration(ANIMATION_DURATION).start()
    }

    private fun hide() {
        if (isHidden) {
            return
        }

        isHidden = true
        // its height covers the status bar padding as well, so this clears the screen entirely
        topBar.animate().translationY(-topBar.height.toFloat()).setDuration(ANIMATION_DURATION).start()
    }

    /**
     * How far down the screen the bar reaches, for anything that has to keep clear of it - a pull
     * to refresh spinner, say. Only known once the bar has been measured.
     */
    val occupiedHeight: Int
        get() = topBar.height
}
