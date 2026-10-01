package org.fossify.gallery.helpers

import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import org.fossify.gallery.R
import org.fossify.gallery.databinding.BottomActionsBinding
import org.fossify.commons.R as commonsR

/**
 * Where a viewer's bottom action bar sits: along the foot of the screen, or - in the landscape
 * layout, where height is what there is least of - in the top bar's row, between the heading and the
 * toolbar's own buttons, standing a little closer together than along the foot.
 *
 * The bar is moved rather than doubled, so its buttons keep the listeners, holds and icons the viewer
 * gave them wherever it is.
 */
class BottomActionsPlacement(
    bar: BottomActionsBinding,
    private val topRow: Toolbar,
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

    // narrower buttons give up padding rather than icon, so the icons stay the size they are at the foot
    private val topRowPadding = margin - (footWidth - topRowWidth) / 2

    private var navigationBarHeight = 0

    /** Whether the bar is up in the top row rather than along the foot. */
    var isInTopRow = false
        private set

    init {
        // clear of the navigation bar along the foot, and nothing of the kind up in the top row
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            navigationBarHeight = insets.getInsetsIgnoringVisibility(Type.systemBars()).bottom
            updatePadding()
            insets
        }
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
        } else {
            home.addView(root, homeIndex, homeParams)
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

    private fun updatePadding() {
        root.updatePadding(
            top = if (isInTopRow) 0 else homePaddingTop,
            bottom = if (isInTopRow) 0 else navigationBarHeight
        )
    }
}
