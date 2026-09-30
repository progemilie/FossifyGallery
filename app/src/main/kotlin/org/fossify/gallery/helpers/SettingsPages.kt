package org.fossify.gallery.helpers

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.descendants
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import org.fossify.gallery.views.SettingsLink
import org.fossify.gallery.views.SettingsPage

/** How long a page takes to push another out across the screen. */
private const val PUSH_MS = 300L

/** A revealed setting is brought this far down what can be seen, and washed in its page's hue. */
private const val REVEAL_AT = 0.3f
private const val REVEAL_WASH_ALPHA = 90
private const val REVEAL_HOLD_MS = 700L
private const val REVEAL_FADE_MS = 900L

private const val OPEN_PAGE = "open_settings_page"

/**
 * The settings as a first page of categories, each opening a page of its own - the way the system's
 * own Settings, One UI and HyperOS lay theirs out. Every page is in the one layout already, so a row
 * keeps the id and the setup code it always had: this only decides which page is up, and moves
 * between them, a page coming in from the side a link points to and going back out the other way.
 *
 * A page only ever opens from the first page and goes back to it, so the first page scrolls on its
 * own and the others share a second scroller: a push draws both, side by side, and the first page
 * keeps its place for as long as a page is open.
 */
class SettingsPages(
    private val homeScroller: NestedScrollView,
    /** The first page's name at the top of it, which is its title in the bar too. */
    private val homeHeading: TextView,
    private val home: ViewGroup,
    /** The scroller every other page is in, one up at a time. */
    private val pageScroller: NestedScrollView,
    private val pageHeading: TextView,
    /** Told of the page coming up before it moves: paint it, and name it in the bar. */
    private val onShown: (page: ViewGroup, title: String) -> Unit,
) {
    // in the order their links are in, which a search lists its findings by
    private val pages = LinkedHashMap<Int, SettingsPage>()
    private var open: SettingsPage? = null

    /** The links on the first page, each wearing the title and hue of the page it opens. */
    val links = home.descendants.filterIsInstance<SettingsLink>().toList()

    val title: String get() = open?.title ?: homeHeading.text.toString()

    /** Whichever page is up. */
    val shown: ViewGroup get() = open ?: home

    /** The scroller of the page that is up, and the name at the top of it, which the bar takes over from. */
    val scroller: NestedScrollView get() = if (open == null) homeScroller else pageScroller
    val heading: TextView get() = if (open == null) homeHeading else pageHeading

    val all: Collection<SettingsPage> get() = pages.values

    init {
        links.forEach { link ->
            val page = pageScroller.findViewById<SettingsPage>(link.opens) ?: return@forEach
            pages[page.id] = page
            link.showing(page)
            link.setOnClickListener { show(page) }
        }
    }

    /** Opens [page], and brings [reveal] - one of its settings - into view and points it out. */
    fun show(page: SettingsPage, animate: Boolean = true, reveal: View? = null) {
        if (open === page) {
            return
        }

        switchTo(page, forward = true, animate)
        reveal?.let { row -> page.doOnNextLayout { reveal(row) } }
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

    /**
     * Puts [page] up - none being the first page - and pushes the scroller it is in across the screen
     * as the other goes out, the page coming in painted and laid out before either moves.
     */
    private fun switchTo(page: SettingsPage?, forward: Boolean, animate: Boolean) {
        val outgoing = scroller
        open = page
        // a page going back stays in its scroller as that is pushed away
        if (page != null) {
            pages.values.forEach { it.isVisible = it === page }
            pageHeading.text = page.title
            // a page opens at its top, the first page being left as it was
            pageScroller.scrollTo(0, 0)
        }

        onShown(shown, title)
        val incoming = scroller
        if (incoming === outgoing) {
            return
        }

        outgoing.animate().cancel()
        incoming.animate().cancel()
        // still on screen from a push the other way that has not finished: it turns back from there
        val turningBack = incoming.isVisible
        incoming.isVisible = true
        if (!animate || !outgoing.isLaidOut) {
            incoming.translationX = 0f
            hide(outgoing)
            return
        }

        val rtl = incoming.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val width = outgoing.width * (if (forward) 1f else -1f) * (if (rtl) -1f else 1f)
        if (!turningBack) {
            incoming.translationX = width
        }

        incoming.animate().translationX(0f).setDuration(PUSH_MS).setInterpolator(incoming.enterCurve()).start()
        outgoing.animate()
            .translationX(-width)
            .setDuration(PUSH_MS)
            .setInterpolator(outgoing.enterCurve())
            .withEndAction { hide(outgoing) }
            .start()
    }

    private fun hide(pane: View) {
        pane.isVisible = false
        pane.translationX = 0f
    }

    /**
     * Scrolls [row] a third of the way down what can be seen, and washes it in the page's hue as the
     * page comes in, fading back out - a press's ripple was too faint a mark on a dark card.
     */
    private fun reveal(row: View) {
        var top = 0
        var view = row
        while (view.parent !== pageScroller) {
            top += view.top
            view = view.parent as? View ?: return
        }

        val visible = pageScroller.height - pageScroller.paddingTop - pageScroller.paddingBottom
        pageScroller.smoothScrollTo(0, (top - visible * REVEAL_AT).toInt().coerceAtLeast(0))

        val wash = (open?.iconColor ?: return).toDrawable().apply { alpha = REVEAL_WASH_ALPHA }
        row.foreground = wash
        ValueAnimator.ofInt(REVEAL_WASH_ALPHA, 0).apply {
            startDelay = PUSH_MS + REVEAL_HOLD_MS
            duration = REVEAL_FADE_MS
            addUpdateListener { wash.alpha = it.animatedValue as Int }
            doOnEnd {
                if (row.foreground === wash) {
                    row.foreground = null
                }
            }

            start()
        }
    }
}
