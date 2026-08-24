package org.fossify.gallery.views

import android.content.Context
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.children
import androidx.core.view.forEach
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.gallery.R
import org.fossify.gallery.databinding.GlassMenuIconBinding
import org.fossify.gallery.databinding.GlassMenuIconsBinding
import org.fossify.gallery.databinding.GlassMenuRowBinding
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.helpers.MenuIcon

/** How far the dotted rule is held back from the panel's full content colour. */
private const val DIVIDER_ALPHA = 0.3f

/** Which way the expander points, closed and open, and how long it takes to turn between them. */
private const val EXPANDER_DOWN = 0f
private const val EXPANDER_UP = 180f
private const val EXPANDER_TURN_MS = 150L

/**
 * The pieces [GlassMenu] is put together out of - what goes in it, what each item looks like and how
 * much room the lot of them want. Split off it to keep either side small.
 *
 * Everything drawn here is painted in the glass' own content colour on the spot rather than themed,
 * so a theme changed since the drop-down was last up is picked up on opening.
 */

/** The strip a toolbar lays its buttons and its three dots out in. */
internal fun Toolbar.actionMenu() = children.filterIsInstance<ActionMenuView>().firstOrNull()

/**
 * Every item a drop-down could draw, in menu order, keyed by id: what the screen has left visible,
 * less whatever the toolbar is already showing as a button of its own.
 */
internal fun Toolbar.overflowItems(): LinkedHashMap<Int, MenuItem> {
    val buttons = actionMenu()?.children.orEmpty()
    val onToolbar = buttons.map { it.id }.filterTo(mutableSetOf()) { it != View.NO_ID }

    val items = LinkedHashMap<Int, MenuItem>()
    menu.forEach { item ->
        if (item.isVisible && item.itemId !in onToolbar) {
            items[item.itemId] = item
        }
    }

    return items
}

/** A labelled item, wearing a chevron when it leads into a submenu. */
internal fun LayoutInflater.menuRow(
    parent: ViewGroup,
    item: MenuItem,
    onClick: (MenuItem) -> Unit,
): View {
    val row = GlassMenuRowBinding.inflate(this, parent, false)
    row.glassMenuRowLabel.text = item.title
    row.glassMenuRowLabel.setTextColor(Glass.contentColor(parent.context))
    row.glassMenuRowChevron.beVisibleIf(item.subMenu != null)
    row.glassMenuRowChevron.applyColorFilter(Glass.contentColor(parent.context))
    row.root.setOnClickListener { onClick(item) }
    return row.root
}

/** The heading of an open submenu, naming it and leading back out of it. */
internal fun LayoutInflater.menuBackRow(
    parent: ViewGroup,
    item: MenuItem,
    onBack: () -> Unit,
): View {
    val row = GlassMenuRowBinding.inflate(this, parent, false)
    row.glassMenuRowLabel.text = item.title
    row.glassMenuRowLabel.setTextColor(Glass.contentColor(parent.context))
    row.glassMenuRowBack.beVisible()
    row.glassMenuRowBack.applyColorFilter(Glass.contentColor(parent.context))
    row.root.setOnClickListener { onBack() }
    return row.root
}

/** Several items side by side, in place of a row each. */
internal fun LayoutInflater.menuIconRow(
    parent: ViewGroup,
    icons: List<Pair<MenuIcon, MenuItem>>,
    onSelect: (MenuItem) -> Unit,
): View {
    val row = GlassMenuIconsBinding.inflate(this, parent, false)
    icons.forEach { (icon, item) ->
        val view = GlassMenuIconBinding.inflate(this, row.root, false).root
        view.setImageResource(icon.icon)
        view.applyColorFilter(Glass.contentColor(parent.context))
        view.contentDescription = item.title
        TooltipCompat.setTooltipText(view, item.title)
        view.setOnClickListener { onSelect(item) }
        row.root.addView(view)
    }

    return row.root
}

/**
 * How wide the panel has to be to hold its widest item, up to the width the drop-down keeps within.
 *
 * Items are laid out at the panel's width rather than at their own, so each has to be asked what it
 * would like before there is a width to lay them out at. [alsoFitting] is measured along with them
 * without being in the column: a menu keeping a section back is sized for that section too, or
 * revealing it would shuffle the width of everything already on screen.
 */
internal fun ViewGroup.widestMenuRow(alsoFitting: List<View> = emptyList()): Int {
    val unbounded = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    var widest = 0
    (children + alsoFitting).forEach {
        it.measure(unbounded, unbounded)
        widest = maxOf(widest, it.measuredWidth)
    }

    return widest.coerceAtMost(resources.getDimensionPixelSize(R.dimen.glass_menu_max_width))
}

/**
 * How tall the panel wants to be once laid out [width] wide, up to the [limit] there is room for -
 * past which the drop-down is a fixed height with its items scrolling inside it.
 */
internal fun ViewGroup.menuHeightAt(width: Int, limit: Int): Int {
    measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )

    return measuredHeight.coerceIn(0, limit.coerceAtLeast(0))
}

/**
 * Puts the arrow that reveals a menu's kept-back section on [this] row, answering [onToggle]. False
 * from anything that is not a labelled row, which has nowhere to put one.
 *
 * The arrow takes its own touches, so the row it shares keeps working as the item it always was. It
 * is turned rather than swapped for a second drawable, which is what makes the flip animatable at
 * all: the row is built afresh on every toggle, so the new arrow starts at the old one's angle and
 * turns from there.
 */
internal fun View.attachMenuExpander(expanded: Boolean, turning: Boolean, onToggle: () -> Unit): Boolean {
    val arrow = findViewById<ImageView>(R.id.glass_menu_row_expand) ?: return false
    val rule = findViewById<View>(R.id.glass_menu_row_rule) ?: return false
    val color = Glass.contentColor(context)

    rule.beVisible()
    rule.setBackgroundColor(color.adjustAlpha(DIVIDER_ALPHA))

    arrow.beVisible()
    arrow.applyColorFilter(color)
    val title = context.getString(if (expanded) R.string.show_fewer_options else R.string.show_more_options)
    arrow.contentDescription = title
    TooltipCompat.setTooltipText(arrow, title)

    val facing = if (expanded) EXPANDER_UP else EXPANDER_DOWN
    if (turning) {
        arrow.rotation = if (expanded) EXPANDER_DOWN else EXPANDER_UP
        arrow.animate().rotation(facing).setDuration(EXPANDER_TURN_MS).start()
    } else {
        arrow.rotation = facing
    }

    arrow.setOnClickListener { onToggle() }
    return true
}

/** The dotted rule between two sections. */
internal fun menuDivider(context: Context) = DottedDivider(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        resources.getDimensionPixelSize(R.dimen.glass_menu_divider_height)
    )

    val padding = resources.getDimensionPixelSize(R.dimen.glass_menu_row_padding)
    setPadding(padding, 0, padding, 0)
    lineColor = Glass.contentColor(context).adjustAlpha(DIVIDER_ALPHA)
}
