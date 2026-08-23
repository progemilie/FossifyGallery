package org.fossify.gallery.interfaces

import android.view.Menu
import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.fossify.commons.views.MyRecyclerView
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.helpers.MenuSpec

/**
 * One of the grids a browsing screen can be showing.
 *
 * The chrome - the search bar, and the navigation pill where there is one - belongs to the screen
 * rather than to the grid under it, so that a screen holding two of these can swap the content
 * without the chrome moving. What is left is this: everything the screen has to ask whichever pane
 * is currently up.
 */
interface GridPane {
    /** The whole pane, which is what a swap slides. */
    val root: View

    val grid: MyRecyclerView
    val refreshLayout: SwipeRefreshLayout

    /** What this pane fills the toolbar with, and how the drop-down arranges it. */
    val menuRes: Int
    val menuSpec: MenuSpec

    /** The hint, the back arrow, and whatever else the bar wears while this pane is up. */
    fun dressTopBar(topBar: MySearchMenu)

    fun refreshMenuItems(menu: Menu)

    /** True if the pane knew what to do with it. */
    fun onMenuItemClick(itemId: Int): Boolean

    fun onSearchTextChanged(text: String)
    fun onSearchToggled(isOpen: Boolean)

    /**
     * Whether the grid has room to make for the bar, or something else on the screen is already
     * keeping clear of it on the grid's behalf.
     */
    fun gridNeedsTopRoom(): Boolean = true

    /** Handled here first: an arrangement in progress, an open search, a group stepped into. */
    fun onBackPressed(): Boolean = false

    /** Brought up: repaint against the current theme, and load whatever it is showing. */
    fun onActivated()

    /** Left behind, by the screen pausing or by a swap to the other pane. */
    fun onDeactivated()
}
