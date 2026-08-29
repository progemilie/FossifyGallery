package org.fossify.gallery.helpers

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.activities.MainActivity
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.extensions.closeTab
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.currentTabIndex
import org.fossify.gallery.extensions.nextTabIndex
import org.fossify.gallery.extensions.openNewTab
import org.fossify.gallery.extensions.recordTabLocation
import org.fossify.gallery.extensions.tabCount
import org.fossify.gallery.models.TabLocation
import org.fossify.gallery.views.TabChoice

/**
 * Moving between tabs. A tab is a record of a place rather than a screen kept alive, so switching
 * is putting that place back up in the one set of screens the app has - nothing is held twice, and
 * nothing is brought forward.
 *
 * Where both places are grids of [MainActivity]'s own, that screen swaps its panes and no activity
 * is touched at all. Anything deeper - a folder, or a file open in the viewer - lives above
 * MainActivity in the stack, so the current stack is dropped back to it and the target's is built:
 * the same stack the user would have walked, so Back behaves normally once inside a tab.
 */
object TabSwitcher {

    /**
     * Whether a switch is in flight. Between asking for one and the tab landing, the screen on its
     * way out still pauses and still stops - and its place is the *old* tab's, which by then is no
     * longer the one being written to.
     */
    var isSwitching = false
        private set

    /** Whichever screen is on top says where it is, so the tab it belongs to can be written down. */
    interface Locatable {
        fun currentTabLocation(): TabLocation

        /** The item at the top of the grid and how far past the top edge, where there is a grid. */
        fun currentTabScroll(): Pair<String, Int>? = null
    }

    /** Writes down where [screen] has got to, under the tab that is up. */
    fun record(activity: Activity, screen: Locatable) {
        if (isSwitching) {
            return
        }

        val scroll = screen.currentTabScroll()
        activity.recordTabLocation(
            location = screen.currentTabLocation(),
            scrollPath = scroll?.first.orEmpty(),
            scrollOffset = scroll?.second ?: 0
        )
    }

    /** The tap on the button: the next tab along, or a new one when this is the only tab. */
    fun quickSwitch(activity: Activity, screen: Locatable) {
        if (activity.tabCount() < 2) {
            newTab(activity, screen)
        } else {
            switchTo(activity, screen, activity.nextTabIndex())
        }
    }

    /**
     * The one dispatch of what a finger let go of the chooser on, so the three screens that offer
     * it cannot drift apart. [onClosed] repaints whatever wears the tab's number on the screen
     * making the call: closing a tab below the one you are on renumbers the one you are.
     */
    fun handle(activity: Activity, screen: Locatable, choice: TabChoice, onClosed: () -> Unit) {
        when (choice) {
            is TabChoice.New -> newTab(activity, screen)
            is TabChoice.Switch -> switchTo(activity, screen, choice.index)
            is TabChoice.Close -> {
                close(activity, screen, choice.index)
                onClosed()
            }
        }
    }

    /** Opens a tab with no place of its own, which comes up on the startup screen. */
    fun newTab(activity: Activity, screen: Locatable) {
        record(activity, screen)
        if (activity.openNewTab()) {
            restart(activity)
        } else {
            // the chooser stops offering another one at the limit, so this is only reached by a
            // tap on a button - which must still say why nothing happened
            activity.toast(activity.getString(R.string.tab_limit_reached, MAX_TABS))
        }
    }

    fun switchTo(activity: Activity, screen: Locatable, index: Int) {
        if (index == activity.currentTabIndex()) {
            return
        }

        record(activity, screen)
        activity.config.currentTabIndex = index
        restart(activity)
    }

    /**
     * Drops a tab. Closing the one you are looking at leaves you on whatever slid into its place,
     * so that one has to be put up; closing any other only shortens the list.
     */
    fun close(activity: Activity, screen: Locatable, index: Int) {
        val wasCurrent = index == activity.currentTabIndex()
        if (wasCurrent) {
            // its place is about to go, so there is nothing worth writing down for it
            activity.closeTab(index)
            restart(activity)
        } else {
            record(activity, screen)
            activity.closeTab(index)
        }
    }

    /**
     * Called by the screen the tab has landed on, which is where a switch ends. A folder or a file
     * is put up by launching over [MainActivity], so for those it is the screen that opens that
     * says so - not the one that posted the intent, which is still showing the place being left.
     */
    fun onTabApplied() {
        isSwitching = false
    }

    /**
     * Drops the stack back to [MainActivity] and asks it to put the current tab's place up.
     *
     * CLEAR_TOP with SINGLE_TOP reuses the MainActivity that is already at the root rather than
     * building another, so this finishes whatever is above it and arrives as onNewIntent. The
     * animation is taken out because a tab switch is a swap rather than somewhere new to be.
     */
    private fun restart(activity: Activity) {
        isSwitching = true

        // the one copy of what a grid is showing, which the incoming pane would otherwise read as
        // its own until its first scan came back
        MediaActivity.mMedia = ArrayList()

        val intent = Intent(activity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(RESTORE_TAB, true)
        }

        val options = ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle()
        activity.startActivity(intent, options)
    }
}
