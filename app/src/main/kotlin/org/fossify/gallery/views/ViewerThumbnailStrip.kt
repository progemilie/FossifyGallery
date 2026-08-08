package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
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
         * distance grows with about v^1.74, so keeping two thirds of the velocity leaves a bit
         * under half the travel - still momentum, over sooner.
         */
        private const val FLING_VELOCITY_KEPT = 0.65f

        /** Half of RecyclerView's own 100ms/inch, so the settle after a fling is not the slow part. */
        private const val MILLIS_PER_INCH = 45f

        /** Thumbnails held ready either side of the strip, so a scroll back over them is instant. */
        private const val VIEW_CACHE_SIZE = 8
    }

    /** Called with the position the strip has come to rest on, so the pager can follow it. */
    var onMediumPicked: ((position: Int) -> Unit)? = null

    private val itemWidth = resources.getDimensionPixelSize(R.dimen.viewer_strip_item_width)
    private val stripAdapter = ViewerThumbnailAdapter(
        thumbnailSize = resources.getDimensionPixelSize(R.dimen.viewer_strip_thumbnail_size),
        onItemClick = ::pick
    )

    // what the pager has actually been told to show. It and the highlight part ways while a drag is
    // in flight and meet again when it settles
    private var committedPosition = NO_POSITION
    private var isUserScrolling = false

    init {
        layoutManager = LinearLayoutManager(context, HORIZONTAL, false)
        adapter = stripAdapter
        setHasFixedSize(true)
        setItemViewCacheSize(VIEW_CACHE_SIZE)
        // the selection animates itself, and item animations only get in the way of a fast scroll
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
                    commitCenteredPosition()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // the highlight tracks the middle of the strip live, so a fling reads as passing
                // over thumbnails rather than as one still picture until it stops
                if (isUserScrolling) {
                    highlight(centeredPosition())
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
        stripAdapter.selectedPosition = position
        committedPosition = position
        centerOn(position, smooth = false)
    }

    /** Follows the pager: what it swiped to is what the strip centers and lights up. */
    fun setSelectedPosition(position: Int, smooth: Boolean = true) {
        if (position !in 0 until stripAdapter.itemCount) {
            return
        }

        committedPosition = position
        highlight(position)
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

    private fun commitCenteredPosition() {
        val position = centeredPosition()
        if (position != NO_POSITION && position != committedPosition) {
            committedPosition = position
            highlight(position)
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

    private fun highlight(position: Int) {
        if (position != NO_POSITION) {
            stripAdapter.selectedPosition = position
        }
    }

    private fun pick(position: Int) {
        committedPosition = position
        highlight(position)
        centerOn(position, smooth = true)
        onMediumPicked?.invoke(position)
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

        override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
            return MILLIS_PER_INCH / displayMetrics.densityDpi
        }
    }
}
