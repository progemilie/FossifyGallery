package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isNotEmpty
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.adapters.ViewerThumbnailAdapter
import org.fossify.gallery.models.Medium
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/**
 * The row of thumbnails between the photo and the bottom actions in the fullscreen viewer, ported
 * from Aves' ThumbnailScroller. Whatever sits in the middle of the strip is what the pager shows,
 * so scrolling the strip and swiping the photo are two views of the same position.
 *
 * The strip is padded by half its own width at either end, which is what lets the first and last
 * item reach the middle; a [LinearSnapHelper] then measures against that same middle, so snapping
 * and centering agree without any offset of their own.
 */
class ViewerThumbnailStrip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    companion object {
        /**
         * The share of a fling's velocity the strip keeps. Aves hands the fling to a plain friction
         * simulation and it coasts for seconds, well past whatever you were aiming at. Fling
         * distance grows with about v^1.74, so four fifths of the velocity is about two thirds of
         * the travel - still clearly shorter than Aves, without stopping the moment you let go.
         */
        private const val FLING_VELOCITY_KEPT = 0.8f

        /** Faster than RecyclerView's own 100ms/inch, so the settle is not the slow part. */
        private const val MILLIS_PER_INCH = 60f

        /**
         * How long the strip takes to bring a position to the middle when something else moved
         * first - a swipe on the photo, or a tap on a thumbnail. Distance alone would give a step
         * of one thumbnail about a frame and a half, which reads as a cut rather than a move.
         */
        private const val CENTERING_MIN_MS = 240
        private const val CENTERING_MAX_MS = 400
        private const val CENTERING_DECELERATION = 1.5f

        /** How far off the middle a thumbnail is drawn shrunk and shaded, in item widths. */
        private const val FALLOFF_ITEMS = 1f
        private const val OFF_CENTRE_SCALE = 0.92f
        private const val OFF_CENTRE_SHADE = 0.45f

        /**
         * How many thumbnails out from the middle the gaps take to close. The widest gap is the one
         * beside the middle thumbnail - what is left of [R.dimen.viewer_strip_item_width] once the
         * thumbnail has taken its share - each one after it is tighter than the last, and from here
         * outwards they are all [R.dimen.viewer_strip_gap_tight].
         */
        private const val GAP_CLOSING_ITEMS = 3f

        /** Thumbnails held ready either side of the strip, so a scroll back over them is instant. */
        private const val VIEW_CACHE_SIZE = 8

        /**
         * Passes over how far past the ends of the strip to lay out. Two would do on a phone held
         * upright, where the pull is a fraction of a thumbnail; the third is for a strip wide
         * enough that the pull is wider than the thumbnail it is applied to.
         */
        private const val EDGE_SETTLING_PASSES = 3

        /**
         * How much narrower than the widest one, in pixels, all the gaps within [items] item widths
         * of the middle add up to being - which is how far towards the middle the thumbnail out
         * there is drawn. Every gap closes by [closedPerStep] more than the one inside it, so the
         * sum over a distance grows with its square, until [GAP_CLOSING_ITEMS] out where the gaps
         * stop closing and each further one takes off the same as the last.
         */
        private fun gapsClosedWithin(items: Float, closedPerStep: Float) =
            if (items <= GAP_CLOSING_ITEMS) {
                closedPerStep * items * items / 2f
            } else {
                closedPerStep * GAP_CLOSING_ITEMS * (items - GAP_CLOSING_ITEMS / 2f)
            }
    }

    /** Called with the position now in the middle of the strip, so the pager can follow it. */
    var onMediumPicked: ((position: Int) -> Unit)? = null

    // eases out harder than the scroller's own interpolator, so the glide leaves promptly and
    // arrives gently rather than stopping dead on the beat
    private val centeringInterpolator = DecelerateInterpolator(CENTERING_DECELERATION)

    private val itemWidth = resources.getDimensionPixelSize(R.dimen.viewer_strip_item_width)
    private val thumbnailWidth =
        resources.getDimensionPixelSize(R.dimen.viewer_strip_thumbnail_width)

    /** How much more of the gap each step out from the middle closes than the step before it. */
    private val gapClosedPerStep = (
        itemWidth - thumbnailWidth - resources.getDimensionPixelSize(R.dimen.viewer_strip_gap_tight)
        ) / GAP_CLOSING_ITEMS

    private val stripAdapter = ViewerThumbnailAdapter(
        thumbnailWidth = thumbnailWidth,
        thumbnailHeight = resources.getDimensionPixelSize(R.dimen.viewer_strip_thumbnail_height),
        // a tapped thumbnail is brought to the middle and the pager sent after it, the same two
        // steps a thumbnail scrolled to the middle goes through
        onItemClick = { position ->
            committedPosition = position
            centerOn(position, smooth = true)
            onMediumPicked?.invoke(position)
        }
    )

    // what the pager has been told to show. It only parts ways with the middle of the strip for the
    // one frame between the middle changing and the pager being told
    private var committedPosition = NO_POSITION
    private var isUserScrolling = false

    init {
        layoutManager = StripLayoutManager()
        adapter = stripAdapter
        setHasFixedSize(true)
        setItemViewCacheSize(VIEW_CACHE_SIZE)
        // nothing here waits on an animation, and item animations only get in the way of a scroll
        itemAnimator = null
        clipToPadding = false
        overScrollMode = OVER_SCROLL_NEVER
        StripSnapHelper().attachToRecyclerView(this)

        addOnScrollListener(object : OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == SCROLL_STATE_DRAGGING) {
                    isUserScrolling = true
                } else if (newState == SCROLL_STATE_IDLE && isUserScrolling) {
                    isUserScrolling = false
                    // the snap may have moved the middle on by one after the fling ran out
                    commitCentre()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateChildDecorations()
                // the photo keeps up with the strip rather than waiting for it to stop
                if (isUserScrolling) {
                    commitCentre()
                }
            }
        })
    }

    /**
     * Padding of half the strip minus half an item at either end: without it the middle is only
     * reachable by items with enough of the list on both sides of them.
     */
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val sidePadding = max(0, (MeasureSpec.getSize(widthSpec) - itemWidth) / 2)
        if (paddingLeft != sidePadding || paddingRight != sidePadding) {
            setPadding(sidePadding, paddingTop, sidePadding, paddingBottom)
        }

        super.onMeasure(widthSpec, heightSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // children that scrolled into view during this pass have not been through onScrolled yet
        updateChildDecorations()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // the middle of the strip is half its width along, so a rotation moves it out from under
        // whatever was sitting there
        if (w != oldw && committedPosition != NO_POSITION) {
            centerOn(committedPosition, smooth = false)
        }
    }

    fun setMedia(media: List<Medium>, selectedPosition: Int) {
        stripAdapter.setItems(media)
        val position = selectedPosition.coerceIn(0, max(0, media.lastIndex))
        committedPosition = position
        centerOn(position, smooth = false)
    }

    /** The file at [path] has been changed in place; draw its thumbnail again. */
    fun reload(path: String) = stripAdapter.reload(path)

    /** Follows the pager: what it swiped to is what the strip centers. */
    fun setSelectedPosition(position: Int, smooth: Boolean = true) {
        if (position !in 0 until stripAdapter.itemCount) {
            return
        }

        committedPosition = position
        // a drag in flight is the user's, and the pager is only echoing where it already put them
        if (!isUserScrolling && centeredPosition() != position) {
            centerOn(position, smooth)
        }
    }

    private fun centerOn(position: Int, smooth: Boolean) {
        val manager = layoutManager as LinearLayoutManager
        if (smooth && isNotEmpty()) {
            manager.startSmoothScroll(CenteringScroller(position))
        } else {
            // an offset of zero is the start of the padding, which is where the middle item begins
            manager.scrollToPositionWithOffset(position, 0)
        }
    }

    private fun commitCentre() {
        val position = centeredPosition()
        if (position != NO_POSITION && position != committedPosition) {
            committedPosition = position
            onMediumPicked?.invoke(position)
        }
    }

    /**
     * The item nearest the middle of the strip, measured from the laid out children rather than
     * from the scroll offset so it stays right mid-snap and mid-fling.
     */
    private fun centeredPosition(): Int {
        val middle = width / 2
        var nearest = NO_POSITION
        var nearestDistance = Int.MAX_VALUE
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val distance = abs((child.left + child.right) / 2 - middle)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = getChildAdapterPosition(child)
            }
        }

        return nearest
    }

    /**
     * Sizes and shades every thumbnail by how near the middle of the strip it currently is, and
     * draws it pulled towards the middle by however much of the gaps between here and there has
     * been closed. Done from the children's own positions on each scroll frame rather than by
     * telling the adapter which item is selected: an adapter change lands a frame later and then
     * animates from there, which reads as the highlight trailing behind the thumbnails it is
     * supposed to be marking.
     *
     * The cells stay evenly spaced where they were laid out - only what is drawn moves - so the
     * snapping and [centeredPosition], which both measure laid out positions, are untouched by any
     * of this.
     */
    private fun updateChildDecorations() {
        val middle = width / 2f
        val falloff = itemWidth * FALLOFF_ITEMS
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val binding =
                (getChildViewHolder(child) as? ViewerThumbnailAdapter.ThumbnailViewHolder)?.binding
            if (binding != null) {
                val offset = (child.left + child.right) / 2f - middle
                val distance = abs(offset)
                val nearness = (1f - distance / falloff).coerceIn(0f, 1f)
                val scale = OFF_CENTRE_SCALE + (1f - OFF_CENTRE_SCALE) * nearness
                binding.viewerThumbnailHolder.scaleX = scale
                binding.viewerThumbnailHolder.scaleY = scale
                binding.viewerThumbnailShade.alpha = OFF_CENTRE_SHADE * (1f - nearness)
                child.translationX =
                    -sign(offset) * gapsClosedWithin(distance / itemWidth, gapClosedPerStep)
            }
        }
    }

    /**
     * Lays out as far past both ends of the strip as the thumbnail there is pulled inwards by
     * [gapsClosedWithin]. A thumbnail is only laid out while the place it would sit without the
     * compression is on screen, so without this the ends of the strip would be a sliver of nothing.
     */
    private inner class StripLayoutManager : LinearLayoutManager(context, HORIZONTAL, false) {
        override fun calculateExtraLayoutSpace(state: State, extraLayoutSpace: IntArray) {
            super.calculateExtraLayoutSpace(state, extraLayoutSpace)
            // how far out the thumbnail drawn at the edge was laid out, which is the edge plus the
            // pull that brought it back - and the pull depends on where it was laid out, so the two
            // are settled against each other. Each pass moves it a small fraction of the last
            var pastTheEnd = 0f
            repeat(EDGE_SETTLING_PASSES) {
                pastTheEnd =
                    gapsClosedWithin((width / 2f + pastTheEnd) / itemWidth, gapClosedPerStep)
            }

            extraLayoutSpace[0] = max(extraLayoutSpace[0], pastTheEnd.toInt() + 1)
            extraLayoutSpace[1] = max(extraLayoutSpace[1], pastTheEnd.toInt() + 1)
        }
    }

    /**
     * Lands the fling on an item rather than wherever friction ran out, the way Aves'
     * KnownExtentScrollPhysics does, and cuts both how far it travels and how long the settle takes.
     */
    private inner class StripSnapHelper : LinearSnapHelper() {
        override fun onFling(velocityX: Int, velocityY: Int): Boolean {
            return super.onFling(
                (velocityX * FLING_VELOCITY_KEPT).toInt(),
                (velocityY * FLING_VELOCITY_KEPT).toInt()
            )
        }

        override fun createScroller(layoutManager: LayoutManager): SmoothScroller? {
            if (layoutManager !is SmoothScroller.ScrollVectorProvider) {
                return null
            }

            return object : LinearSmoothScroller(context) {
                override fun onTargetFound(targetView: View, state: State, action: Action) {
                    val snapDistances =
                        calculateDistanceToFinalSnap(layoutManager, targetView) ?: return
                    val time = calculateTimeForDeceleration(
                        max(abs(snapDistances[0]), abs(snapDistances[1]))
                    )
                    if (time > 0) {
                        action.update(
                            snapDistances[0], snapDistances[1], time, mDecelerateInterpolator
                        )
                    }
                }

                override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                    return MILLIS_PER_INCH / displayMetrics.densityDpi
                }
            }
        }
    }

    /** Brings a position to the middle of the strip, which is where the start padding ends. */
    private inner class CenteringScroller(position: Int) : LinearSmoothScroller(context) {
        init {
            targetPosition = position
        }

        override fun getHorizontalSnapPreference() = SNAP_TO_START

        /**
         * Held to a glide the eye can follow. The scroller's own timing is a function of distance,
         * and the distance here is usually a single thumbnail - too little to be worth animating by
         * that measure, so the strip would arrive before anyone saw it leave.
         */
        override fun onTargetFound(targetView: View, state: State, action: Action) {
            val dx = calculateDxToMakeVisible(targetView, SNAP_TO_START)
            if (dx != 0) {
                val time = calculateTimeForDeceleration(abs(dx))
                    .coerceIn(CENTERING_MIN_MS, CENTERING_MAX_MS)
                action.update(-dx, 0, time, centeringInterpolator)
            }
        }

        override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
            return MILLIS_PER_INCH / displayMetrics.densityDpi
        }
    }
}
