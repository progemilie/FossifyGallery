package org.fossify.gallery.adapters

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
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
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.interfaces.ItemTouchHelperContract
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.DRAG_LIFT_DURATION_MS
import org.fossify.gallery.helpers.DRAG_LIFT_SCALE
import org.fossify.gallery.helpers.FOLDER_DRAG_MOVE_THRESHOLD
import org.fossify.gallery.helpers.FOLDER_DROP_BORDER_FRACTION
import org.fossify.gallery.helpers.FOLDER_DROP_DWELL_MS
import org.fossify.gallery.helpers.FOLDER_FLASH_BLINKS
import org.fossify.gallery.helpers.FOLDER_FLASH_DURATION_MS
import org.fossify.gallery.helpers.FOLDER_FLY_IN_DURATION_MS
import org.fossify.gallery.helpers.FOLDER_FLY_IN_SCALE
import org.fossify.gallery.helpers.FOLDER_DROP_TARGET_SCALE
import org.fossify.gallery.helpers.FOLDER_DROP_ZONE
import org.fossify.gallery.helpers.FOLDER_HELD_OVER_SCALE
import org.fossify.gallery.helpers.FOLDER_LIFT_ALPHA
import org.fossify.gallery.helpers.PaddedGridMoveCallback
import org.fossify.gallery.models.Directory

/**
 * Drag and drop over the folder grid: a tile let go of between two others arranges the grid, a tile
 * held over another goes into a folder group with it. This drives [DirectoryAdapter] rather than
 * living inside it - binding tiles and arranging them by hand are two jobs, and only one is
 * upstream's.
 *
 * The long press that lifts a tile is the one that already selected it, so a folder is ticked and
 * picked up in the same gesture; what the finger does next is what tells the two apart.
 *
 * Two rules keep the drops honest:
 * - **The grid holds still while a lifted tile sits on another one.** Nothing can be dropped onto a
 *   tile that steps aside as the finger arrives, so the middle of every tile ([FOLDER_DROP_ZONE]) is
 *   a place the arranging does not reach - and a tile has to travel most of its own width
 *   ([FOLDER_DRAG_MOVE_THRESHOLD]) before the grid shifts at all, or it would never get there.
 * - **Only a plain folder is ever carried into something.** Groups do not nest and two of them
 *   cannot be merged, so a lifted group tile can only be moved, never dropped in.
 */
// a drag is a long conversation - picked up, carried, held over something, dropped - and every step
// of it is a couple of lines that only makes sense next to the ones around it
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

    /**
     * Whether the tile is on its way into another one and its place is no longer ItemTouchHelper's
     * to say. The settle back it lines up is given no time, but it is still drawn once after it has
     * ended - and that one frame is enough to put the tile back where it was picked up from.
     */
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

    // how far the tile had been carried when the finger left it. ItemTouchHelper takes that offset
    // back off the view before it hands it over, so a flight measured from where the view then sits
    // would start with the tile jumping home
    private var lastCarriedX = 0f
    private var lastCarriedY = 0f

    private var liftAnimator: Animator? = null
    private var targetAnimator: Animator? = null

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

    /**
     * Takes over the tile's long press. Selecting is left to the adapter's own handling of it, so
     * the tile is ticked and the action mode raised exactly as they always were.
     */
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
    fun detach() = itemTouchHelper.attachToRecyclerView(null)

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

    /** A hand made order is what a drag rearranges - a group's member order is always one. */
    fun canReorder() = adapter.openGroupId != 0L ||
        activity.config.directorySorting and SORT_BY_CUSTOM != 0

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
            adapter.takeTileOffGrid(goingIn.dragged)
            flashTargetRing(goingIn.landing.itemView) {
                clearTargetHighlight()
                onDroppedInto(goingIn.dragged, goingIn.target)
            }
        }
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
        val x = lifted.left + dX + lifted.width / HALVES
        val y = lifted.top + dY + lifted.height / HALVES
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
            targetAnimator = scaleTo(
                FOLDER_DROP_TARGET_SCALE,
                activity.resources.getDimension(R.dimen.drop_target_elevation)
            )

            coverView()?.apply { foreground = dropTargetRing(width) }
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
            targetAnimator = scaleTo(1f, 0f)
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
        val scalesAboutX = left + width / HALVES.toFloat()
        val scalesAboutY = top + height / HALVES.toFloat()
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
                onLanded()
            }

            start()
        }
    }

    /** Blinks the ring on the tile that took the drop, so what happened is said on the tile it happened to. */
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
                onDone()
            }

            start()
        }
    }

    /**
     * Pulls the picked up tile out of the grid - smaller, half see through and casting a shadow into
     * the gap that opens around it - so the moment it is free to be moved is unmistakable. A light
     * tap goes with it, the finger is on the tile and cannot see it.
     */
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
        liftAnimator = lifted.scaleTo(
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
        liftAnimator = scaleTo(1f, 0f)
    }

    /**
     * Animators of our own rather than the view's animate() builder: the grid's item animator uses
     * that builder for the tiles it slides around and cancels whatever it finds on it, which would
     * strand a picked up tile half lifted.
     */
    private fun View.scaleTo(scale: Float, elevation: Float, alpha: Float = 1f): Animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@scaleTo, View.SCALE_X, scale),
            ObjectAnimator.ofFloat(this@scaleTo, View.SCALE_Y, scale),
            ObjectAnimator.ofFloat(this@scaleTo, View.TRANSLATION_Z, elevation),
            ObjectAnimator.ofFloat(this@scaleTo, View.ALPHA, alpha)
        )
        duration = DRAG_LIFT_DURATION_MS
        start()
    }

    /**
     * What a tile wears while the lifted one is held over it: a thin accent ring and nothing over
     * the cover itself, which stays the picture the user picked the folder out by.
     *
     * The radius is the cover's own less half the stroke, because [GradientDrawable] draws the
     * stroke inside the bounds it is given - at the cover's radius the ring would come out rounder
     * than the picture and leave its corners sticking out past it.
     */
    private fun dropTargetRing(coverWidth: Int): GradientDrawable {
        val strokeWidth = (coverWidth * FOLDER_DROP_BORDER_FRACTION).coerceIn(
            activity.resources.getDimension(R.dimen.accent_border_min_width),
            activity.resources.getDimension(R.dimen.accent_border_max_width)
        )

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = (adapter.thumbnailCornerRadius - strokeWidth / HALVES).coerceAtLeast(0f)
            setStroke(strokeWidth.toInt(), activity.getProperPrimaryColor())
        }
    }

    /**
     * The tile carries its name and count below its cover, so a shadow around the whole of it would
     * sit off the picture. This traces the cover inside it instead, corners and all.
     */
    private val coverOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val cover = view.coverView()
            if (cover == null || cover.width == 0) {
                return
            }

            outline.setRoundRect(
                cover.left,
                cover.top,
                cover.right,
                cover.bottom,
                adapter.thumbnailCornerRadius
            )
        }
    }
}

private const val HALVES = 2
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
        left + (cover.left + cover.right) / HALVES.toFloat(),
        top + (cover.top + cover.bottom) / HALVES.toFloat()
    )
}

private fun View.holds(x: Float, y: Float) = x >= left + translationX && x <= right + translationX &&
    y >= top + translationY && y <= bottom + translationY

/** The middle of the tile, which is what a lifted tile has to be over to be dropped into it. */
private fun View.isInDropZone(x: Float, y: Float): Boolean {
    val insetX = width * (1 - FOLDER_DROP_ZONE) / HALVES
    val insetY = height * (1 - FOLDER_DROP_ZONE) / HALVES
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
