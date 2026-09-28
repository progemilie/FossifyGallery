package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.SparseArray
import androidx.core.util.size
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import org.fossify.gallery.models.Medium

/**
 * New pictures a frame may ask for, for tiles with nothing to show yet. A count coming into view
 * wants hundreds, and asking for them all in one frame would stall the very frame the pinch is meant
 * to be smoothest in.
 */
private const val MISSING_PER_FRAME = 48

/** ...and for tiles already drawn with a picture of another size, which only come out sharper. */
private const val SHARPER_PER_FRAME = 6

/**
 * However many that leaves, the most of a frame asking may take. A request costs the main thread a
 * good deal more on a slow phone than a fast one, and a frame it overruns is dropped just the same;
 * asking faster than the decoders can keep up shows nothing sooner anyway.
 */
private const val ASKING_NANOS_PER_FRAME = 3_000_000L

/**
 * The pictures a zoom draws its tiles with - see MediaGridZoom.
 *
 * Each is asked for exactly as the grid's own bind at that count would ask for it, so the tiles bound
 * once a zoom settles find their pictures done instead of decoding them a second time. Until a
 * picture arrives its tile is drawn with any other picture of the same medium to hand, which is
 * nearly always the one it was showing when the pinch began.
 *
 * Those it was showing are borrowed from the grid's own tiles, which is what lets a pinch start
 * without asking for a screenful of pictures in its first frame. A borrowed picture is only safe to
 * draw while its tile still holds it: [stopBorrowing] has to come before anything rebinds the grid.
 */
class ZoomThumbnails(
    context: Context,
    private val loader: Loader,
    private val onArrived: () -> Unit,
) {
    interface Loader {
        /** One number for any two counts whose tiles are bound with the same picture. */
        fun keyOf(columnCount: Int): Int

        /** Starts the grid's own request at [columnCount] into [target]; false where there is none. */
        fun load(medium: Medium, columnCount: Int, target: CustomTarget<Drawable>): Boolean
    }

    private val requests = Glide.with(context.applicationContext)
    private val borrowed = HashMap<String, Drawable>()
    private var borrowedColumns = 0
    private var borrowedKey = Int.MIN_VALUE
    private var canBorrow = true
    private var isSharpening = true

    /** Every request made, by key and then path; one that could not be made stays, empty. */
    private val slots = SparseArray<HashMap<String, Slot>>()

    /** The last picture of each medium to arrive, whatever it was asked for at. */
    private val latest = HashMap<String, Drawable>()

    private var missingBudget = MISSING_PER_FRAME
    private var sharperBudget = SHARPER_PER_FRAME
    private var askingNanos = 0L
    private var isAsking = false

    /**
     * Whether the frame left tiles unasked for want of allowance. Another frame has to ask for them:
     * with the fingers still, nothing else may draw one, as a picture already in memory arrives at
     * once and asks for no redraw of its own.
     */
    var isShortOfAsks = false
        private set

    /** Takes the pictures the grid's tiles are showing at [columnCount], by path. */
    fun borrow(columnCount: Int, pictures: Map<String, Drawable>) {
        borrowedColumns = columnCount
        borrowedKey = loader.keyOf(columnCount)
        borrowed.putAll(pictures)
    }

    fun startFrame() {
        missingBudget = MISSING_PER_FRAME
        sharperBudget = SHARPER_PER_FRAME
        askingNanos = 0L
        isShortOfAsks = false
    }

    /**
     * The best picture of [medium] there is to draw its tile at [columnCount] with, or null. Asks
     * for a better one where [mayAsk] and the frame has not asked for its share already.
     */
    fun pictureFor(medium: Medium, columnCount: Int, mayAsk: Boolean): Drawable? {
        val path = medium.path
        val key = loader.keyOf(columnCount)
        val slot = slots[key]?.get(path)
        slot?.picture?.let { return it }
        if (canBorrow && key == borrowedKey) {
            borrowed[path]?.let { return it }
        }

        val stand = latest[path] ?: if (canBorrow) borrowed[path] else null
        val wantsOne = slot == null && (stand == null || isSharpening)
        if (wantsOne && mayAsk && spend(hasPicture = stand != null)) {
            ask(medium, columnCount, key)?.let { return it }
        }

        return stand
    }

    private fun spend(hasPicture: Boolean): Boolean {
        val allowed = askingNanos < ASKING_NANOS_PER_FRAME && if (hasPicture) {
            sharperBudget-- > 0
        } else {
            missingBudget-- > 0
        }

        isShortOfAsks = isShortOfAsks || !allowed
        return allowed
    }

    /**
     * The zoom has come to rest, and the grid is about to bind its own pictures: a tile drawn with one
     * of another size keeps it rather than asking again for what the grid is asking for. A tile with
     * nothing to show still asks, or it would wait on the whole grid's pictures to fade in over it.
     */
    fun stopSharpening() {
        isSharpening = false
    }

    /**
     * From here on only pictures asked for by the zoom itself are drawn. Any of [onScreen] drawn with
     * a borrowed picture and nothing of the zoom's own to stand in for it is asked for first, at the
     * count it was borrowed at: its tile still holds it, so it comes straight back with no decode,
     * where once the grid rebinds it may not be there to come back.
     */
    fun stopBorrowing(onScreen: Collection<Medium>) {
        for (medium in onScreen) {
            val path = medium.path
            if (latest[path] == null && borrowed.containsKey(path) && slots[borrowedKey]?.containsKey(path) != true) {
                ask(medium, borrowedColumns, borrowedKey)
            }
        }

        canBorrow = false
        borrowed.clear()
    }

    /** Lets go of every picture, which drops them into Glide's memory cache for the grid to find. */
    fun release() {
        for (index in 0 until slots.size) {
            slots.valueAt(index).values.forEach(requests::clear)
        }

        slots.clear()
        latest.clear()
        borrowed.clear()
    }

    private fun ask(medium: Medium, columnCount: Int, key: Int): Drawable? {
        val slot = Slot(medium.path)
        (slots[key] ?: HashMap<String, Slot>().also { slots.put(key, it) })[medium.path] = slot
        // a picture already in memory arrives before load() returns, and is drawn this frame
        isAsking = true
        val startedAt = System.nanoTime()
        try {
            Perf.section("zoom.ask") { loader.load(medium, columnCount, slot) }
        } finally {
            isAsking = false
            askingNanos += System.nanoTime() - startedAt
        }

        return slot.picture
    }

    private inner class Slot(private val path: String) : CustomTarget<Drawable>() {
        var picture: Drawable? = null
            private set

        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
            picture = resource
            latest[path] = resource
            if (!isAsking) {
                onArrived()
            }
        }

        override fun onLoadCleared(placeholder: Drawable?) {
            if (latest[path] === picture) {
                latest.remove(path)
            }

            picture = null
        }
    }
}
