package org.fossify.gallery.helpers

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.helpers.isSPlus
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.interfaces.GridPane
import org.fossify.gallery.models.SearchFilter
import org.fossify.gallery.views.FilterChip
import org.fossify.gallery.views.SearchLine
import org.fossify.gallery.views.SearchOverlay

private const val DIM_IN_MS = 300L
private const val OPTIONS_IN_MS = 380L
private const val EDGE_IN_MS = 520L
private const val OUT_MS = 180L

/** How much of the edge's entrance it takes to come up to full strength; the light keeps rising after. */
private const val EDGE_FADE_SHARE = 0.35f

/** The search pill's edge, along its lit top. */
private const val SEARCH_EDGE_OPACITY = 0.5f

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
    private val dimCurve = context.curve(R.interpolator.search_dim)
    private val settle = context.curve(R.interpolator.search_settle)
    private val leave = context.curve(R.interpolator.search_leave)

    private val overlay = SearchOverlay(context)
    private val chip = FilterChip(topBar, SearchLine.around(topBar))
    private val edge = SearchEdge(topBar, settle, leave)
    private val keyboard = SearchKeyboard(topBar)

    private val rise = resources.getDimension(R.dimen.search_options_rise)
    private val barGap = resources.getDimensionPixelSize(R.dimen.search_options_top_gap)

    private var pane: GridPane? = null

    // photos the trait index reads while the options are up can call for pills of their own
    private val traitsFound = TraitIndex.Listener { askOptions() }

    // what the options on show were filled from, so an answer that changes nothing leaves them be
    private var shownOptions: SearchOptions? = null

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
        overlay.blurred = contentBehind
        overlay.onChosen = { filter ->
            // the filter already on, picked again, is taken off
            onFilterChosen?.invoke(filter.takeUnless { it == pane?.activeFilter })
        }

        overlay.onDimTapped = keyboard::putAway
        chip.onClear = { onFilterChosen?.invoke(null) }
    }

    /** Points the chip and the options at [pane]. */
    fun bind(pane: GridPane) {
        val isNewPane = pane !== this.pane
        this.pane = pane
        refreshChip()

        // a search carried over to another pane, whose options are the new pane's
        if (isNewPane && topBar.isSearchOpen && topBar.getCurrentQuery().isEmpty()) {
            showOptions()
        }
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
        edge.light(on = true)
        showOptions()
    }

    fun onSearchClosed() {
        optionsAsked++
        edge.light(on = false)
        fadeOptions()
    }

    fun onSearchTextChanged(text: String) {
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
        overlay.canBlur = isSPlus() && Glass.isEnabled(context)
        overlay.keepClearOfBar(topBar.height + barGap)

        // whatever a fade on its way out had left of the dim is where it comes back from
        overlay.options.animate().cancel()
        overlay.beVisible()
        overlay.dimTo(1f, DIM_IN_MS, dimCurve)
        TraitIndex.addListener(traitsFound)
        askOptions()
    }

    private fun askOptions() {
        val pane = pane?.takeIf { topBar.isSearchOpen && topBar.getCurrentQuery().isEmpty() } ?: return
        val asked = ++optionsAsked
        // answered again whenever what the pane counts them from changes under an open search
        pane.loadSearchOptions { options ->
            if (asked == optionsAsked && topBar.isSearchOpen) {
                fill(options, pane.activeFilter)
            }
        }
    }

    private fun fill(options: SearchOptions, active: SearchFilter?) {
        if (options == shownOptions) {
            return
        }

        val isArriving = shownOptions?.isEmpty != false
        shownOptions = options
        overlay.fill(options, active)
        if (isArriving) {
            // they rise the last little way into place as they fade in
            overlay.options.alpha = 0f
            overlay.options.translationY = rise
            overlay.options.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(OPTIONS_IN_MS)
                .setInterpolator(settle)
                .start()
        }
    }

    /** Fades the dim, the blur and the options away as one. */
    private fun fadeOptions() {
        shownOptions = null
        TraitIndex.removeListener(traitsFound)
        if (!overlay.isShown) {
            return
        }

        overlay.options.animate().alpha(0f).setDuration(OUT_MS).setInterpolator(leave).start()
        overlay.dimTo(0f, OUT_MS, leave).doOnEnd {
            if (overlay.dimLevel == 0f) {
                overlay.hide()
            }
        }
    }
}

/**
 * The search pill's edge while a search is open, the [LitEdge] the reorder mode's Save wears. In, it
 * comes up with its light along the bottom of the pill and the light rises to rest along the top; out,
 * it fades with everything else.
 */
private class SearchEdge(topBar: MySearchMenu, private val settle: Interpolator, private val leave: Interpolator) {
    private val context = topBar.context
    private val edge = LitEdgeDrawable(
        context,
        context.resources.getDimension(org.fossify.commons.R.dimen.material_dialog_corner_radius),
        // twice Save's: on a pill the length of the screen the usual line is lost, light rising and all
        LitEdge(opacity = SEARCH_EDGE_OPACITY)
    ).apply { strength = 0f }

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
 * The search field holds its focus, and with it the cursor, only while the keyboard is up. Whatever
 * puts the keyboard away - Back, a tap on the dim, anything else - leaves the search open with nothing
 * blinking in it, so Back takes the keyboard first and closes the search the time after.
 *
 * The field never asks for the fullscreen editor a landscape keyboard puts up, which would cover the
 * options with a box of its own.
 */
private class SearchKeyboard(topBar: MySearchMenu) {
    private val field = topBar.binding.topToolbarSearch
    private var isUp = false

    init {
        field.imeOptions = field.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN

        // turning the screen rebuilds it with the search closed - commons opens it only for focus
        // taken once the screen is up - so the field is not to be handed back its text or its focus
        field.isSaveEnabled = false
        field.post {
            if (!topBar.isSearchOpen) {
                field.clearFocus()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(field) { _, insets ->
            val wasUp = isUp
            isUp = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (wasUp && !isUp) {
                // not in the middle of insets: losing focus has the field let go of the keyboard
                field.post {
                    if (!isUp) {
                        field.clearFocus()
                    }
                }
            }

            insets
        }

        // commons opens the search only as the field takes focus, and a tap on a field that holds it
        // already is a click instead - so one somehow left focused with the search closed opens here
        field.setOnClickListener {
            if (!topBar.isSearchOpen) {
                field.clearFocus()
                field.requestFocus()
            }
        }

        // the keyboard goes with the screen, and is not there to come back to
        (field.context as? LifecycleOwner)?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = field.clearFocus()
        })
    }

    /** Puts the keyboard away and leaves the search up, without a cursor even where no keyboard showed. */
    fun putAway() {
        (field.context as? Activity)?.hideKeyboard(field)
        field.clearFocus()
    }
}

private fun Context.curve(id: Int): Interpolator = AnimationUtils.loadInterpolator(this, id)
