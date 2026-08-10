package org.fossify.gallery.adapters

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.gallery.helpers.REVEAL_DURATION_MS
import org.fossify.gallery.helpers.REVEAL_START_SCALE
import org.fossify.gallery.models.Medium

/**
 * Where a media grid is looking: scrolling an item into view, growing the one the viewer was just
 * left from, and carrying a place across the reload that replaces the adapter holding it.
 *
 * Kept out of [MediaAdapter], whose job is binding items rather than driving the view they are
 * bound into.
 */
class MediaGridNavigator(private val adapter: MediaAdapter) {
    private val recyclerView get() = adapter.recyclerView

    private var revealAnimator: Animator? = null

    /** A place in the grid, as [currentPosition] takes it and [restore] puts it back. */
    data class GridPosition(val path: String, val offset: Int)

    /**
     * Scrolls the item at [path] into view if needed and lets it grow into place, so it is obvious
     * which thumbnail the fullscreen viewer was just left from. Returns false if the item is not in
     * the grid (deleted, filtered out), letting the caller retry after a refresh.
     */
    fun revealItem(path: String): Boolean {
        val position = adapter.getItemKeyPosition(path.hashCode())
        if (position == -1) {
            return false
        }

        // scroll on the next pass so the grid is laid out, wait a further one only if it did scroll
        recyclerView.post {
            if (scrollIntoView(position)) {
                recyclerView.post { scaleItemIn(position) }
            } else {
                scaleItemIn(position)
            }
        }

        return true
    }

    /** Centres the item at [position] unless it is already fully on screen. Returns whether it scrolled. */
    fun scrollIntoView(position: Int): Boolean {
        val layoutManager = recyclerView.layoutManager as? MyGridLayoutManager ?: return false
        val isHorizontal = layoutManager.orientation == RecyclerView.HORIZONTAL
        val itemView = layoutManager.findViewByPosition(position)
        if (itemView != null && isFullyVisible(itemView, isHorizontal)) {
            return false
        }

        val available = if (isHorizontal) {
            recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight
        } else {
            recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
        }

        val itemSize = getItemSize(isHorizontal) ?: (available / layoutManager.spanCount)
        layoutManager.scrollToPositionWithOffset(position, ((available - itemSize) / 2).coerceAtLeast(0))
        return true
    }

    /** Sends the grid to one of its ends, whichever [toTop] asks for. */
    fun scrollToEdge(toTop: Boolean) {
        val layoutManager = recyclerView.layoutManager as? MyGridLayoutManager ?: return
        if (toTop) {
            layoutManager.scrollToPositionWithOffset(0, 0)
        } else {
            layoutManager.scrollToPosition(adapter.media.lastIndex)
        }
    }

    /**
     * Where the grid is sitting, as the item at the top of it and how far that item has been
     * scrolled past the top edge. Taken by path rather than by position so it survives a reload
     * that regroups the list, and handed back rather than kept because a reload builds the new
     * adapter that has to act on it.
     */
    fun currentPosition(): GridPosition? {
        val layoutManager = recyclerView.layoutManager as? MyGridLayoutManager ?: return null
        val position = layoutManager.findFirstVisibleItemPosition()
        val itemView = layoutManager.findViewByPosition(position) ?: return null
        val path = (adapter.media.getOrNull(position) as? Medium)?.path ?: return null
        // measured from below the padding, which is where scrollToPositionWithOffset() measures to
        val offset = if (layoutManager.orientation == RecyclerView.HORIZONTAL) {
            layoutManager.getDecoratedLeft(itemView) - recyclerView.paddingLeft
        } else {
            layoutManager.getDecoratedTop(itemView) - recyclerView.paddingTop
        }

        return GridPosition(path, offset)
    }

    /** Puts the grid back where [gridPosition] was taken from, if that item is still in it. */
    fun restore(gridPosition: GridPosition) {
        val layoutManager = recyclerView.layoutManager as? MyGridLayoutManager ?: return
        val position = adapter.getItemKeyPosition(gridPosition.path.hashCode())
        if (position != -1) {
            layoutManager.scrollToPositionWithOffset(position, gridPosition.offset)
        }
    }

    /** A view recycled mid reveal would go back into the grid still part grown. */
    fun cancelReveal() = revealAnimator?.cancel()

    /** The whole item, not just its thumbnail, so the badges over the photo come up with it. */
    private fun scaleItemIn(position: Int) {
        val itemView = recyclerView.findViewHolderForAdapterPosition(position)?.itemView ?: return

        revealAnimator?.cancel()
        revealAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(itemView, View.SCALE_X, REVEAL_START_SCALE, 1f),
                ObjectAnimator.ofFloat(itemView, View.SCALE_Y, REVEAL_START_SCALE, 1f)
            )
            duration = REVEAL_DURATION_MS
            interpolator = DecelerateInterpolator()
            // runs on a cancel too, so a reveal cut short cannot leave the item part grown
            doOnEnd {
                itemView.scaleX = 1f
                itemView.scaleY = 1f
                revealAnimator = null
            }

            start()
        }
    }

    private fun isFullyVisible(itemView: View, isHorizontal: Boolean) = if (isHorizontal) {
        itemView.left >= recyclerView.paddingLeft && itemView.right <= recyclerView.width - recyclerView.paddingRight
    } else {
        itemView.top >= recyclerView.paddingTop && itemView.bottom <= recyclerView.height - recyclerView.paddingBottom
    }

    // all media items share a size, section titles do not, so measure any visible medium
    private fun getItemSize(isHorizontal: Boolean): Int? {
        return recyclerView.children
            .firstOrNull {
                val position = recyclerView.getChildAdapterPosition(it)
                position != RecyclerView.NO_POSITION && !adapter.isASectionTitle(position)
            }?.let {
                if (isHorizontal) it.width else it.height
            }
    }
}
