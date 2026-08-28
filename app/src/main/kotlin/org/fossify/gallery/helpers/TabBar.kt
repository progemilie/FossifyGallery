package org.fossify.gallery.helpers

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.updateLayoutParams
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.currentTabIndex
import org.fossify.gallery.extensions.tabCount
import org.fossify.gallery.views.GlassPanel
import org.fossify.gallery.views.TabBadgeDrawable
import org.fossify.gallery.views.TabChoice
import org.fossify.gallery.views.TabChooser
import org.fossify.gallery.views.holdToChoose

/**
 * The tab button, worn on the right hand end of a browsing screen's search bar.
 *
 * It is put *inside* commons' bar rather than floated beside it, so it pans away with the bar, takes
 * the bar's status bar inset for free, and moves with it on a rotation. The pill it sits next to is
 * shortened by the same amount to make room. Commons cannot be edited, but its binding is public and
 * [FloatingTopBar] already builds the pill's glass this way.
 *
 * [apply] runs again after every one of commons' repaints, so everything here is written to be safe
 * to run any number of times.
 */
class TabBar(
    private val topBar: MySearchMenu,
    private val chooser: TabChooser,
) {
    private val context = topBar.context
    private val resources = topBar.resources
    private val size = resources.getDimensionPixelSize(R.dimen.tab_button_size)
    private val gap = resources.getDimensionPixelSize(R.dimen.tab_button_gap)
    private val badgeSize = resources.getDimensionPixelSize(R.dimen.tab_button_badge_size)

    private var button: GlassPanel? = null
    private var icon: ImageView? = null

    /** Whether the screen wants the button at all - tabs turned off, or somewhere they make no sense. */
    var isAvailable = true
        set(value) {
            field = value
            refresh()
        }

    var onQuickSwitch: (() -> Unit)? = null
    var onChoice: ((TabChoice) -> Unit)? = null

    /**
     * Builds the button if it is not there, points its glass at [contentBehind] again, and puts the
     * current tab's number on it.
     */
    fun apply(contentBehind: ViewGroup) {
        if (!context.config.tabsEnabled) {
            button?.beGone()
            shortenPill(false)
            return
        }

        build()
        button?.frost(contentBehind)
        refresh()
    }

    private fun build() {
        if (button != null) {
            return
        }

        val panel = GlassPanel(context).apply {
            cornerRadius = size / 2f
            blurRadius = Glass.SEARCH_PILL_RADIUS
            elevation = resources.getDimension(R.dimen.floating_chrome_elevation)
            contentDescription = context.getString(R.string.switch_tab)
        }

        // the same rounded square the viewer wears, so the tab switcher looks the one thing
        // wherever it is offered
        val badge = ImageView(context)
        panel.addView(
            badge,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        topBar.binding.searchBarContainer.addView(
            panel,
            FrameLayout.LayoutParams(size, size, Gravity.END or Gravity.CENTER_VERTICAL)
        )

        panel.setOnClickListener { onQuickSwitch?.invoke() }
        panel.holdToChoose(
            chooser = chooser,
            onOpen = {
                chooser.dropsBelow = true
                chooser.setTabs(
                    count = context.tabCount(),
                    current = context.currentTabIndex(),
                    canAdd = context.tabCount() < MAX_TABS
                )
                true
            },
            onChosen = { chooser.choice?.let { onChoice?.invoke(it) } }
        )

        button = panel
        icon = badge
    }

    /** Re-reads which tab is up, and whether the button belongs on screen at all. */
    fun refresh() {
        val panel = button ?: return
        val shouldShow = isAvailable && context.config.tabsEnabled
        if (shouldShow) {
            panel.beVisible()
            // rebuilt rather than renumbered, so a theme changed since is picked up with it
            icon?.setImageDrawable(
                TabBadgeDrawable(context, Glass.contentColor(context), badgeSize).apply {
                    index = context.currentTabIndex()
                }
            )
        } else {
            panel.beGone()
            chooser.close()
        }

        shortenPill(shouldShow)
    }

    /**
     * Hands the button its room by taking it off the end of the search pill, which is what makes
     * the bar visibly shorter rather than the button sitting on top of the three dots.
     */
    private fun shortenPill(shorten: Boolean) {
        topBar.binding.toolbarContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginEnd = if (shorten) size + gap else 0
        }
    }

    /** The button itself, for a screen that has to know whether a touch landed on it. */
    val view: View? get() = button
}
