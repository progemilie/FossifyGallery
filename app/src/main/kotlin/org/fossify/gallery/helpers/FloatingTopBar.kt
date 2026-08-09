package org.fossify.gallery.helpers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.BlurViewFacade
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.helpers.isSPlus
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R

private const val ANIMATION_DURATION = 200L
private const val PILL_TINT_ALPHA = 0.5f
private const val PILL_BLUR_RADIUS = 20f

// what the pill falls back to where there is no cheap blur to be had (below Android 12)
private const val PILL_OPAQUE_ALPHA = 0.97f

// how far the pill's colour is carried off the theme's background towards white, at its darkest
// and at its lightest. A dark theme gets the most of it: a near black pill over a photo reads as a
// hole punched in the picture rather than as glass laid over it. A light one has barely anywhere
// to go and wants no more than a hint, or the pill turns into a white slab.
private const val PILL_MAX_LIFT = 0.20f
private const val PILL_MIN_LIFT = 0.05f

// Rec. 601 luma weights: perceived brightness, in which a mid grey lands near the middle. The
// relative luminance androidx works in linearises first, which would read that same grey as dark
// and hand it most of the lift.
private const val LUMA_RED = 0.299f
private const val LUMA_GREEN = 0.587f
private const val LUMA_BLUE = 0.114f
private const val FULL_CHANNEL = 255f

/**
 * The pill's own colour: the theme's background carried towards white by an amount that falls away
 * as that background gets lighter, so the bar reads as glass laid over the app rather than as a
 * patch of the same paint it is sitting on.
 */
private fun pillTint(background: Int): Int {
    val brightness = (
        LUMA_RED * Color.red(background) +
            LUMA_GREEN * Color.green(background) +
            LUMA_BLUE * Color.blue(background)
        ) / FULL_CHANNEL

    val darkness = 1f - brightness
    val lift = PILL_MIN_LIFT + (PILL_MAX_LIFT - PILL_MIN_LIFT) * darkness * darkness
    return ColorUtils.blendARGB(background, Color.WHITE, lift)
}

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
    private var blurBackdrop: BlurViewFacade? = null

    private val pillOutline = object : ViewOutlineProvider() {
        private val radius =
            resources.getDimension(org.fossify.commons.R.dimen.material_dialog_corner_radius)

        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
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
        if (isSPlus()) {
            pill.background = null
            addBlurBackdrop(pill)
        } else {
            // no hardware blur below Android 12
            pill.background?.applyColorFilter(
                pillTint(pill.context.getProperBackgroundColor()).adjustAlpha(PILL_OPAQUE_ALPHA)
            )
        }
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

    private fun addBlurBackdrop(pill: ViewGroup) {
        val backdrop = blurBackdrop ?: createBlurBackdrop(pill).also { blurBackdrop = it }
        val base = pill.context.getProperBackgroundColor()

        backdrop
            .setBlurRadius(PILL_BLUR_RADIUS)
            // the grid paints nothing of its own between the thumbnails, so a capture that starts
            // from a cleared buffer comes back transparent everywhere there is no photo - over a
            // date header, over the gaps between cells, over the half of the pill hanging past the
            // end of a row. Blurring transparency leaves transparency, and the real content shows
            // straight through it, unblurred and sharp. Starting each capture from the background
            // the grid sits on is what makes the pill frost everything under it and not just the
            // photos.
            .setFrameClearDrawable(SolidColorDrawable(base))
            // the frame clear above stands in for the grid's own background and stays the real
            // colour, so the lift rides on the tint instead - the pill parts company with the app
            // behind it without the frost parting company with the grid it is a copy of
            .setOverlayColor(pillTint(base).adjustAlpha(PILL_TINT_ALPHA))
    }

    private fun createBlurBackdrop(pill: ViewGroup): BlurViewFacade {
        val view = BlurView(pill.context).apply {
            outlineProvider = pillOutline
            clipToOutline = true
        }

        pill.addView(
            view, 0,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        return view.setupWith(contentBehind)
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
        blurBackdrop?.setBlurAutoUpdate(true)
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
            .withEndAction { if (isHidden) blurBackdrop?.setBlurAutoUpdate(false) }
            .start()
    }

    val occupiedHeight: Int
        get() = topBar.height
}

/**
 * Floods whatever canvas it is handed, whatever bounds it was set. BlurView hands its capture
 * buffer over untransformed and without ever setting bounds on this, which a plain ColorDrawable
 * would answer by painting nothing at all.
 */
private class SolidColorDrawable(private val color: Int) : Drawable() {
    override fun draw(canvas: Canvas) = canvas.drawColor(color)

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("abstract on Drawable, so it has to be answered whatever its own docs say")
    override fun getOpacity() = PixelFormat.OPAQUE
}
