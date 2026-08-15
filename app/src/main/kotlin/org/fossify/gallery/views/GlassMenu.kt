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
 * It is built from the toolbar's own [Menu] every time it opens and picked from through that same
 * menu, so everything a screen already does - hiding an item whose job a bottom action button has
 * taken, or one the current folder has no use for - carries over with nothing to wire up. Items the
 * toolbar is currently showing as buttons of its own are left out for the same reason.
 */
class GlassMenu private constructor(
    private val toolbar: Toolbar,
    private val spec: MenuSpec,
    private val contentBehind: ViewGroup,
) {
    companion object {
        /** Puts the drop-down on [toolbar]'s three dots. [contentBehind] is what it frosts. */
        fun replaceOverflow(toolbar: Toolbar, spec: MenuSpec, contentBehind: ViewGroup) {
            GlassMenu(toolbar, spec, contentBehind).attach()
        }
    }

    private val resources = toolbar.resources
    private val inflater = LayoutInflater.from(toolbar.context)
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

    // how much of the screen was left under the three dots when the drop-down last opened
    private var roomBelow = 0

    private fun attach() {
        binding.glassMenuPanel.apply {
            cornerRadius = resources.getDimension(R.dimen.glass_menu_corner_radius)
            blurRadius = Glass.DEFAULT_RADIUS
            overlayAlpha = Glass.TEXT_TINT_ALPHA
            elevation = resources.getDimension(R.dimen.glass_menu_elevation)
            // so a tap that lands between rows stops at the panel rather than counting as a miss
            isClickable = true
        }

        // a tap on the room left for the shadow is a tap outside the panel as far as anyone can tell
        binding.glassMenuFrame.setOnClickListener { popup.dismiss() }

        toolbar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> bindOverflow() }
        toolbar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            // a popup outlives the screen that put it up, and takes the window down with it
            override fun onViewDetachedFromWindow(view: View) = popup.dismiss()
        })

        bindOverflow()
    }

    /**
     * Points the three dots at this menu instead. They are the only [ImageView] the toolbar's action
     * menu holds - every item shown as a button of its own is a text view wearing its icon.
     */
    private fun bindOverflow() {
        val overflow = toolbar.actionMenu()?.children?.firstOrNull { it is ImageView } ?: return
        if (overflow === boundOverflow) {
            return
        }

        boundOverflow = overflow
        // the platform puts its own popup up from both of these
        overflow.setOnTouchListener(null)
        overflow.setOnClickListener { show(overflow) }
    }

    private fun show(anchor: View) {
        fill()
        binding.glassMenuPanel.frost(contentBehind)

        val gap = resources.getDimensionPixelSize(R.dimen.glass_menu_drop_gap)
        val room = resources.getDimensionPixelSize(R.dimen.glass_menu_shadow_room)
        val margin = resources.getDimensionPixelSize(R.dimen.glass_menu_screen_margin)

        // what is left of the screen under the three dots, which a popup left to size itself would
        // simply hang off the end of rather than scroll inside
        val anchorBottom = IntArray(2).also { anchor.getLocationOnScreen(it) }[1] + anchor.height
        val systemBars = ViewCompat.getRootWindowInsets(anchor)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
        roomBelow = anchor.rootView.height - systemBars - anchorBottom - gap - margin

        // offsets and size are the popup's, and the panel sits [room] inside it on every side
        popup.width = column.widestMenuRow() + room * 2
        popup.height = fittedHeight()
        popup.showAsDropDown(anchor, room - margin, gap - room, Gravity.END)

        // the frost is a copy taken while the content behind draws, and nothing back there has any
        // reason to draw again once a popup is up over it
        binding.glassMenuPanel.post { contentBehind.invalidate() }
    }

    /** Fills the panel with the top level of the menu, in the sections the spec asks for. */
    private fun fill() {
        // taken out of as they are placed, so what is left over is what the spec forgot
        val available = toolbar.overflowItems()
        val sections = spec.sections.map { entries ->
            entries.mapNotNull { buildEntry(it, available) }
        }.toMutableList()

        // nothing the spec never named may go missing along the way
        if (available.isNotEmpty()) {
            sections[sections.lastIndex] = sections.last() + available.values.map { row(it) }
        }

        draw(sections)
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
            popup.update(popup.width, fittedHeight())
        }
    }

    private fun fittedHeight(): Int {
        val room = resources.getDimensionPixelSize(R.dimen.glass_menu_shadow_room)
        return column.menuHeightAt(popup.width - room * 2, roomBelow - room * 2) + room * 2
    }

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
        toolbar.menu.performIdentifierAction(item.itemId, 0)
    }
}
