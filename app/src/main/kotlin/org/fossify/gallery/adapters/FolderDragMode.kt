package org.fossify.gallery.adapters

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.interfaces.ItemTouchHelperContract
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.DRAG_LIFT_SCALE
import org.fossify.gallery.helpers.FOLDER_DRAG_MOVE_THRESHOLD
import org.fossify.gallery.helpers.FOLDER_DROP_BORDER_FRACTION
import org.fossify.gallery.helpers.FOLDER_DROP_DWELL_MS
import org.fossify.gallery.helpers.FOLDER_DROP_TARGET_SCALE
import org.fossify.gallery.helpers.FOLDER_DROP_ZONE
import org.fossify.gallery.helpers.FOLDER_FLASH_BLINKS
import org.fossify.gallery.helpers.FOLDER_FLASH_DURATION_MS
import org.fossify.gallery.helpers.FOLDER_FLY_IN_DURATION_MS
import org.fossify.gallery.helpers.FOLDER_FLY_IN_SCALE
import org.fossify.gallery.helpers.FOLDER_HELD_OVER_SCALE
import org.fossify.gallery.helpers.FOLDER_LIFT_ALPHA
import org.fossify.gallery.helpers.PaddedGridMoveCallback
import org.fossify.gallery.helpers.animateDragLift
import org.fossify.gallery.helpers.dragAccentRing
import org.fossify.gallery.helpers.dragPictureOutline
import org.fossify.gallery.models.Directory

/**
 * Drag and drop over the folder grid: a tile let go of between two others arranges the grid, a tile
 * held over another goes into a folder group with it. This drives [DirectoryAdapter] rather than
 * living inside it - binding tiles and arranging them by hand are two jobs, and only one is
 * upstream's.
 *
 * Two rules keep the drops honest:
 * - **The grid holds still while a lifted tile sits on another one.** Nothing can be dropped onto a
 *   tile that steps aside as the finger arrives, so the middle of every tile ([FOLDER_DROP_ZONE]) is
 *   a place the arranging does not reach - and a tile has to travel most of its own width
 *   ([FOLDER_DRAG_MOVE_THRESHOLD]) before the grid shifts at all, or it would never get there.
 * - **Only a plain folder is ever carried into something.** Groups do not nest and two of them
 *   cannot be merged, so a lifted group tile can only be moved, never dropped in.
 */
@Suppress("TooManyFunctions")
class FolderDragMode(
    private val adapter: DirectoryAdapter,
    private val onOrderChanged: () -> Unit,
    private val onDroppedInto: (dragged: Directory, target: Directory) -> Unit
) : ItemTouchHelperContract {

    private val activity get() = adapter.activity
    private val recyclerView get() = adapter.recyclerView
    private val dirs get() = adapter.dirs

    /** Off while something else owns the grid: a search narrowing it, or the drag handles. */
    var isEnabled = true
        set(value) {
            field = value
            itemTouchHelper.attachToRecyclerView(if (value) recyclerView else null)
        }

    val isDragging get() = draggedPath != null

    /** Whether the lifted tile is sitting on another one, which holds the grid still under it. */
    val isHovering get() = hoveredTile != null

    /** Whether letting go now would put the lifted tile into the one under it rather than the grid. */
    val isDroppingIn get() = armedTile != null

    /** Whether the tile is placing itself, on its way into another one. */
    fun isFlyingIn(holder: RecyclerView.ViewHolder) = flyingView != null && flyingView === holder.itemView

    private val itemTouchHelper = ItemTouchHelper(FolderMoveCallback(this))
    private var draggedPath: String? = null
    private var flyingView: View? = null
    private var liftedView: View? = null
    private var didMove = false

    // the tile the lifted one is over, and the same tile once it has been held there long enough to
    // be dropped into. the grid does not shift under a lifted tile, so what is under the finger
    // stays what the finger arrived at
    private var hoveredTile: RecyclerView.ViewHolder? = null
    private var armedTile: RecyclerView.ViewHolder? = null
    private val dwell = Handler(Looper.getMainLooper())

    // the tile that flew into another one and the cell it left, held until the group it was dropped
    // into is either made or called off
    private var landedTile: Directory? = null
    private var landedTilePlace = 0

    // how far the tile had been carried when the finger left it. ItemTouchHelper takes that offset
    // back off the view before it hands it over, so a flight measured from where the view then sits
    // would start with the tile jumping home
    private var lastCarriedX = 0f
    private var lastCarriedY = 0f

    private var liftAnimator: Animator? = null
    private var targetAnimator: Animator? = null

    private val coverOutlineProvider =
        dragPictureOutline({ adapter.thumbnailCornerRadius }) { it.coverView() }

    init {
        // before any tile is held rather than when one is: the helper works out where the finger is
        // from the press that starts the gesture, and one attached at the long press has missed it
        itemTouchHelper.attachToRecyclerView(recyclerView)
        // the long press ticks the tile, which the grid answers with a change animation - and that
        // animation draws the tile twice, the second copy landing in the cell the first has just
        // been lifted out of. the drag would carry the copy that is on its way out, and the copy
        // left behind would read as a tile to be dropped into
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    /** Takes over the tile's long press; the adapter's own handling still ticks it. */
    fun bindItemGestures(holder: MyRecyclerViewAdapter.ViewHolder, dir: Directory) {
        // the view's own long press buzz would land on top of the lighter tap a lift gets
        holder.itemView.isHapticFeedbackEnabled = false
        holder.itemView.setOnLongClickListener {
            holder.viewLongClicked()
            if (isEnabled) {
                startDrag(holder, dir)
            } else {
                recyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }

            true
        }
    }

    /** Lets go of the grid, which outlives the adapters that are put on it. */
    fun detach() {
        dwell.removeCallbacksAndMessages(null)
        itemTouchHelper.attachToRecyclerView(null)
    }

    /** A view let go of mid drag would come back to another tile still lifted or still ringed. */
    fun resetItemState(itemView: View) {
        if (itemView === liftedView || itemView === armedTile?.itemView) {
            return
        }

        itemView.scaleX = 1f
        itemView.scaleY = 1f
        itemView.alpha = 1f
        itemView.translationX = 0f
        itemView.translationY = 0f
        itemView.translationZ = 0f
        itemView.outlineProvider = ViewOutlineProvider.BACKGROUND
        itemView.coverView()?.foreground = null
    }

    /** A hand made order is what a drag rearranges, and custom sorting is what makes one. */
    fun canReorder() = activity.config.directorySorting and SORT_BY_CUSTOM != 0

    /** The folder on its way into a group, which no scan may draw back onto the grid. */
    val landedTilePath get() = landedTile?.path

    /**
     * Answers for the tile that flew into another one. [restore] puts it back where it stood, for a
     * group that was never named; otherwise it stays off the grid for the rebuild to settle.
     */
    fun releaseLandedTile(restore: Boolean) {
        val dir = landedTile ?: return
        landedTile = null
        if (!restore) {
            return
        }

        val position = landedTilePlace.coerceAtMost(dirs.size)
        adapter.replaceDirs(ArrayList(dirs).apply { add(position, dir) })
        adapter.notifyItemInserted(position)
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        adapter.onRowMoved(fromPosition, toPosition)
        didMove = true
    }

    override fun onRowSelected(myViewHolder: MyRecyclerViewAdapter.ViewHolder?) {
        adapter.onRowSelected(myViewHolder)
        liftedView = myViewHolder?.itemView
        myViewHolder?.itemView?.liftForDrag()
    }

    override fun onRowClear(myViewHolder: MyRecyclerViewAdapter.ViewHolder?) {
        adapter.onRowClear(myViewHolder)
        drop(myViewHolder?.itemView)
    }

    /**
     * Follows the lifted tile as it is drawn, which is where the grid says what is under the finger.
     * A tile it comes to rest on is one it can be dropped into once [FOLDER_DROP_DWELL_MS] has
     * passed with it still there.
     */
    fun onDragDrawn(source: RecyclerView.ViewHolder, dX: Float, dY: Float) {
        lastCarriedX = dX
        lastCarriedY = dY
        val tile = dropTargetUnder(source, dX, dY)
        if (tile === hoveredTile) {
            return
        }

        clearHover()
        hoveredTile = tile
        if (tile != null) {
            dwell.postDelayed({ armDropTarget(tile) }, FOLDER_DROP_DWELL_MS)
        }
    }

    private fun startDrag(holder: MyRecyclerViewAdapter.ViewHolder, dir: Directory) {
        draggedPath = dir.path
        didMove = false
        lastCarriedX = 0f
        lastCarriedY = 0f
        itemTouchHelper.startDrag(holder)
    }

    /**
     * What the drag amounted to. An arrangement is kept whether or not the tile also landed on
     * something - the grid closed over the tiles it passed, and putting them back would read as the
     * whole drag having been thrown away.
     */
    private fun drop(lifted: View?) {
        val goingIn = dropIn(lifted)
        draggedPath = null
        dwell.removeCallbacksAndMessages(null)
        hoveredTile = null

        if (didMove) {
            didMove = false
            onOrderChanged()
            // ending the action mode rebinds what was selected, which would catch a tile still on
            // its way into the one it was dropped on
            if (goingIn == null) {
                adapter.finishActMode()
            }
        }

        if (goingIn == null) {
            liftedView = null
            lifted?.dropAfterDrag()
            clearTargetHighlight()
            return
        }

        // the tile is not going back where it came from, so it carries on into the tile it was
        // dropped on. the settle back that would otherwise run first is what
        // FolderMoveCallback.getAnimationDuration turns off
        goingIn.lifted.flyInto(goingIn.landing.itemView) {
            liftedView = null
            // the tile is in the other one now. it leaves the grid on the frame it lands, and the
            // rest of the grid closes over the cell it left - the group itself is a rescan away,
            // and until then the tile is one the grid could still be asked to draw
            takeLandedTileOffGrid(goingIn.dragged)
            flashTargetRing(goingIn.landing.itemView) {
                clearTargetHighlight()
                onDroppedInto(goingIn.dragged, goingIn.target)
            }
        }
    }

    private fun takeLandedTileOffGrid(dragged: Directory) {
        val position = dirs.indexOfFirst { it.path == dragged.path }
        if (position < 0) {
            return
        }

        landedTile = dirs[position]
        landedTilePlace = position
        // a copy rather than a removal from the list in hand: the screen may still be holding the
        // one it handed over, and a folder dropped from that reads back as a folder that is gone
        adapter.replaceDirs(ArrayList(dirs).apply { removeAt(position) })
        adapter.notifyItemRemoved(position)
    }

    /** What the drop puts where, or null when the tile was let go of anywhere but on another one. */
    private fun dropIn(lifted: View?): DropIn? {
        val landing = armedTile ?: return null
        val dragged = dirs.firstOrNull { it.path == draggedPath } ?: return null
        val target = dirs.getOrNull(landing.absoluteAdapterPosition) ?: return null
        if (lifted == null || target.path == dragged.path) {
            return null
        }

        return DropIn(lifted, landing, dragged, target)
    }

    /** The tile the lifted one is resting on, or null while it is between tiles. */
    private fun dropTargetUnder(
        source: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float
    ): RecyclerView.ViewHolder? {
        // inside a group everything already is in one, and a group cannot hold another
        if (adapter.openGroupId != 0L) {
            return null
        }

        val dragged = dirs.getOrNull(source.absoluteAdapterPosition) ?: return null
        if (!dragged.canBeGrouped()) {
            return null
        }

        val lifted = source.itemView
        val x = lifted.left + dX + lifted.width / 2
        val y = lifted.top + dY + lifted.height / 2
        val tile = tileUnder(x, y, lifted) ?: return null
        if (!tile.itemView.isInDropZone(x, y)) {
            return null
        }

        val over = dirs.getOrNull(tile.absoluteAdapterPosition) ?: return null
        return tile.takeIf { over.canBeGrouped() || over.isFolderGroup() }
    }

    private fun tileUnder(x: Float, y: Float, lifted: View): RecyclerView.ViewHolder? {
        for (index in recyclerView.childCount - 1 downTo 0) {
            val child = recyclerView.getChildAt(index)
            if (child !== lifted && child.holds(x, y)) {
                return recyclerView.getChildViewHolder(child)
            }
        }

        return null
    }

    private fun armDropTarget(tile: RecyclerView.ViewHolder) {
        if (tile.absoluteAdapterPosition == RecyclerView.NO_POSITION) {
            return
        }

        armedTile = tile
        recyclerView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        // out of the way of the tile lighting up underneath, which it otherwise covers whole
        liftTo(FOLDER_HELD_OVER_SCALE)
        tile.itemView.apply {
            outlineProvider = coverOutlineProvider
            targetAnimator?.cancel()
            targetAnimator = animateDragLift(
                scale = FOLDER_DROP_TARGET_SCALE,
                elevation = activity.resources.getDimension(R.dimen.drop_target_elevation)
            )

            coverView()?.apply {
                // nothing over the cover itself, which stays the picture the user picks the folder
                // out by - a photo with something laid over it reads as a different folder
                foreground = activity.dragAccentRing(
                    pictureWidth = width,
                    cornerRadius = adapter.thumbnailCornerRadius,
                    fraction = FOLDER_DROP_BORDER_FRACTION,
                    insetCorners = true
                )
            }
        }
    }

    private fun clearHover() {
        dwell.removeCallbacksAndMessages(null)
        hoveredTile = null
        if (armedTile != null) {
            liftTo(DRAG_LIFT_SCALE)
            clearTargetHighlight()
        }
    }

    private fun clearTargetHighlight() {
        val tile = armedTile ?: return
        armedTile = null
        tile.itemView.apply {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            coverView()?.foreground = null
            targetAnimator?.cancel()
            targetAnimator = animateDragLift(1f, 0f)
        }
    }

    /**
     * Carries the tile the rest of the way into the one it was dropped on and leaves it there,
     * shrunk into the cover and faded out - it is on its way into that folder, and the grid is
     * about to be rebuilt without it.
     *
     * The scale and the offset are worked out together on every frame: a tile scales about its own
     * middle and its cover is not in its middle, so a plain translation would drift off the target
     * as the tile shrank.
     */
    private fun View.flyInto(target: View, onLanded: () -> Unit) {
        val tile = this
        val from = coverCentre()
        val to = target.coverCentre()
        val scalesAboutX = left + width / 2f
        val scalesAboutY = top + height / 2f
        val startScale = scaleX
        val startX = from.x * startScale + scalesAboutX * (1 - startScale) + lastCarriedX
        val startY = from.y * startScale + scalesAboutY * (1 - startScale) + lastCarriedY

        // put the tile back where the finger left it before the first frame is drawn, and take the
        // lift back off ItemTouchHelper, which hands the elevation in as it lets go
        flyingView = tile
        translationX = lastCarriedX
        translationY = lastCarriedY
        translationZ = activity.resources.getDimension(R.dimen.drag_lift_elevation)
        liftAnimator?.cancel()
        liftAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FOLDER_FLY_IN_DURATION_MS
            addUpdateListener { animator ->
                val flown = animator.animatedFraction
                val scale = startScale + (FOLDER_FLY_IN_SCALE - startScale) * flown
                tile.scaleX = scale
                tile.scaleY = scale
                tile.alpha = FOLDER_LIFT_ALPHA * (1 - flown)
                tile.translationX = startX + (to.x - startX) * flown - (from.x * scale + scalesAboutX * (1 - scale))
                tile.translationY = startY + (to.y - startY) * flown - (from.y * scale + scalesAboutY * (1 - scale))
            }

            doOnEnd {
                flyingView = null
                // the screen can go while the tile is still in the air, and what waits at the end
                // of this touches the grid and puts up a dialog
                if (!activity.isDestroyed) {
                    onLanded()
                }
            }

            start()
        }
    }

    /** Blinks the ring on the tile that took the drop. */
    private fun flashTargetRing(target: View, onDone: () -> Unit) {
        val ring = target.coverView()?.foreground
        if (ring == null) {
            onDone()
            return
        }

        ValueAnimator.ofInt(OPAQUE, 0).apply {
            duration = FOLDER_FLASH_DURATION_MS
            repeatCount = FOLDER_FLASH_BLINKS
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { ring.alpha = it.animatedValue as Int }
            doOnEnd {
                ring.alpha = OPAQUE
                if (!activity.isDestroyed) {
                    onDone()
                }
            }

            start()
        }
    }

    /** Pulls the picked up tile out of the grid, with a tap of feedback the finger cannot see. */
    private fun View.liftForDrag() {
        recyclerView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        // ItemTouchHelper owns the elevation of whatever it drags, translationZ is ours to lift with
        outlineProvider = coverOutlineProvider
        liftTo(DRAG_LIFT_SCALE)
    }

    /** How far off the grid the tile in hand is held, which the tile it is over has a say in. */
    private fun liftTo(scale: Float) {
        val lifted = liftedView ?: return
        liftAnimator?.cancel()
        liftAnimator = lifted.animateDragLift(
            scale = scale,
            elevation = activity.resources.getDimension(R.dimen.drag_lift_elevation),
            alpha = FOLDER_LIFT_ALPHA
        )
    }

    private fun View.dropAfterDrag() {
        // the shadow goes at once rather than when the tile has settled - a drop can re-lay the grid
        // out, and a reset waiting on an animation that a re-layout can cut short would leave the
        // tile faded for good
        outlineProvider = ViewOutlineProvider.BACKGROUND
        liftAnimator?.cancel()
        liftAnimator = animateDragLift(1f, 0f)
    }
}

private const val OPAQUE = 255

/** A tile on its way into another one: what flies, what it lands on, and what the two stand for. */
private class DropIn(
    val lifted: View,
    val landing: RecyclerView.ViewHolder,
    val dragged: Directory,
    val target: Directory
)

/** What a tile shows for a picture: a folder group's collage, or a folder's own thumbnail. */
private fun View.coverView(): View? {
    val collage = findViewById<View>(R.id.dir_group_thumbnail)
    return if (collage?.isVisible == true) collage else findViewById(R.id.dir_thumbnail)
}

/** Where a tile's picture sits in the grid, which is what one tile is aimed at and shrinks into. */
private fun View.coverCentre(): PointF {
    val cover = coverView() ?: this
    return PointF(
        left + (cover.left + cover.right) / 2f,
        top + (cover.top + cover.bottom) / 2f
    )
}

private fun View.holds(x: Float, y: Float) = x >= left + translationX && x <= right + translationX &&
    y >= top + translationY && y <= bottom + translationY

/** The middle of the tile, which is what a lifted tile has to be over to be dropped into it. */
private fun View.isInDropZone(x: Float, y: Float): Boolean {
    val insetX = width * (1 - FOLDER_DROP_ZONE) / 2
    val insetY = height * (1 - FOLDER_DROP_ZONE) / 2
    return x >= left + translationX + insetX && x <= right + translationX - insetX &&
        y >= top + translationY + insetY && y <= bottom + translationY - insetY
}

/**
 * Holds the grid still under a tile that is being held over another, and hands every frame of the
 * drag back so the tile underneath can be found - [ItemTouchHelper] tells nobody else where the
 * finger is.
 */
private class FolderMoveCallback(private val mode: FolderDragMode) : PaddedGridMoveCallback(mode, true) {
    override fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder) = FOLDER_DRAG_MOVE_THRESHOLD

    /**
     * A tile let go of over another one is carried into it by [FolderDragMode] instead, so the
     * settle back into the grid that would otherwise run first is given no time at all.
     */
    override fun getAnimationDuration(
        recyclerView: RecyclerView,
        animationType: Int,
        animateDx: Float,
        animateDy: Float
    ) = if (mode.isDroppingIn) {
        0L
    } else {
        super.getAnimationDuration(recyclerView, animationType, animateDx, animateDy)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        if (mode.isHovering || !mode.canReorder()) {
            return false
        }

        return super.onMove(recyclerView, viewHolder, target)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        // a tile already flying into another one places itself, and the settle back this would
        // otherwise draw would put it back in the grid for the one frame it takes to notice
        if (mode.isFlyingIn(viewHolder)) {
            return
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
            mode.onDragDrawn(viewHolder, dX, dY)
        }
    }
}
