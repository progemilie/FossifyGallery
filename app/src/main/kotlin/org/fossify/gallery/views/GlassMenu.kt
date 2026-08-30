package org.fossify.gallery.views

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.gallery.R
import org.fossify.gallery.databinding.GlassMenuBinding
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.helpers.MenuEntry
import org.fossify.gallery.helpers.MenuSpec

/**
 * The frosted drop-down a browsing screen opens from its three dots, in place of the platform's own
 * overflow popup - the same pane of glass ([Glass]) the search pill and the bottom action choosers
 * are made of, with its items gathered into sections by a [MenuSpec].
 *
 * It is built from a live [Menu] every time it opens and picked from through that same menu, so
 * everything a screen already does - hiding an item whose job a bottom action button has taken, or
 * one the current folder has no use for - carries over with nothing to wire up. Whatever is already
 * showing as a button of its own is left out for the same reason.
 *
 * Where those items come from is [items]' business, so one drop-down serves a toolbar's overflow
 * and the selection pill's menu button alike.
 */
class GlassMenu private constructor(
    private val host: View,
    private val items: () -> LinkedHashMap<Int, MenuItem>,
    private val onPick: (MenuItem) -> Unit,
    private val spec: () -> MenuSpec,
    private val contentBehind: ViewGroup,
) {
    companion object {
        /**
         * Puts the drop-down on [toolbar]'s three dots. [contentBehind] is what it frosts.
         * [alsoOpenedBy] is a second door onto the same menu, opening upward from wherever it sits.
         *
         * [spec] is asked again every time the menu opens rather than held: a screen that swaps one
         * pane of content for another swaps the toolbar's menu with it, and the sections along with.
         */
        fun replaceOverflow(
            toolbar: Toolbar,
            spec: () -> MenuSpec,
            contentBehind: ViewGroup,
            alsoOpenedBy: View? = null,
        ) = GlassMenu(
            host = toolbar,
            items = toolbar::overflowItems,
            onPick = { toolbar.menu.performIdentifierAction(it.itemId, 0) },
            spec = spec,
            contentBehind = contentBehind,
        ).also { it.attachToToolbar(toolbar, alsoOpenedBy) }

        /**
         * The same drop-down opened upward out of one [button], over a menu that is nobody's
         * toolbar - the selection pill's, whose items belong to an action mode.
         */
        fun openedBy(
            button: View,
            items: () -> LinkedHashMap<Int, MenuItem>,
            onPick: (MenuItem) -> Unit,
            spec: () -> MenuSpec,
            contentBehind: ViewGroup,
        ) {
            GlassMenu(button, items, onPick, spec, contentBehind).attach(button)
        }
    }

    private val resources = host.resources
    private val inflater = LayoutInflater.from(host.context)
    private val binding = GlassMenuBinding.inflate(inflater)
    private val column = binding.glassMenuColumn

    private val popup = PopupWindow(
        binding.root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true
    ).apply {
        // the window itself stays out of sight: the panel inside it is what is seen and what casts
        // the shadow. An outside tap only dismisses a popup that has a background of some kind
        setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        isOutsideTouchable = true
    }

    // the toolbar builds its three dots lazily and rebuilds them when the menu changes, so which
    // view they are is worth looking at again rather than resolving once
    private var boundOverflow: View? = null

    /**
     * Whether the three dots are on the bar at all. A screen already wearing a pill that opens this
     * same menu has no use for them: two doors onto one drop-down is one too many.
     */
    var isOnToolbar = true
        set(value) {
            field = value
            boundOverflow?.beVisibleIf(value)
        }

    private val gap = resources.getDimensionPixelSize(R.dimen.glass_menu_drop_gap)
    private val room = resources.getDimensionPixelSize(R.dimen.glass_menu_shadow_room)
    private val margin = resources.getDimensionPixelSize(R.dimen.glass_menu_screen_margin)

    // what the drop-down last opened from, and whether it opened upward out of it
    private var anchor: View? = null
    private var dropUp = false

    // whether the section the spec keeps back is up, and whether the arrow saying so has a turn to
    // make - it has one only when a tap put it there, never when the menu is built for the first time
    private var expanded = false
    private var turningExpander = false

    // the kept-back section's rows, built whether or not they are up: the panel is measured for them
    // so that revealing them changes its height and nothing else
    private var hiddenRows = emptyList<View>()

    // how much of the screen was left the way it opens, when it last opened
    private var roomForPanel = 0

    private fun attach(openedBy: View?) {
        binding.glassMenuPanel.apply {
            cornerRadius = resources.getDimension(R.dimen.glass_menu_corner_radius)
            blurRadius = Glass.DEFAULT_RADIUS
            overlayAlpha = Glass.TEXT_TINT_ALPHA
            elevation = resources.getDimension(R.dimen.floating_chrome_elevation)
            // so a tap that lands between rows stops at the panel rather than counting as a miss
            isClickable = true
        }

        // a tap on the room left for the shadow is a tap outside the panel as far as anyone can tell
        binding.glassMenuFrame.setOnClickListener { popup.dismiss() }

        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            // a popup outlives the screen that put it up, and takes the window down with it
            override fun onViewDetachedFromWindow(view: View) = popup.dismiss()
        })

        // a pill at the foot of the screen has nothing but the screen above it to open into
        openedBy?.setOnClickListener { show(it, dropUp = true) }
    }

    private fun attachToToolbar(toolbar: Toolbar, alsoOpenedBy: View?) {
        attach(alsoOpenedBy)
        toolbar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> bindOverflow(toolbar) }
        bindOverflow(toolbar)
    }

    /**
     * Points the three dots at this menu instead. They are the only [ImageView] the toolbar's action
     * menu holds - every item shown as a button of its own is a text view wearing its icon.
     */
    private fun bindOverflow(toolbar: Toolbar) {
        val overflow = toolbar.actionMenu()?.children?.firstOrNull { it is ImageView } ?: return
        if (overflow !== boundOverflow) {
            boundOverflow = overflow
            // the platform puts its own popup up from both of these
            overflow.setOnTouchListener(null)
            overflow.setOnClickListener { show(overflow, dropUp = false) }
        }

        // reapplied rather than set once: the toolbar builds its dots back on every menu change
        overflow.beVisibleIf(isOnToolbar)
    }

    private fun show(anchor: View, dropUp: Boolean) {
        this.anchor = anchor
        this.dropUp = dropUp
        // however it was left last time, a drop-down opens compressed
        expanded = false
        turningExpander = false
        fill()
        binding.glassMenuPanel.frost(contentBehind)

        // what is left of the window the way the drop-down opens, which a popup left to size itself
        // would simply hang off the end of rather than scroll inside. Measured within the window
        // rather than on the screen, the height it is taken from being the window's: one the system
        // has put anywhere but the top of the display - split screen, freeform - has the two
        // disagreeing by however far down it starts, and the drop-down opening as an empty sliver
        val anchorTop = IntArray(2).also { anchor.getLocationInWindow(it) }[1]
        val systemBars = ViewCompat.getRootWindowInsets(anchor)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        roomForPanel = if (dropUp) {
            anchorTop - (systemBars?.top ?: 0) - gap - margin
        } else {
            anchor.rootView.height - (systemBars?.bottom ?: 0) - anchorTop - anchor.height - gap - margin
        }

        // offsets and size are the popup's, and the panel sits [room] inside it on every side
        popup.width = column.widestMenuRow(hiddenRows) + room * 2
        popup.height = fittedHeight()
        popup.showAsDropDown(anchor, room - margin, verticalOffset(anchor, popup.height), Gravity.END)

        // the frost is a copy taken while the content behind draws, and nothing back there has any
        // reason to draw again once a popup is up over it
        binding.glassMenuPanel.post { contentBehind.invalidate() }
    }

    /** How far below the anchor's own foot the panel is dropped, which for a drop-up is above it. */
    private fun verticalOffset(anchor: View, height: Int) = if (dropUp) {
        room - gap - anchor.height - height
    } else {
        gap - room
    }

    /** Fills the panel with the top level of the menu, in the sections the spec asks for. */
    private fun fill() {
        // taken out of as they are placed, so what is left over is what the spec forgot
        val available = items()
        val spec = spec()

        // the kept-back section is built first, so that what is left for the shown sections is what
        // the spec did not name at all rather than what it put behind the arrow
        hiddenRows = spec.hidden.mapNotNull { buildEntry(it, available) }
        val sections = spec.sections.map { entries ->
            entries.mapNotNull { buildEntry(it, available) }
        }.toMutableList()

        // nothing the spec never named may go missing along the way, so leftovers join the last shown
        // section rather than the kept-back one - an item forgotten here is still one tap away
        if (available.isNotEmpty()) {
            sections[sections.lastIndex] = sections.last() + available.values.map { row(it) }
        }

        // an expander with nowhere to hang gives up and shows everything, rather than stranding a
        // section behind an arrow that was never drawn
        val shown = sections.flatten()
        val expander = hiddenRows.isNotEmpty() &&
            shown.lastOrNull()?.attachMenuExpander(expanded, turningExpander, ::toggleExpanded) == true

        turningExpander = false
        draw(if (expanded || !expander) sections + listOf(hiddenRows) else sections)
    }

    /** Reveals the kept-back section, or puts it away again. */
    private fun toggleExpanded() {
        expanded = !expanded
        turningExpander = true
        fill()

        // a panel too tall for the room it has scrolls inside a fixed height, and would otherwise
        // answer the tap by growing entirely below its own foot
        if (expanded) {
            binding.glassMenuScroller.post { binding.glassMenuScroller.fullScroll(View.FOCUS_DOWN) }
        }
    }

    /** Fills the panel with one submenu, with the way back out of it above. */
    private fun drillInto(parent: MenuItem, submenu: Menu) {
        val back = inflater.menuBackRow(column, parent) { fill() }
        val items = submenu.children.filter { it.isVisible }.map { row(it) }.toList()
        draw(listOf(listOf(back), items))
    }

    /** Lays [sections] out, dropping the empty ones along with the rule that would have led them. */
    private fun draw(sections: List<List<View>>) {
        column.removeAllViews()
        sections.filter { it.isNotEmpty() }.forEachIndexed { index, views ->
            if (index > 0) {
                column.addView(menuDivider(column.context))
            }

            views.forEach { column.addView(it) }
        }

        // the panel is already up when a submenu is opened, and its window has to follow it
        if (popup.isShowing) {
            val height = fittedHeight()
            val anchor = anchor
            if (dropUp && anchor != null) {
                // a popup keeps the corner it was placed by, so one growing upward has to be moved
                // as well as resized, or it would reach back down over what it opened from
                popup.update(anchor, room - margin, verticalOffset(anchor, height), popup.width, height)
            } else {
                popup.update(popup.width, height)
            }
        }
    }

    private fun fittedHeight() =
        column.menuHeightAt(popup.width - room * 2, roomForPanel - room * 2) + room * 2

    private fun buildEntry(entry: MenuEntry, available: MutableMap<Int, MenuItem>) = when (entry) {
        is MenuEntry.Row -> available.remove(entry.id)?.let { row(it) }

        is MenuEntry.Icons -> entry.icons
            .mapNotNull { icon -> available.remove(icon.id)?.let { icon to it } }
            .takeIf { it.isNotEmpty() }
            ?.let { inflater.menuIconRow(column, it, ::select) }
    }

    private fun row(item: MenuItem) = inflater.menuRow(column, item) { picked ->
        val submenu = picked.subMenu
        if (submenu != null) drillInto(picked, submenu) else select(picked)
    }

    /**
     * Picked through the menu the item came from, so the screen's own listener answers it and needs
     * to know nothing about where it was tapped.
     */
    private fun select(item: MenuItem) {
        popup.dismiss()
        onPick(item)
    }
}
