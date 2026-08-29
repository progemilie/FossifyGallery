package org.fossify.gallery.helpers

import android.view.ViewGroup
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.interfaces.GridPane
import org.fossify.gallery.views.GlassMenu
import org.fossify.gallery.views.NavPill
import org.fossify.gallery.views.TabChooser

/**
 * The chrome a browsing screen floats over its content - the search bar and, on the two top level
 * screens, the navigation pill - and the wiring that points it at whichever [GridPane] is up.
 *
 * Written once and worn by both browsing screens, so the one that holds two panes can hand the bar
 * from one to the other without any of it moving: [bind] swaps the toolbar's menu and re-aims the
 * listeners and the panning, and nothing about the bar itself is touched.
 */
class GridChrome(
    val topBar: MySearchMenu,
    private val contentBehind: ViewGroup,
    val navPill: NavPill? = null,
    tabChooser: TabChooser? = null,
) {
    val floatingTopBar = FloatingTopBar(topBar, contentBehind)

    /** The tab button worn on the end of the bar, where the screen has a chooser for it to open. */
    val tabBar = tabChooser?.let { TabBar(topBar, it) }

    private var pane: GridPane? = null

    /**
     * The wiring that outlives any one pane. Commons' [MySearchMenu.setupMenu] hangs listeners on
     * the search field rather than on the menu, so it belongs here and must not be run again by
     * [bind] - twice would have every keystroke searched twice.
     */
    fun attach(pane: GridPane) {
        topBar.setupMenu()
        GlassMenu.replaceOverflow(
            toolbar = topBar.requireToolbar(),
            spec = { this.pane?.menuSpec ?: pane.menuSpec },
            contentBehind = contentBehind,
            alsoOpenedBy = navPill?.menuButton
        )

        topBar.onSearchOpenListener = { this.pane?.onSearchToggled(true) }
        topBar.onSearchClosedListener = { this.pane?.onSearchToggled(false) }
        topBar.onSearchTextChangedListener = { text -> this.pane?.onSearchTextChanged(text) }
        topBar.requireToolbar().setOnMenuItemClickListener { item ->
            this.pane?.onMenuItemClick(item.itemId) == true
        }

        bind(pane)
    }

    /** Points the bar, the drop-down and the panning at [pane]. */
    fun bind(pane: GridPane) {
        this.pane = pane
        val toolbar = topBar.requireToolbar()
        toolbar.menu.clear()
        toolbar.inflateMenu(pane.menuRes)
        pane.dressTopBar(topBar)
        pane.refreshMenuItems(toolbar.menu)
        // a freshly inflated menu wears the icons' own colours, which are not the ones that read
        // against the pill - the tinting is part of what commons repaints here
        updateColors()

        floatingTopBar.floatOver(pane.grid, pane.refreshLayout, pane::gridNeedsTopRoom)
        navPill?.panWith(pane.grid)
    }

    /** Asks the pane that is up for its menu entries again, whatever has just changed. */
    fun refreshMenuItems() {
        pane?.refreshMenuItems(topBar.requireToolbar().menu)
    }

    /**
     * Commons paints the bar back onto its band of colour on every one of these, so the lifting has
     * to run again right behind it.
     */
    fun updateColors() {
        topBar.updateColors()
        floatingTopBar.makeFloating()
        // behind the lifting above, which is what leaves the bar with room to hang a button off
        tabBar?.apply(contentBehind)
        navPill?.updateColors()
    }

    val isSearchOpen get() = topBar.isSearchOpen

    fun closeSearch() = topBar.closeSearch()
}
