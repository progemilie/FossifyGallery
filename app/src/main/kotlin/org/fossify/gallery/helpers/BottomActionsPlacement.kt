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
 * Moves a viewer's bottom action bar between the foot and, in the landscape layout, the top row beside
 * the toolbar's own buttons. Up there, buttons that don't fit beside the heading are squeezed out from
 * the end of the bar's order and listed in [overflowed].
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

    // narrower buttons give up padding, keeping their icons' size
    private val topRowPadding = margin - (footWidth - topRowWidth) / 2

    private var navigationBarHeight = 0

    private var orderedButtons = emptyArray<BottomActionButton>()
    private var orderedActions = IntArray(0)

    // fitted before drawing, so no frame shows buttons over the heading
    private val fitter = ViewTreeObserver.OnPreDrawListener { fitTopRow() }

    var isInTopRow = false
        private set

    /** Actions squeezed out of the top row, as Config.visibleBottomActions bits. */
    var overflowed = 0
        private set

    init {
        // padded clear of the navigation bar only along the foot
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            navigationBarHeight = insets.getInsetsIgnoringVisibility(Type.systemBars()).bottom
            updatePadding()
            insets
        }
    }

    /** Also the order the top row squeezes buttons out from the end of. */
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
            // ahead of the heading, which takes all the room left when the toolbar measures it
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
     * Squeezes buttons out, or lets them back, so the heading keeps its minimum width. The heading and
     * the bar share the same room however many are up. Answers whether the frame can be drawn.
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
                // hidden by the viewer, not by the row
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
