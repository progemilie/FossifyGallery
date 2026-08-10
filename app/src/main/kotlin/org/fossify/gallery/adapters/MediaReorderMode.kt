package org.fossify.gallery.adapters

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.interfaces.ItemTouchHelperContract
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.DRAG_BORDER_WIDTH_FRACTION
import org.fossify.gallery.helpers.DRAG_LIFT_DURATION_MS
import org.fossify.gallery.helpers.DRAG_LIFT_SCALE
import org.fossify.gallery.helpers.PaddedGridMoveCallback
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import java.util.Collections

/**
 * Drag-to-arrange over a media grid: what is marked, what a drag is carrying and how a picked up
 * thumbnail looks while it travels. The grid this rearranges is [MediaAdapter]'s own list, which is
 * why this drives that adapter rather than living inside it - binding thumbnails and arranging them
 * by hand are two jobs, and only one of them is upstream's.
 *
 * While the mode is on, the two gestures the action mode normally owns mean something else: a tap
 * marks an item to travel with the next drag, a long press picks it up.
 */
// a drag is a long conversation - picked up, carried, dropped, put back - and every step of it is
// a couple of lines that only makes sense next to the ones around it
@Suppress("TooManyFunctions")
class MediaReorderMode(private val adapter: MediaAdapter) : ItemTouchHelperContract {
    private val activity get() = adapter.activity
    private val recyclerView get() = adapter.recyclerView
    private val media get() = adapter.media

    var isActive = false
        private set

    private var itemTouchHelper: ItemTouchHelper? = null

    // paths ticked off while reordering, kept in the order they were ticked - a separate set from
    // the adapter's selectedKeys, which belongs to the action mode and has no business being up
    // mid arrangement
    private val markedPaths = LinkedHashSet<String>()

    // what the drag under way is carrying: the marked items in the order they were picked up, the
    // dragged one among them. empty while a lone item is being dragged
    private var carriedItems = emptyList<Medium>()
    private var draggedPath: String? = null

    private var dragLiftAnimator: Animator? = null

    /** Tells how many items are marked, so the reorder bar can enable what acts on them. */
    var onSelectionChanged: ((marked: Int) -> Unit)? = null

    /**
     * Swaps the grid over to [newMedia] - which the caller flattens, sections cannot take part in a
     * hand made order - and lets a long press start a drag rather than a selection. Leaving the
     * mode restores whatever list the caller passes back in, or keeps the dragged one when the
     * caller passes none.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun setActive(reordering: Boolean, newMedia: ArrayList<ThumbnailItem>? = null) {
        isActive = reordering
        carriedItems = emptyList()
        draggedPath = null
        markedPaths.clear()
        notifySelection()
        if (reordering) {
            adapter.finishActMode()
            if (itemTouchHelper == null) {
                itemTouchHelper = ItemTouchHelper(NearestCellMoveCallback(this))
            }
            itemTouchHelper?.attachToRecyclerView(recyclerView)
        } else {
            itemTouchHelper?.attachToRecyclerView(null)
        }

        adapter.replaceMedia(newMedia)
        adapter.notifyDataSetChanged()
    }

    /** Whether [medium] is one of the items the next drag will carry. */
    fun isMarked(medium: Medium) = markedPaths.contains(medium.path)

    /**
     * Takes over the item's gestures for the length of the mode. Only called for a view the adapter
     * has just bound without its usual click and long press listeners, so nothing is being replaced.
     */
    fun bindItemGestures(holder: MyRecyclerViewAdapter.ViewHolder, medium: Medium) {
        holder.itemView.setOnClickListener { toggleMark(medium, holder) }
        holder.itemView.setOnLongClickListener {
            startDragging(medium, holder)
            true
        }
    }

    /**
     * Sends every marked item to one end of the grid at once, as an alternative to dragging a group
     * the length of a long folder. The marked items keep the order they are in on the grid rather
     * than the order they were ticked in, which is what dragging a group does too.
     *
     * The grid is left showing them: after the move the items are at an end the view is as likely as
     * not nowhere near, and a move nothing visibly happened after reads as a move that did nothing.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun moveSelectionToEdge(toTop: Boolean) {
        if (!isActive || markedPaths.isEmpty()) {
            return
        }

        val marked = media.filterIsInstance<Medium>().filter { isMarked(it) }
        if (marked.isEmpty()) {
            return
        }

        val movedPaths = marked.mapTo(HashSet()) { it.path }
        media.removeAll { it is Medium && movedPaths.contains(it.path) }
        media.addAll(if (toTop) 0 else media.size, marked)
        adapter.notifyDataSetChanged()
        adapter.gridNavigator.scrollToEdge(toTop)
    }

    /** The arrangement as it stands, for saving. */
    fun orderedPaths(): List<String> {
        // a save that lands while a drag is still carrying items must not go out without them
        dropCarriedItems()
        return media.mapNotNull { (it as? Medium)?.path }
    }

    /** A view let go of mid drag would come back to another item still lifted and still ringed. */
    fun resetItemState(itemView: View) {
        itemView.scaleX = 1f
        itemView.scaleY = 1f
        itemView.translationZ = 0f
        // the tick is put back on every bind, the count of a carried group is not
        itemView.findCountBadge()?.beGone()
        itemView.findThumbnail()?.foreground = null
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(media, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(media, i, i - 1)
            }
        }

        adapter.notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: MyRecyclerViewAdapter.ViewHolder?) {
        adapter.swipeRefreshLayout?.isEnabled = false
        myViewHolder?.itemView?.liftForDrag()
    }

    override fun onRowClear(myViewHolder: MyRecyclerViewAdapter.ViewHolder?) {
        adapter.swipeRefreshLayout?.isEnabled = !isActive && activity.config.enablePullToRefresh
        myViewHolder?.itemView?.dropAfterDrag()
        dropCarriedItems()
        notifySelection()
    }

    /**
     * Repaints only the check rather than rebinding the item - a rebind would put the thumbnail
     * through another image request, and this runs on every tap.
     */
    private fun toggleMark(medium: Medium, holder: MyRecyclerViewAdapter.ViewHolder) {
        if (!markedPaths.remove(medium.path)) {
            markedPaths.add(medium.path)
        }

        adapter.repaintSelection(holder.itemView, medium)
        notifySelection()
    }

    /**
     * Picks up the item held down and, if it is one of several marked, every other marked item with
     * it. The others leave the grid for the length of the drag: the grid closes over the gaps they
     * leave and opens one where the group is headed, so what is on screen while dragging is what
     * the arrangement will look like rather than one item wandering through the old one.
     *
     * The group is taken out before the drag starts rather than after. ItemTouchHelper anchors the
     * dragged view to where it sat when it was picked up, so the item stays under the finger even
     * though the grid closes up underneath it.
     */
    private fun startDragging(medium: Medium, holder: MyRecyclerViewAdapter.ViewHolder) {
        draggedPath = medium.path
        carriedItems = if (markedPaths.size > 1 && isMarked(medium)) {
            media.filterIsInstance<Medium>().filter { isMarked(it) }
        } else {
            emptyList()
        }

        carriedItems.filter { it.path != medium.path }.forEach { removeItem(it.path) }
        notifySelection()
        itemTouchHelper?.startDrag(holder)
    }

    /**
     * Lands the carried items around the one just dropped, keeping the order they were picked up in
     * - the drag says where the group goes, not how it is shuffled. Whatever was ahead of the
     * dragged item in the group goes directly in front of it, whatever was behind goes directly
     * after, so the dropped item keeps the place the finger left it in among the items that stayed.
     */
    private fun dropCarriedItems() {
        val carried = carriedItems
        val droppedPath = draggedPath
        carriedItems = emptyList()
        draggedPath = null
        if (carried.size < 2 || droppedPath == null) {
            return
        }

        val droppedIndex = indexOfPath(droppedPath)
        if (droppedIndex == -1) {
            return
        }

        val offset = carried.indexOfFirst { it.path == droppedPath }
        carried.take(offset).forEachIndexed { index, item ->
            insertItem(item, droppedIndex + index)
        }

        carried.drop(offset + 1).forEachIndexed { index, item ->
            insertItem(item, droppedIndex + offset + 1 + index)
        }

        // items landing in front of the dropped one push the grid down while it holds its scroll on
        // what it was showing, which can leave the group that just landed above the fold
        if (offset > 0) {
            recyclerView.post {
                val landedAt = indexOfPath(carried.first().path)
                if (landedAt != -1) {
                    adapter.gridNavigator.scrollIntoView(landedAt)
                }
            }
        }
    }

    private fun removeItem(path: String) {
        val position = indexOfPath(path)
        if (position != -1) {
            media.removeAt(position)
            adapter.notifyItemRemoved(position)
        }
    }

    private fun insertItem(item: Medium, position: Int) {
        media.add(position, item)
        adapter.notifyItemInserted(position)
    }

    private fun indexOfPath(path: String) = media.indexOfFirst { (it as? Medium)?.path == path }

    private fun notifySelection() = onSelectionChanged?.invoke(markedPaths.size)

    /**
     * Pulls the picked up thumbnail out of the grid - smaller, ringed in the accent color and
     * casting a shadow into the gap that opens around it - so the moment the long press takes hold
     * and the item is free to be moved is unmistakable. A tap of feedback goes with it, the finger
     * is on the item and cannot see it.
     */
    private fun View.liftForDrag() {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        // ItemTouchHelper owns the elevation of whatever it drags, translationZ is ours to lift with
        outlineProvider = thumbnailOutlineProvider
        animateLift(DRAG_LIFT_SCALE, activity.resources.getDimension(R.dimen.drag_lift_elevation))
        showCarriedCount(carriedItems.size)

        findThumbnail()?.apply {
            foreground = buildAccentBorder(width)
        }
    }

    private fun View.dropAfterDrag() {
        // the ring and the shadow go at once rather than when the item has settled - carrying a
        // group re-lays the grid out on the drop, and a reset waiting on an animation that a
        // re-layout can cut short would leave the ring painted on the thumbnail for good
        outlineProvider = ViewOutlineProvider.BACKGROUND
        findThumbnail()?.foreground = null
        hideCarriedCount()
        animateLift(1f, 0f)
    }

    /**
     * The rest of a group being carried is off the grid, so the one thumbnail on its way counts the
     * whole group in the badge the tick came from - one item on the move has nothing to count and
     * keeps its tick.
     */
    private fun View.showCarriedCount(count: Int) {
        if (count < 2) {
            return
        }

        findCountBadge()?.apply {
            text = count.toString()
            background?.applyColorFilter(activity.getProperPrimaryColor())
            setTextColor(activity.getProperPrimaryColor().getContrastColor())
            beVisible()
        }

        findCheck()?.beGone()
    }

    private fun View.hideCarriedCount() {
        val badge = findCountBadge() ?: return
        if (badge.isVisible) {
            badge.beGone()
            // only a marked item is ever carried in a group, so its tick is due back
            findCheck()?.beVisible()
        }
    }

    /**
     * Animators of our own rather than the view's animate() builder: the grid's item animator uses
     * that builder for the items it slides around and cancels whatever it finds on it, which would
     * strand a picked up thumbnail half lifted.
     */
    private fun View.animateLift(scale: Float, elevation: Float) {
        dragLiftAnimator?.cancel()
        dragLiftAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(this@animateLift, View.SCALE_X, scale),
                ObjectAnimator.ofFloat(this@animateLift, View.SCALE_Y, scale),
                ObjectAnimator.ofFloat(this@animateLift, View.TRANSLATION_Z, elevation)
            )
            duration = DRAG_LIFT_DURATION_MS
            start()
        }
    }

    /**
     * An accent ring following the thumbnail's own corners, [DRAG_BORDER_WIDTH_FRACTION] of its
     * width so it stays in proportion whatever column count the grid is on.
     */
    private fun buildAccentBorder(thumbnailWidth: Int): GradientDrawable {
        val strokeWidth = (thumbnailWidth * DRAG_BORDER_WIDTH_FRACTION).coerceIn(
            activity.resources.getDimension(R.dimen.accent_border_min_width),
            activity.resources.getDimension(R.dimen.accent_border_max_width)
        )

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = adapter.thumbnailCornerRadius
            setStroke(strokeWidth.toInt(), activity.getProperPrimaryColor())
        }
    }

    /**
     * The item view carries the padding that spaces the grid out, so a shadow around it would sit
     * off the picture. This traces the thumbnail inside it instead, corners and all.
     */
    private val thumbnailOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(
                view.paddingLeft,
                view.paddingTop,
                view.width - view.paddingRight,
                view.height - view.paddingBottom,
                adapter.thumbnailCornerRadius
            )
        }
    }
}

private fun View.findThumbnail() = findViewById<ImageView>(R.id.medium_thumbnail)

private fun View.findCountBadge() = findViewById<TextView>(R.id.medium_count)

private fun View.findCheck() = findViewById<ImageView>(R.id.medium_check)

/**
 * The dragged item takes the cell it covers most, which is the head of the list handed over here -
 * ItemTouchHelper has already narrowed it to the cells the item overlaps and sorted them by how far
 * their centre is from its own.
 *
 * The default rule instead asks which cells lie between where the item is being drawn and the cell
 * it currently occupies. Those are the same place while a lone item is dragged, but picking up a
 * group takes the rest of it out of the grid, which slides the held item's cell out from under the
 * finger - and then that rule can find nothing between the two and the item stops responding. Going
 * by what is under the item needs no such agreement, and it drops where it looks like it will.
 */
private class NearestCellMoveCallback(contract: ItemTouchHelperContract) : PaddedGridMoveCallback(contract, true) {
    override fun chooseDropTarget(
        selected: RecyclerView.ViewHolder,
        dropTargets: MutableList<RecyclerView.ViewHolder>,
        curX: Int,
        curY: Int
    ) = dropTargets.firstOrNull() ?: super.chooseDropTarget(selected, dropTargets, curX, curY)
}
