@file:Suppress("TooManyFunctions") // one small edit apiece over the one stored list

package org.fossify.gallery.extensions

import android.content.Context
import java.io.File
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.gallery.helpers.MAX_TABS
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.models.Tab
import org.fossify.gallery.models.TabLocation
import org.fossify.gallery.models.TabScreen

/**
 * The places the app is keeping. A tab holds where it is and nothing else - no view, no adapter, no
 * media - so switching to one is putting that place back up rather than bringing a second app
 * forward. TabSwitcher.kt is what does the putting back; this file is only the list.
 *
 * The first tab is the app's own: it is never closed, and its place is dropped at every launch so
 * the app still opens where "Open on startup" says it should.
 *
 * The list is stored as JSON, so every read of it is a parse. Everything here takes it once and
 * works off what it has rather than asking again.
 */

/** Always at least one, never more than [MAX_TABS], whatever is on file. */
fun Context.tabs(): ArrayList<Tab> {
    val stored = config.parseTabs()
    if (stored.isEmpty()) {
        return arrayListOf(Tab())
    }

    return ArrayList(stored.take(MAX_TABS))
}

private fun Context.storeTabs(tabList: List<Tab>) {
    val kept = tabList.take(MAX_TABS).ifEmpty { listOf(Tab()) }
    config.saveTabs(kept)
    config.currentTabIndex = config.currentTabIndex.coerceIn(0, kept.lastIndex)
}

fun Context.tabCount() = tabs().size

/** Where the tab that is up sits in a list already in hand. Never points past its end. */
fun Context.currentTabIndexIn(tabList: List<Tab>) = config.currentTabIndex.coerceIn(0, tabList.lastIndex)

/** The position of the tab that is up. Never points past the end of the list. */
fun Context.currentTabIndex() = currentTabIndexIn(tabs())

fun Context.currentTab(): Tab = tabs().let { it[currentTabIndexIn(it)] }

/**
 * Writes down where the tab that is up has got to. Called on every switch and on the way out of
 * every screen, which is what lets tabs survive the app closing.
 */
fun Context.recordTabLocation(location: TabLocation, scrollPath: String = "", scrollOffset: Int = 0) {
    if (!config.tabsEnabled) {
        return
    }

    val tabList = tabs()
    val index = currentTabIndexIn(tabList)
    tabList[index] = tabList[index].copy(
        location = location,
        scrollPath = scrollPath,
        scrollOffset = scrollOffset
    )
    storeTabs(tabList)
}

/** Where the grid was left, for the screen putting that tab back up. Taken once and cleared. */
fun Context.takeTabScroll(): Pair<String, Int>? {
    val tabList = tabs()
    val index = currentTabIndexIn(tabList)
    val tab = tabList[index]
    if (tab.scrollPath.isEmpty()) {
        return null
    }

    tabList[index] = tab.copy(scrollPath = "", scrollOffset = 0)
    storeTabs(tabList)
    return tab.scrollPath to tab.scrollOffset
}

/**
 * Puts the first tab back to where the app opens by default. Run at every launch: the other tabs
 * are kept exactly as they were left, this one is the app's own front door.
 */
fun Context.resetFirstTab() {
    val tabList = tabs()
    tabList[0] = Tab()
    config.currentTabIndex = 0
    storeTabs(tabList)
}

/**
 * Adds a tab and makes it the one that is up. It has no place of its own yet, so the screen putting
 * it up falls through to the same startup handling a launch does. Returns false when full.
 */
fun Context.openNewTab(): Boolean {
    val tabList = tabs()
    if (tabList.size >= MAX_TABS) {
        return false
    }

    tabList.add(Tab())
    config.currentTabIndex = tabList.lastIndex
    storeTabs(tabList)
    return true
}

/**
 * Drops the tab at [index] and returns the position left showing. Tabs are numbered by where they
 * sit, so closing one renumbers those after it - which is what the switcher draws.
 */
fun Context.closeTab(index: Int): Int {
    val tabList = tabs()
    val current = currentTabIndexIn(tabList)
    if (index !in tabList.indices || tabList.size <= 1) {
        return current
    }

    tabList.removeAt(index)
    // staying where you are unless the tab under you is the one that went, in which case the one
    // that slid into its place is the obvious thing to be looking at
    config.currentTabIndex = when {
        index == current -> index.coerceAtMost(tabList.lastIndex)
        index < current -> current - 1
        else -> current
    }

    storeTabs(tabList)
    return config.currentTabIndex
}

/** The next tab along, wrapping. What a plain tap on the button asks for. */
fun Context.nextTabIndex() = tabs().let { (currentTabIndexIn(it) + 1) % it.size }

/**
 * Whether a tab's place is still there to go back to. A folder deleted or a photo removed while the
 * tab was in the background would otherwise open onto nothing.
 */
fun Context.isTabLocationGone(location: TabLocation): Boolean {
    if (!location.isDeep() || location.isSentinelTarget()) {
        return false
    }

    val target = location.target()
    // the bin is a real directory, but its own sentinel and the paths inside it are not worth
    // stat'ing on the main thread just to find they are there
    if (target == RECYCLE_BIN || target.startsWith(recycleBinPath)) {
        return false
    }

    return when (location.screen) {
        TabScreen.VIEWER -> !File(target).isFile
        else -> !File(target).isDirectory
    }
}
