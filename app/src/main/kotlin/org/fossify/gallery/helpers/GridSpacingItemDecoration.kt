package org.fossify.gallery.helpers

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

class GridSpacingItemDecoration(
    val spanCount: Int, val spacing: Int, val isScrollingHorizontally: Boolean, val addSideSpacing: Boolean,
    var items: ArrayList<ThumbnailItem>, val useGridPosition: Boolean
) : RecyclerView.ItemDecoration() {

    private val insets = TileInsets(spanCount, spacing, isScrollingHorizontally, addSideSpacing)

    override fun toString() = "spanCount: $spanCount, spacing: $spacing, isScrollingHorizontally: $isScrollingHorizontally, addSideSpacing: $addSideSpacing, " +
        "items: ${items.hashCode()}, useGridPosition: $useGridPosition"

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (spacing <= 1) {
            return
        }

        val position = parent.getChildAdapterPosition(view)
        val medium = items.getOrNull(position) as? Medium ?: return
        val gridPositionToUse = if (useGridPosition) medium.gridPosition else position
        insets.compute(gridPositionToUse, position, useGridPosition)
        outRect.set(insets.left, insets.top, insets.right, insets.bottom)
    }
}

/**
 * The space [GridSpacingItemDecoration] leaves around one tile, worked out apart from it so that
 * [GridZoomLayout] can put a tile exactly where the grid would without a view to ask about.
 */
class TileInsets(
    private val spanCount: Int,
    private val spacing: Int,
    private val isScrollingHorizontally: Boolean,
    private val addSideSpacing: Boolean,
) {
    var left = 0
        private set
    var top = 0
        private set
    var right = 0
        private set
    var bottom = 0
        private set

    /** The space ahead of the tile and behind it, the way the grid scrolls and the way its spans run. */
    val alongBefore get() = if (isScrollingHorizontally) left else top
    val alongAfter get() = if (isScrollingHorizontally) right else bottom
    val acrossBefore get() = if (isScrollingHorizontally) top else left
    val acrossAfter get() = if (isScrollingHorizontally) bottom else right

    /** [gridPosition] counts from the tile's section when [useGridPosition], else it is [position]. */
    fun compute(gridPosition: Int, position: Int, useGridPosition: Boolean) {
        left = 0
        top = 0
        right = 0
        bottom = 0
        if (spacing <= 1) {
            return
        }

        val column = gridPosition % spanCount
        val before = column * spacing / spanCount
        val after = (column + 1) * spacing / spanCount
        if (isScrollingHorizontally) {
            if (addSideSpacing) {
                top = spacing - before
                bottom = after
                right = spacing
                if (position < spanCount) {
                    left = spacing
                }
            } else {
                top = before
                bottom = spacing - after
                if (position >= spanCount) {
                    left = spacing
                }
            }
        } else {
            if (addSideSpacing) {
                left = spacing - before
                right = after
                bottom = spacing
                if (position < spanCount && !useGridPosition) {
                    top = spacing
                }
            } else {
                left = before
                right = spacing - after
                if (gridPosition >= spanCount) {
                    top = spacing
                }
            }
        }
    }
}
