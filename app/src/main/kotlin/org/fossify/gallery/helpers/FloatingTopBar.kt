package org.fossify.gallery.helpers

import android.graphics.Color
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.views.GlassPanel

private const val ANIMATION_DURATION = 200L

/**
 * Lifts a screen's search bar off its band of colour so the grid runs underneath it, and pans it
 * out of the way while that grid is actively being dragged down.
 *
 * Commons paints the bar again on every resume ([MySearchMenu.updateColors]), so [makeFloating]
 * has to run after each of those calls rather than once at startup.
 */
class FloatingTopBar(
    private val topBar: MySearchMenu,
    private val contentBehind: ViewGroup,
) : RecyclerView.OnScrollListener() {
    private val resources = topBar.resources
    private val hideThreshold = resources.getDimensionPixelSize(R.dimen.top_bar_hide_threshold)
    private val showThreshold = resources.getDimensionPixelSize(R.dimen.top_bar_show_threshold)

    // how far the grid has been dragged the same way in a row, reset whenever it turns around
    private var travelledSinceTurn = 0
    private var isHidden = false
    private var glass: GlassPanel? = null

    private val pillRadius =
        resources.getDimension(org.fossify.commons.R.dimen.material_dialog_corner_radius)

    private val pillOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, pillRadius)
        }
    }

    /**
     * Stops the bar from panning away and brings it back down if it had. The grid is not the only
     * thing that can be going on - a search or a selection puts the bar to work
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
        topBar.stateListAnimator = null
        topBar.elevation = 0f

        val pill = topBar.binding.toolbarContainer
        castPillShadow(pill)

        // commons leaves the search pill at a quarter of the accent colour, which was legible over
        // the band of background colour that used to be behind it and is not over a photo
        pill.background = null
        glassify(pill)
    }

    /**
     * A shadow is drawn *outside* the view casting it, so every parent between the pill and the
     * `CoordinatorLayout` has to stop clipping to its own bounds first - there is only a couple of
     * dp of room under the pill inside the app bar, and without this the shadow comes back cropped
     * square along the bottom. Nothing else lives in those parents by now for the unclipping to let
     * spill.
     */
    private fun castPillShadow(pill: ViewGroup) {
        pill.outlineProvider = pillOutline
        pill.elevation = resources.getDimension(R.dimen.search_pill_elevation)

        var parent = pill.parent
        while (parent is ViewGroup) {
            parent.clipChildren = false
            parent.clipToPadding = false
            if (parent === topBar.parent) {
                return
            }

            parent = parent.parent
        }
    }

    /** Fills the pill with a pane of glass, laid behind whatever commons put in it. */
    private fun glassify(pill: ViewGroup) {
        if (glass == null) {
            glass = GlassPanel(pill.context).apply {
                cornerRadius = pillRadius
                blurRadius = Glass.SEARCH_PILL_RADIUS
                pill.addView(
                    this, 0,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
            }
        }

        // re-frosted rather than merely recoloured: the pill never leaves the screen for the panel to
        // notice a change on its way back, and this runs on every resume, settings screen included
        glass?.frost(contentBehind)
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
        } else if (travelledSinceTurn < -showThreshold) {
            show()
        }
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            travelledSinceTurn = 0
            show()
        }
    }

    fun show() {
        if (!isHidden && topBar.translationY == 0f) {
            return
        }

        isHidden = false
        glass?.isFrostPaused = false
        topBar.animate().translationY(0f).setDuration(ANIMATION_DURATION).start()
    }

    private fun hide() {
        if (isHidden) {
            return
        }

        isHidden = true
        topBar.animate()
            .translationY(-topBar.height.toFloat())
            .setDuration(ANIMATION_DURATION)
            // the bar stays VISIBLE while panned away, so the glass has to be told to stop copying
            .withEndAction { if (isHidden) glass?.isFrostPaused = true }
            .start()
    }

    val occupiedHeight: Int
        get() = topBar.height
}
