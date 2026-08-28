package org.fossify.gallery.models

import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.helpers.SHOW_ALL

/** Which of the app's browsing screens a tab is sitting on. */
enum class TabScreen { ALBUMS, PICTURES, FOLDER, VIEWER }

/**
 * Where a tab is. Not a screen and not a view - only enough to put one back up, which is why a tab
 * costs nothing to keep and why three of them hold no more than one does.
 *
 * [TabScreen.FOLDER] covers the favourites, the recycle bin and the USB storage too: those are
 * already nothing but paths handed to the same grid.
 */
data class TabLocation(
    var screen: TabScreen = TabScreen.ALBUMS,
    /** The folder, on FOLDER. The file being looked at, on VIEWER. */
    var path: String = "",
    /** VIEWER only: the folder it was opened from, empty for the all media grid. */
    var folderPath: String = "",
    /** ALBUMS only: the folder group stepped into, 0 at the root. */
    var groupId: Long = 0L,
    /** ALBUMS only: how far "group direct subfolders" has been drilled into. */
    var subfolderPrefix: String = "",
) {
    /** Whether putting this back up needs a screen above [org.fossify.gallery.activities.MainActivity]. */
    fun isDeep() = screen == TabScreen.FOLDER || screen == TabScreen.VIEWER

    /**
     * The folder a deep location opens on the way in. The sentinels are paths in their own right,
     * so only the all media grid has nothing to name.
     */
    fun gridPath() = when (screen) {
        TabScreen.FOLDER -> path
        TabScreen.VIEWER -> folderPath
        else -> ""
    }

    /** Whether the grid this opens is the whole library rather than one folder of its own. */
    fun isAllMediaGrid() = gridPath().let { it.isEmpty() || it == SHOW_ALL }

    /** What the tab is fetched from, for a look at whether it is still there. */
    fun target() = when (screen) {
        TabScreen.VIEWER -> path
        TabScreen.FOLDER -> path
        else -> ""
    }

    /** The sentinel folders cannot be stat'd, so nothing may go looking for them on disk. */
    fun isSentinelTarget() = target().let { it == FAVORITES || it == SHOW_ALL || it.isEmpty() }
}

/**
 * One of the places the app is keeping.
 *
 * [location] is null for a tab that has never been anywhere, which is a tab just opened and the
 * first tab at every launch - both come up on whatever "Open on startup" names rather than on a
 * remembered place. [scrollPath] is the item that was at the top of the grid rather than an index,
 * so it survives the rescan that rebuilds the list around it.
 */
data class Tab(
    var location: TabLocation? = null,
    var scrollPath: String = "",
    var scrollOffset: Int = 0,
)
