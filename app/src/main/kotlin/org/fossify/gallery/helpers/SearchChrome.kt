package org.fossify.gallery.helpers

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.core.animation.doOnEnd
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.isSPlus
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.interfaces.GridPane
import org.fossify.gallery.models.SearchFilter
import org.fossify.gallery.views.FilterChip
import org.fossify.gallery.views.SearchOverlay

private const val DIM_IN_MS = 300L
private const val OPTIONS_IN_MS = 380L
private const val EDGE_IN_MS = 520L
private const val OUT_MS = 180L

/** How much of the edge's entrance it takes to come up to full strength; the light keeps rising after. */
private const val EDGE_FADE_SHARE = 0.35f

/** Below this a blur is not worth the offscreen pass it costs. */
private const val MIN_BLUR_PX = 0.5f

/**
 * The search's own chrome over commons' bar. While a search is open with nothing typed, the grid dims
 * and blurs behind the options it can be narrowed by, and the bar's pill is edged; a pill picked
 * becomes the bar's chip. Opening is one entrance - the dim on a curve, the edge's light rising from
 * the bottom of the pill, the options settling up into place - and closing fades the lot at once.
 *
 * Typing hands the grid back for its results: the dim and the options fade, the edge stays for as
 * long as the search is open, and clearing the text brings them back.
 */
class SearchChrome(
    private val topBar: MySearchMenu,
    private val contentBehind: ViewGroup,
) {
    private val context = topBar.context
    private val resources = context.resources
    private val overlay = SearchOverlay(context)
    private val chip = FilterChip(topBar)
    private val edge = SearchEdge(topBar)

    private val dimCurve = context.curve(R.interpolator.search_dim)
    private val settle = context.curve(R.interpolator.search_settle)
    private val leave = context.curve(R.interpolator.search_leave)
    private val rise = resources.getDimension(R.dimen.search_options_rise)
    private val blurRadius = resources.getDimension(R.dimen.search_blur_radius)
    private val barGap = resources.getDimensionPixelSize(R.dimen.search_options_top_gap)
    // what keeps Back with nothing typed for closing the search, where the platform can be asked
    private val syncUntouchedBack: () -> Unit =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) UntouchedSearchBack(topBar)::sync else ({})

    private var pane: GridPane? = null
    private var dimming: ValueAnimator? = null
    private var canBlur = false

    // a pane's options come back off the main thread, and only the latest asking is still wanted
    private var optionsAsked = 0

    /** A pill picked, or the chip's cross for null. */
    var onFilterChosen: ((SearchFilter?) -> Unit)? = null

    init {
        // just above the content: under the bar, which is neither dimmed nor frosting the dim
        val screen = contentBehind.parent as ViewGroup
        screen.addView(
            overlay,
            screen.indexOfChild(contentBehind) + 1,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        overlay.beGone()
        overlay.onChosen = { filter ->
            // the filter already on, picked again, is taken off
            onFilterChosen?.invoke(filter.takeUnless { it == pane?.activeFilter })
        }

        chip.onClear = { onFilterChosen?.invoke(null) }
    }

    /** Points the chip and the options at [pane]. */
    fun bind(pane: GridPane) {
        this.pane = pane
        refreshChip()
    }

    /** Puts the chip in line with the filter the pane is narrowed by now. */
    fun refreshChip() {
        chip.show(pane?.activeFilter)
    }

    fun updateColors() {
        edge.updateColors()
        chip.updateColors()
    }

    fun onSearchOpened() {
        syncUntouchedBack()
        edge.light(on = true)
        showOptions()
    }

    fun onSearchClosed() {
        optionsAsked++
        syncUntouchedBack()
        edge.light(on = false)
        fadeOptions()
    }

    fun onSearchTextChanged(text: String) {
        syncUntouchedBack()
        if (!topBar.isSearchOpen) {
            return
        }

        if (text.isEmpty()) {
            showOptions()
        } else {
            optionsAsked++
            fadeOptions()
        }
    }

    private fun showOptions() {
        val pane = pane?.takeIf { it.offersSearchOptions } ?: return
        canBlur = isSPlus() && Glass.isEnabled(context)
        overlay.keepClearOfBar(topBar.height + barGap)

        // whatever a fade on its way out had left of the dim is where it comes back from
        dimming?.cancel()
        val from = overlay.dimLevel * overlay.alpha
        overlay.alpha = 1f
        overlay.dimLevel = from
        overlay.beVisible()
        overlay.options.animate().cancel()
        overlay.options.alpha = 0f
        animateDim(from, 1f, DIM_IN_MS, dimCurve) { level ->
            overlay.dimLevel = level
            blur(level)
        }

        val asked = ++optionsAsked
        pane.loadSearchOptions { options ->
            if (asked == optionsAsked && topBar.isSearchOpen) {
                overlay.fill(options, pane.activeFilter)
                // they rise the last little way into place as they fade in
                overlay.options.translationY = rise
                overlay.options.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(OPTIONS_IN_MS)
                    .setInterpolator(settle)
                    .start()
            }
        }
    }

    /** Fades the dim, the blur and the options away as one. */
    private fun fadeOptions() {
        if (!overlay.isShown) {
            return
        }

        dimming?.cancel()
        overlay.options.animate().cancel()
        val dimLevel = overlay.dimLevel
        animateDim(overlay.alpha, 0f, OUT_MS, leave) { level ->
            overlay.alpha = level
            blur(level * dimLevel)
        }.doOnEnd {
            if (overlay.alpha == 0f) {
                overlay.beGone()
                overlay.dimLevel = 0f
                blur(0f)
            }
        }
    }

    private fun animateDim(from: Float, to: Float, duration: Long, curve: Interpolator, apply: (Float) -> Unit) =
        ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration
            interpolator = curve
            addUpdateListener { apply(it.animatedValue as Float) }
            dimming = this
            start()
        }

    private fun blur(level: Float) {
        if (!canBlur || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }

        val radius = level * blurRadius
        contentBehind.setRenderEffect(
            if (radius < MIN_BLUR_PX) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        )
    }
}

/**
 * The search pill's edge while a search is open, the [LitEdge] the reorder mode's Save wears. In, it
 * comes up with its light along the bottom of the pill and the light rises to rest along the top; out,
 * it fades with everything else.
 */
private class SearchEdge(topBar: MySearchMenu) {
    private val context = topBar.context
    private val edge = LitEdgeDrawable(
        context,
        context.resources.getDimension(org.fossify.commons.R.dimen.material_dialog_corner_radius)
    ).apply { strength = 0f }

    private val settle = context.curve(R.interpolator.search_settle)
    private val leave = context.curve(R.interpolator.search_leave)
    private var running: ValueAnimator? = null

    init {
        topBar.binding.toolbarContainer.foreground = edge
    }

    fun light(on: Boolean) {
        running?.cancel()
        val from = edge.strength
        running = ValueAnimator.ofFloat(0f, 1f).apply {
            // shaped below, the strength and the light each on a curve of their own
            interpolator = LinearInterpolator()
            if (on) {
                duration = EDGE_IN_MS
                addUpdateListener {
                    val elapsed = it.animatedFraction
                    edge.strength = (from + elapsed / EDGE_FADE_SHARE).coerceAtMost(1f)
                    edge.lightAt = 1f - settle.getInterpolation(elapsed)
                }
            } else {
                duration = OUT_MS
                addUpdateListener { edge.strength = from * (1f - leave.getInterpolation(it.animatedFraction)) }
            }

            start()
        }
    }

    fun updateColors() {
        edge.color = context.getProperTextColor()
    }
}

/**
 * Back while a search is open and nothing has been typed closes it at once, keyboard and all. Left to
 * itself the keyboard takes that Back to put itself away, and the search only follows once it has
 * gone - so for as long as there is nothing to lose, a callback above the keyboard's own takes it.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class UntouchedSearchBack(private val topBar: MySearchMenu) {
    private val callback = OnBackInvokedCallback { topBar.closeSearch() }
    private var isRegistered = false

    fun sync() {
        val dispatcher = (topBar.context as? Activity)?.onBackInvokedDispatcher ?: return
        val wanted = topBar.isSearchOpen && topBar.getCurrentQuery().isEmpty()
        if (wanted == isRegistered) {
            return
        }

        isRegistered = wanted
        if (wanted) {
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
        } else {
            dispatcher.unregisterOnBackInvokedCallback(callback)
        }
    }
}

private fun Context.curve(id: Int): Interpolator = AnimationUtils.loadInterpolator(this, id)
