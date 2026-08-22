package org.fossify.gallery.helpers

import android.view.View
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.target.Target

/** Screenfuls warmed ahead of the finger while the grid is being scrolled. */
private const val AHEAD_SCREENFULS = 1.0f

/** ...and behind it, for a turn-around. */
private const val BEHIND_SCREENFULS = 0.25f

/** Both ways once the grid comes to rest, with no direction left to favour. */
private const val IDLE_SCREENFULS = 0.5f

/** However many that comes to, no more requests in flight at once than this. */
private const val MAX_IN_FLIGHT = 120

/** The most of [MAX_IN_FLIGHT] the trailing window may take, the leading one having first call. */
private const val MAX_BEHIND_SHARE = 0.25f

/**
 * Decodes the thumbnails of items the grid has not reached yet, weighted towards the way it is being
 * scrolled: about a screenful ahead of the finger against a quarter of one behind, and a small
 * window both ways once it comes to rest - which is also what warms the first screenful of a folder
 * before anything has been touched.
 *
 * Only the mechanism lives here. What a position is worth loading, and how, is [preloadAt]'s
 * business, and it must build **exactly** the request the item's own bind will build: the model,
 * signature, size, transform and decode format all go into Glide's cache key, and a preload that
 * misses it by any of them has the grid decode every picture twice. The size matters twice over -
 * see `ExifThumbnailLoader`, which sizes a request out of the copy stored inside the photo only
 * while that copy covers what was asked for.
 *
 * Sits on top of the recycler's own look-ahead rather than replacing it: `GapWorker` still binds a
 * row ahead, and the item view cache still holds a couple of rows behind.
 */
class ThumbnailPrefetcher(
    private val recyclerView: RecyclerView,
    /** Starts the request for a position, or null for an item there is nothing to warm. */
    private val preloadAt: (position: Int) -> Target<*>?,
    private val cancel: (Target<*>) -> Unit,
) : RecyclerView.OnScrollListener() {

    /**
     * What is in flight, by the position that wanted it. Doubles as the record of what has already
     * been asked for, so a slow drag does not re-submit its whole window every frame.
     */
    private val outstanding = LinkedHashMap<Int, Target<*>>()

    /**
     * The positions this pass wants, nearest the screen first. Held across passes rather than built
     * fresh: a pass runs on every scrolled frame.
     */
    private val wanted = LinkedHashSet<Int>()

    private val pass = Runnable { prefetch() }

    // which way the grid is being dragged, kept through a frame that did not move rather than read
    // as a change of mind
    private var isForward = true
    private var isScrolling = false
    private var isAwaitingLayout = false

    init {
        recyclerView.addOnScrollListener(this)
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        val travelled = travel(dx, dy)
        if (travelled != 0) {
            isForward = travelled > 0
        }

        prefetch()
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        isScrolling = newState != RecyclerView.SCROLL_STATE_IDLE
        if (!isScrolling) {
            prefetch()
        }
    }

    /**
     * Lets go of everything in flight and looks again. A position's item and a tile's size are both
     * part of what was asked for, so anything that moves an item or resizes a tile leaves every
     * outstanding request fetching the wrong picture, or the right one at the wrong size.
     */
    fun reset() {
        cancelOutstanding()
        // posted: the layout manager has not been through the change yet, so there are no visible
        // positions to work a window out from until it has
        recyclerView.removeCallbacks(pass)
        recyclerView.post(pass)
    }

    fun detach() {
        recyclerView.removeOnScrollListener(this)
        recyclerView.removeCallbacks(pass)
        cancelOutstanding()
    }

    private fun prefetch() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        val itemCount = recyclerView.adapter?.itemCount ?: 0
        if (itemCount == 0) {
            return
        }

        // nothing on screen to work a window out from: the grid has items but has not been laid out
        // since whatever changed under it, which is where a freshly opened folder starts. Waiting on
        // the layout rather than posting again - a posted pass runs before the traversal it needs
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
            awaitLayout()
            return
        }

        // in items rather than rows: a row is a very different amount of scrolling at three columns
        // than at twenty, where a screenful is hundreds of tiles
        val screenful = last - first + 1
        val step = if (isForward) 1 else -1
        val leadingEdge = if (isForward) last else first
        val trailingEdge = if (isForward) first else last

        wanted.clear()
        collect(leadingEdge + step, step, aheadCount(screenful), itemCount)
        collect(trailingEdge - step, -step, behindCount(screenful), itemCount)

        dropUnwanted()
        for (position in wanted) {
            if (!outstanding.containsKey(position)) {
                outstanding[position] = preloadAt(position) ?: continue
            }
        }
    }

    private fun awaitLayout() {
        if (isAwaitingLayout) {
            return
        }

        isAwaitingLayout = true
        recyclerView.doOnNextLayout {
            isAwaitingLayout = false
            prefetch()
        }
    }

    /** Walks [count] positions from [from] outwards, so the nearest are asked for first. */
    private fun collect(from: Int, step: Int, count: Int, itemCount: Int) {
        var position = from
        repeat(count) {
            if (position < 0 || position >= itemCount) {
                return
            }

            wanted.add(position)
            position += step
        }
    }

    /** Frees a Glide worker for something still wanted by letting go of what has scrolled past. */
    private fun dropUnwanted() {
        val entries = outstanding.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            if (entry.key !in wanted) {
                cancel(entry.value)
                entries.remove()
            }
        }
    }

    private fun aheadCount(screenful: Int) = if (isScrolling) {
        (screenful * AHEAD_SCREENFULS).toInt().coerceAtMost(MAX_IN_FLIGHT - behindCount(screenful))
    } else {
        idleCount(screenful)
    }

    private fun behindCount(screenful: Int) = if (isScrolling) {
        (screenful * BEHIND_SCREENFULS).toInt().coerceAtMost((MAX_IN_FLIGHT * MAX_BEHIND_SHARE).toInt())
    } else {
        idleCount(screenful)
    }

    private fun idleCount(screenful: Int) = (screenful * IDLE_SCREENFULS).toInt().coerceAtMost(MAX_IN_FLIGHT / 2)

    /**
     * How far the grid moved towards later items. Only one axis is the one it scrolls, so whichever
     * reports anything is that one - except that a sideways grid laid out right to left travels the
     * other way for the same delta.
     */
    private fun travel(dx: Int, dy: Int): Int {
        if (dx == 0) {
            return dy
        }

        val isRtl = recyclerView.layoutManager?.layoutDirection == View.LAYOUT_DIRECTION_RTL
        return if (isRtl) -dx else dx
    }

    private fun cancelOutstanding() {
        outstanding.values.forEach(cancel)
        outstanding.clear()
    }
}
