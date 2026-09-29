package org.fossify.gallery.helpers

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.view.descendants
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import org.fossify.gallery.views.SettingsLink
import org.fossify.gallery.views.SettingsPage

/** How far a page slides as it swaps with another, against the screen's width. */
private const val SLIDE = 0.1f
private const val OUT_MS = 90L
private const val IN_MS = 220L

private const val OPEN_PAGE = "open_settings_page"

/**
 * The settings as a first page of categories, each opening a page of its own - the way the system's
 * own Settings, One UI and HyperOS lay theirs out. Every page is in the one layout already, so a row
 * keeps the id and the setup code it always had: this only decides which page is up, and moves
 * between them, a page coming in from the side a link points to and going back out the other way.
 */
class SettingsPages(
    private val scroller: NestedScrollView,
    /** Everything that scrolls, which is what slides. */
    private val content: ViewGroup,
    private val heading: TextView,
    private val home: ViewGroup,
    private val homeTitle: String,
    /** Told of the page coming up before its first frame: paint it, and name it in the bar. */
    private val onShown: (page: ViewGroup, title: String) -> Unit,
) {
    private val pages = HashMap<Int, SettingsPage>()
    private var open: SettingsPage? = null

    /** Where the first page was scrolled to, for coming back to it. */
    private var homeScroll = 0

    /** The links on the first page, each wearing the title and hue of the page it opens. */
    val links = home.descendants.filterIsInstance<SettingsLink>().toList()

    val title get() = open?.title ?: homeTitle

    /** Whichever page is up. */
    val shown: ViewGroup get() = open ?: home

    val all: Collection<SettingsPage> get() = pages.values

    init {
        links.forEach { link ->
            val page = content.findViewById<SettingsPage>(link.opens) ?: return@forEach
            pages[page.id] = page
            link.showing(page)
            link.setOnClickListener { show(page) }
        }
    }

    fun show(page: SettingsPage, animate: Boolean = true) {
        if (open === page) {
            return
        }

        if (open == null) {
            homeScroll = scroller.scrollY
        }

        switchTo(page, forward = true, animate)
    }

    /** Back to the first page, answering whether there was anywhere to go back from. */
    fun goHome(animate: Boolean = true): Boolean {
        if (open == null) {
            return false
        }

        switchTo(null, forward = false, animate)
        return true
    }

    fun saveState(outState: Bundle) {
        open?.let { outState.putInt(OPEN_PAGE, it.id) }
    }

    fun restoreState(state: Bundle?) {
        val id = state?.getInt(OPEN_PAGE, View.NO_ID) ?: View.NO_ID
        pages[id]?.let { show(it, animate = false) }
    }

    private fun switchTo(page: SettingsPage?, forward: Boolean, animate: Boolean) {
        content.animate().cancel()
        if (!animate) {
            content.alpha = 1f
            content.translationX = 0f
            swap(page)
            return
        }

        val rtl = content.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val shift = content.width * SLIDE * (if (forward) 1 else -1) * (if (rtl) -1 else 1)
        val context = content.context
        content.animate()
            .alpha(0f)
            .translationX(-shift)
            .setDuration(OUT_MS)
            .setInterpolator(AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_linear_in))
            .withEndAction {
                swap(page)
                content.translationX = shift
                content.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(IN_MS)
                    .setInterpolator(AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in))
                    .start()
            }
            .start()
    }

    private fun swap(page: SettingsPage?) {
        open = page
        home.isVisible = page == null
        pages.values.forEach { it.isVisible = it === page }
        heading.text = title
        onShown(shown, title)

        // a page opens at its top, and the first page comes back as it was left - once the page
        // swapped in has been measured, or the scroll is held to the one swapped out
        val target = if (page == null) homeScroll else 0
        scroller.scrollTo(0, target)
        content.doOnNextLayout { scroller.scrollTo(0, target) }
    }
}
