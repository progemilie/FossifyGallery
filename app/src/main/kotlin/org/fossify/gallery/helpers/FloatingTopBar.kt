package org.fossify.gallery.helpers

import android.graphics.Color
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.fossify.commons.extensions.onGlobalLayout
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
) {
    private val resources = topBar.resources
    private val panner = ScrollPanner(resources, ::hide, ::show)

    private var isHidden = false
    private var glass: GlassPanel? = null

    private var grid: RecyclerView? = null
    private var refreshLayout: SwipeRefreshLayout? = null
    private var gridNeedsRoom: () -> Boolean = { true }

    private val pillRadius =
        resources.getDimension(org.fossify.commons.R.dimen.material_dialog_corner_radius)

    private val pillOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, pillRadius)
        }
    }

    /** Stops the bar from panning away and brings it back down if it had. */
    var isPanningEnabled: Boolean
        get() = panner.isPanningEnabled
        set(value) {
            panner.isPanningEnabled = value
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
        pill.elevation = resources.getDimension(R.dimen.floating_chrome_elevation)

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

    /**
     * Takes the bar off its band of colour and hangs it over [grid]: it pans with that grid, the
     * grid keeps its first row clear of it by padding rather than by starting below it, and
     * [refreshLayout]'s spinner drops in from under the bar rather than from behind it.
     *
     * [gridNeedsRoom] is asked again whenever the room is measured, for a screen that sometimes has
     * something else keeping clear of the bar on the grid's behalf.
     */
    fun floatOver(
        grid: RecyclerView,
        refreshLayout: SwipeRefreshLayout,
        gridNeedsRoom: () -> Boolean = { true },
    ) {
        this.grid = grid
        this.refreshLayout = refreshLayout
        this.gridNeedsRoom = gridNeedsRoom

        makeFloating()
        panner.panWith(grid)
        onHeightChanged = ::keepGridClear
        topBar.onGlobalLayout { keepGridClear() }
    }

    /**
     * Hands the grid the room the bar takes up. Set here rather than in the layout because the
     * bar's height is the status bar inset plus its own, and only the running app knows the first.
     */
    fun keepGridClear() {
        val barHeight = occupiedHeight
        grid?.updatePadding(top = if (gridNeedsRoom()) barHeight else 0)

        val travel = resources.getDimensionPixelSize(R.dimen.refresh_spinner_travel)
        refreshLayout?.setProgressViewOffset(false, barHeight, barHeight + travel)
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
