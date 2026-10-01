package org.fossify.gallery.helpers

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.children
import androidx.core.view.doOnAttach
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import org.fossify.gallery.R
import org.fossify.gallery.databinding.BottomActionsBinding
import org.fossify.gallery.views.BottomActionButton
import org.fossify.commons.R as commonsR

/**
 * Where a viewer's bottom action bar sits: along the foot of the screen, or - in the landscape
 * layout, where height is what there is least of - in the top bar's row, between the heading and the
 * toolbar's own buttons, standing a little closer together than along the foot.
 *
 * The bar is moved rather than doubled, so its buttons keep the listeners, holds and icons the viewer
 * gave them wherever it is.
 *
 * Up in the top row it is only as wide as the heading leaves it: buttons are squeezed out from the
 * end of the bar's order until the heading keeps [R.dimen.viewer_top_row_min_title_width], and the
 * actions squeezed out are [overflowed] into the three dots' menu, which the viewer is told about.
 */
class BottomActionsPlacement(
    private val bar: BottomActionsBinding,
    private val topRow: Toolbar,
    private val title: View,
    private val onOverflowChanged: () -> Unit,
) {
    private val root = bar.root
    private val home = root.parent as ViewGroup
    private val homeIndex = home.indexOfChild(root)
    private val homeParams = root.layoutParams
    private val homeBackground = root.background
    private val homeMinHeight = root.minHeight
    private val homePaddingTop = root.paddingTop

    private val margin = root.resources.getDimensionPixelSize(commonsR.dimen.normal_margin)
    private val footWidth = root.resources.getDimensionPixelSize(commonsR.dimen.list_touch_target_min)
    private val topRowWidth = root.resources.getDimensionPixelSize(R.dimen.viewer_top_row_action_width)
    private val minTitleWidth = root.resources.getDimensionPixelSize(R.dimen.viewer_top_row_min_title_width)

    // narrower buttons give up padding rather than icon, so the icons stay the size they are at the foot
    private val topRowPadding = margin - (footWidth - topRowWidth) / 2

    private var navigationBarHeight = 0

    // the bar's buttons in its order, and the action each one is
    private var orderedButtons = emptyArray<BottomActionButton>()
    private var orderedActions = IntArray(0)

    // fitted before every frame is drawn, so the row never shows buttons over the heading even for one
    private val fitter = ViewTreeObserver.OnPreDrawListener { fitTopRow() }

    /** Whether the bar is up in the top row rather than along the foot. */
    var isInTopRow = false
        private set

    /** The actions the top row has no room for, in the bits of Config.visibleBottomActions. */
    var overflowed = 0
        private set

    init {
        // clear of the navigation bar along the foot, and nothing of the kind up in the top row
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            navigationBarHeight = insets.getInsetsIgnoringVisibility(Type.systemBars()).bottom
            updatePadding()
            insets
        }
    }

    /**
     * Lays the buttons out in [order], see applyBottomActionsOrder. The top row squeezes buttons out
     * from the end of it.
     */
    fun applyOrder(order: List<Int>) {
        bar.applyBottomActionsOrder(order)
        val byAction = ALL_BOTTOM_ACTIONS.associateBy { it.id }
        val ordered = order.mapNotNull { action ->
            byAction[action]?.let { action to root.findViewById<BottomActionButton>(it.viewId) }
        }

        orderedActions = ordered.map { it.first }.toIntArray()
        orderedButtons = ordered.map { it.second }.toTypedArray()
    }

    fun placeInTopRow(inTopRow: Boolean) {
        if (inTopRow == isInTopRow) {
            return
        }

        isInTopRow = inTopRow
        (root.parent as? ViewGroup)?.removeView(root)
        if (inTopRow) {
            // ahead of the heading: the toolbar measures its own views in order, and the heading takes
            // all of the room left when it gets there
            val params = Toolbar.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            )
            topRow.addView(root, 0, params)
            topRow.doOnAttach { it.viewTreeObserver.addOnPreDrawListener(fitter) }
        } else {
            home.addView(root, homeIndex, homeParams)
            topRow.viewTreeObserver.removeOnPreDrawListener(fitter)
            unsqueezeAll()
        }

        root.background = if (inTopRow) null else homeBackground
        root.minHeight = if (inTopRow) 0 else homeMinHeight
        root.minimumHeight = root.minHeight
        updatePadding()
        root.children.forEach { button ->
            val padding = if (inTopRow) topRowPadding else margin
            button.setPadding(padding, margin, padding, margin)
            button.updateLayoutParams<ConstraintLayout.LayoutParams> {
                width = if (inTopRow) topRowWidth else footWidth
                bottomMargin = if (inTopRow) 0 else margin
            }
        }
    }

    /**
     * Squeezes out whatever the heading needs the room of, and lets back in whatever it no longer
     * does. The heading and the bar share all of the toolbar the toolbar's own views leave, however
     * many buttons are up, so their two widths together are the room to share out. Answers whether
     * the frame can be drawn as laid out.
     */
    private fun fitTopRow(): Boolean {
        if (!isInTopRow || !topRow.isLaidOut || root.visibility != View.VISIBLE) {
            return true
        }

        var room = title.width + root.width - minTitleWidth
        var squeezedOut = 0
        var isRowChanged = false
        for (i in orderedButtons.indices) {
            val button = orderedButtons[i]
            if (button.wantedVisibility != View.VISIBLE) {
                // not up for this file at all, which is the viewer's business rather than the row's
                button.isSqueezedOut = false
                continue
            }

            val fits = room >= topRowWidth
            if (fits) {
                room -= topRowWidth
            } else {
                squeezedOut = squeezedOut or orderedActions[i]
            }

            if (button.isSqueezedOut == fits) {
                button.isSqueezedOut = !fits
                isRowChanged = true
            }
        }

        if (squeezedOut != overflowed) {
            overflowed = squeezedOut
            onOverflowChanged()
        }

        return !isRowChanged
    }

    private fun unsqueezeAll() {
        orderedButtons.forEach { it.isSqueezedOut = false }
        if (overflowed != 0) {
            overflowed = 0
            onOverflowChanged()
        }
    }

    private fun updatePadding() {
        root.updatePadding(
            top = if (isInTopRow) 0 else homePaddingTop,
            bottom = if (isInTopRow) 0 else navigationBarHeight
        )
    }
}
