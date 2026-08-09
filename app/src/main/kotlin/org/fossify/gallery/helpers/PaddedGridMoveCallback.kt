package org.fossify.gallery.helpers

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.interfaces.ItemMoveCallback
import org.fossify.commons.interfaces.ItemTouchHelperContract

/**
 * Drops an item where the grid is rather than where its padding is.
 *
 * Both grids keep their first row clear of the floating search bar by padding rather than by
 * starting below it (see [FloatingTopBar]), and that padding breaks the stock drag. Every swap ends
 * in `onMoved()`, which asks [LinearLayoutManager.prepareForDrop] to hold the item being swapped
 * with where it is - and that hands the item's current top straight to
 * [LinearLayoutManager.scrollToPositionWithOffset], which measures from *below* the padding. The
 * two disagree by exactly the padding, so every swap shoves the list down by it. Only at the very
 * top of the list does the layout pass close the gap that opens above the first row, which is why
 * the drag survives there and nowhere else.
 *
 * The same pinning with the padding taken off. Sideways scrolling measures from the left padding,
 * which nothing here sets, so that is left to the original.
 */
open class PaddedGridMoveCallback(
    adapter: ItemTouchHelperContract,
    allowHorizontalDrag: Boolean = false
) : ItemMoveCallback(adapter, allowHorizontalDrag) {

    override fun onMoved(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        fromPos: Int,
        target: RecyclerView.ViewHolder,
        toPos: Int,
        x: Int,
        y: Int
    ) {
        val grid = recyclerView.layoutManager as? LinearLayoutManager
        if (grid == null || grid.orientation != RecyclerView.VERTICAL || grid.reverseLayout) {
            super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y)
            return
        }

        // the dragged item takes the room the target gives up: dragging up it lands on the target's
        // top edge, dragging down it ends on its bottom one
        val landsAt = if (fromPos > toPos) {
            grid.getDecoratedTop(target.itemView)
        } else {
            grid.getDecoratedBottom(target.itemView) - grid.decoratedHeightOf(viewHolder.itemView)
        }

        grid.scrollToPositionWithOffset(toPos, landsAt - recyclerView.paddingTop)
    }

    private fun RecyclerView.LayoutManager.decoratedHeightOf(itemView: View): Int {
        val params = itemView.layoutParams as RecyclerView.LayoutParams
        return getDecoratedMeasuredHeight(itemView) + params.topMargin + params.bottomMargin
    }
}
