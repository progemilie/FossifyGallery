package org.fossify.gallery.views

import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.gallery.R
import org.fossify.gallery.databinding.SelectionBottomPillBinding
import org.fossify.gallery.databinding.SelectionTopPillBinding
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.helpers.PanelPivot
import org.fossify.gallery.helpers.hidePanel
import org.fossify.gallery.helpers.showPanel

/**
 * The two frosted pills a selection is made through: what it holds at the top of the screen, what
 * can be done with it along the foot. Both are made of the same glass as the navigation pill they
 * stand in for, and neither pans away with the grid - a selection is not something a scroll should
 * take the way out of.
 *
 * This is the view half; what fills it comes from
 * [org.fossify.gallery.helpers.SelectionChrome], which reads the action mode.
 */
class SelectionPills(
    private val top: SelectionTopPillBinding,
    private val bottom: SelectionBottomPillBinding,
    private val contentBehind: ViewGroup,
) {
    private val context = top.root.context
    private val resources = context.resources

    /** What the drop-down carrying the rest of the actions hangs off. */
    val menuButton: View get() = bottom.selectionMore

    /** The way out of the selection. */
    var onBack: (() -> Unit)? = null

    /** A tap on the count, which is how everything is selected at once. */
    var onCountTapped: (() -> Unit)? = null

    /** Which actions the row is currently showing, so an unchanged row is not rebuilt under a tap. */
    private var shownIds = emptyList<Int>()

    init {
        listOf(top.selectionTopPanel, bottom.selectionBottomPanel).forEach { panel ->
            panel.blurRadius = Glass.DEFAULT_RADIUS
            // both are covered in icons and text, which have to read over whatever is scrolling past
            panel.overlayAlpha = Glass.TEXT_TINT_ALPHA
            panel.elevation = resources.getDimension(R.dimen.floating_chrome_elevation)
        }

        top.selectionTopPanel.cornerRadius = resources.getDimension(R.dimen.peek_pill_radius)
        bottom.selectionBottomPanel.cornerRadius = resources.getDimension(R.dimen.nav_pill_radius)

        top.selectionBack.setOnClickListener { onBack?.invoke() }
        top.selectionCount.setOnClickListener { onCountTapped?.invoke() }
    }

    val isShowing get() = top.root.isVisible

    fun show() {
        if (isShowing) {
            return
        }

        shownIds = emptyList()
        listOf(top.selectionTopPanel, bottom.selectionBottomPanel).forEach { it.frost(contentBehind) }
        updateColors()
        top.root.showPanel(pivot = PanelPivot.TOP)
        bottom.root.showPanel(pivot = PanelPivot.BOTTOM)
    }

    fun hide() {
        if (!isShowing) {
            return
        }

        top.root.hidePanel(pivot = PanelPivot.TOP)
        bottom.root.hidePanel(pivot = PanelPivot.BOTTOM)
    }

    /** What the selection holds, in whatever wording the action mode is keeping it in. */
    fun setCount(text: CharSequence?) {
        top.selectionCount.text = text
    }

    /**
     * Fills the row with [actions], and shows the menu segment where [hasMore] says there is more
     * behind it. Left alone where the same actions are already up: this is asked again on every
     * change to the selection, and rebuilding the row would cut short the feedback of a tap on it.
     */
    fun setActions(actions: List<MenuItem>, hasMore: Boolean, onPick: (MenuItem) -> Unit) {
        bottom.selectionMore.beVisibleIf(hasMore)
        val ids = actions.map { it.itemId }
        if (ids == shownIds) {
            return
        }

        shownIds = ids
        bottom.selectionActions.removeAllViews()
        actions.forEach { bottom.selectionActions.addView(actionButton(it, onPick)) }
        updateColors()
    }

    /** Repainted whenever the pills are shown: the theme can change while they are away. */
    fun updateColors() {
        val content = Glass.contentColor(context)
        top.selectionTopPanel.updateColors()
        bottom.selectionBottomPanel.updateColors()
        top.selectionBack.paintWash(content, isCurrent = false)
        top.selectionBackIcon.applyColorFilter(content)
        top.selectionCount.setTextColor(content)
        bottom.selectionMore.paintWash(content, isCurrent = false)
        bottom.selectionMoreIcon.applyColorFilter(content)
        bottom.selectionActions.children.forEach { paintAction(it, content) }
    }

    private fun actionButton(item: MenuItem, onPick: (MenuItem) -> Unit): View {
        val segment = NavPillSegment(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.selection_action_width),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = item.title
            setOnClickListener { onPick(item) }
        }

        // the label a row in the drop-down would have carried, since there is no room for one here
        TooltipCompat.setTooltipText(segment, item.title)
        val iconSize = resources.getDimensionPixelSize(R.dimen.nav_pill_icon_size)
        segment.addView(
            ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                setImageDrawable(item.icon?.mutate())
            }
        )

        return segment
    }

    private fun paintAction(view: View, content: Int) {
        (view as? NavPillSegment)?.paintWash(content, isCurrent = false)
        ((view as? ViewGroup)?.getChildAt(0) as? ImageView)?.applyColorFilter(content)
    }
}
