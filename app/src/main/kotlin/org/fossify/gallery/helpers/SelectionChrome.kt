package org.fossify.gallery.helpers

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.children
import org.fossify.gallery.R
import org.fossify.gallery.views.GlassMenu
import org.fossify.gallery.views.SelectionPills

/**
 * What a browsing screen puts up in place of the platform's contextual action bar: a pill at the top
 * saying what is selected, and one along the foot holding what can be done with it.
 *
 * The bar itself never exists. `AppCompatActivity.onWindowStartingSupportActionMode` is offered the
 * chance to supply an action mode of its own before AppCompat builds one, and [start] takes it - so
 * there is no band of colour over the top of the grid to hide, and the grid's own edge fades are
 * what the system bars read against, exactly as they do the rest of the time.
 *
 * Everything upstream does through the action mode carries over untouched: the adapter inflates its
 * menu into [Mode]'s, hides the items that do not apply in `prepareActionMode`, and invalidates on
 * every change to the selection, which is what fills the pills in again.
 */
class SelectionChrome(
    private val context: Context,
    private val pills: SelectionPills,
    contentBehind: ViewGroup,
) {
    private var mode: Mode? = null

    /** Whatever the pill's buttons did not take, for the drop-down the menu segment opens. */
    private var overflow = LinkedHashMap<Int, MenuItem>()

    /** Told when a selection starts or ends, for chrome that has to get out of its way. */
    var onActiveChanged: ((Boolean) -> Unit)? = null

    val isActive get() = mode != null

    init {
        pills.onBack = { mode?.finish() }
        // upstream hangs select-all off the count in its own bar; the pill inherits it
        pills.onCountTapped = { mode?.selectAll() }
        GlassMenu.openedBy(
            button = pills.menuButton,
            items = { LinkedHashMap(overflow) },
            onPick = { item -> mode?.pick(item) },
            spec = { SELECTION_MENU },
            contentBehind = contentBehind,
        )
    }

    /**
     * Answers AppCompat's offer to supply the action mode, putting the pills up in place of the bar
     * it would otherwise have built. Null where the callback wants no mode at all, which leaves
     * AppCompat to do whatever it would have done.
     */
    fun start(callback: ActionMode.Callback): ActionMode? {
        mode?.finish()
        val started = Mode(callback)
        if (!started.create()) {
            return null
        }

        mode = started
        pills.show()
        onActiveChanged?.invoke(true)
        started.invalidate()
        return started
    }

    /** Repainted on every resume: the theme can change while the screen is in the back stack. */
    fun updateColors() {
        if (isActive) {
            pills.updateColors()
        }
    }

    private fun ended(mode: Mode) {
        if (this.mode !== mode) {
            return
        }

        this.mode = null
        overflow = LinkedHashMap()
        pills.hide()
        onActiveChanged?.invoke(false)
    }

    /**
     * The action mode itself. Its menu is a real one borrowed from a [PopupMenu] that is never
     * shown: AppCompat hands the adapter's own callback a wrapper around it, and only a menu of the
     * support library's own can be wrapped.
     */
    // most of what is below is ActionMode's own contract, a line apiece
    @Suppress("TooManyFunctions")
    private inner class Mode(private val callback: ActionMode.Callback) : ActionMode() {
        private val backingMenu = PopupMenu(context, pills.menuButton).menu
        private var custom: View? = null
        private var titleText: CharSequence? = null
        private var subtitleText: CharSequence? = null
        private var isFinished = false

        fun create() = callback.onCreateActionMode(this, backingMenu)

        fun pick(item: MenuItem) {
            callback.onActionItemClicked(this, item)
        }

        /** Upstream puts select-all behind a tap on the count, in a view it never shows anywhere. */
        fun selectAll() {
            custom?.performClick()
        }

        override fun getMenu(): Menu = backingMenu

        // upstream inflates through the activity's, so this is only here to complete the contract
        override fun getMenuInflater(): MenuInflater = MenuInflater(context)

        override fun getTitle() = titleText

        override fun getSubtitle() = subtitleText

        override fun getCustomView() = custom

        override fun setTitle(title: CharSequence?) {
            titleText = title
        }

        override fun setTitle(resId: Int) = setTitle(context.getString(resId))

        override fun setSubtitle(subtitle: CharSequence?) {
            subtitleText = subtitle
        }

        override fun setSubtitle(resId: Int) = setSubtitle(context.getString(resId))

        // never added to the screen: upstream carries the count in it, which the pill draws itself
        override fun setCustomView(view: View?) {
            custom = view
        }

        override fun invalidate() {
            callback.onPrepareActionMode(this, backingMenu)
            fillPills()
        }

        override fun finish() {
            if (isFinished) {
                return
            }

            // set before the callback, which finishes right back through here on its way out
            isFinished = true
            callback.onDestroyActionMode(this)
            ended(this)
        }

        /**
         * Reads the menu again: what the selection holds, and which of its actions the pill can show
         * as buttons of its own. An item with a submenu is left for the drop-down, which is the only
         * one of the two with room to open one.
         */
        private fun fillPills() {
            pills.setCount((custom as? TextView)?.text ?: titleText)

            val visible = backingMenu.children.filter { it.isVisible }.toList()
            val buttons = visible.filter {
                it.icon != null && it.subMenu == null && it.itemId !in MENU_ONLY_ACTIONS
            }
            val shown = if (visible.size <= MAX_ACTIONS && buttons.size == visible.size) {
                buttons
            } else {
                buttons.take(MAX_ACTIONS - 1)
            }

            overflow = LinkedHashMap<Int, MenuItem>().apply {
                visible.filterNot { it in shown }.forEach { put(it.itemId, it) }
            }

            pills.setActions(shown, hasMore = overflow.isNotEmpty(), onPick = ::pick)
        }
    }

    private companion object {
        /**
         * How many actions the pill shows as buttons of its own. Chosen so it comes out about the
         * width of the navigation pill it stands in place of; the rest go behind the menu segment.
         */
        const val MAX_ACTIONS = 5

        /**
         * Kept behind the menu segment however much room the row has: the mirror rewrites every
         * selected file in place, which is not something a stray tap should reach.
         */
        val MENU_ONLY_ACTIONS = setOf(R.id.cab_mirror)
    }
}
