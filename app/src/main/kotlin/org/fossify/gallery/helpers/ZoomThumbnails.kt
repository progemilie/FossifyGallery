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
private const val MISSING_PER_FRAME = 24

/** ...and for tiles already drawn with a picture of another size, which only come out sharper. */
private const val SHARPER_PER_FRAME = 6

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
    private var borrowedKey = Int.MIN_VALUE
    private var canBorrow = true

    /** Every request made, by key and then path; one that could not be made stays, empty. */
    private val slots = SparseArray<HashMap<String, Slot>>()

    /** The last picture of each medium to arrive, whatever it was asked for at. */
    private val latest = HashMap<String, Drawable>()

    private var missingBudget = MISSING_PER_FRAME
    private var sharperBudget = SHARPER_PER_FRAME
    private var isAsking = false

    /** Takes the pictures the grid's tiles are showing at [columnCount], by path. */
    fun borrow(columnCount: Int, pictures: Map<String, Drawable>) {
        borrowedKey = loader.keyOf(columnCount)
        borrowed.putAll(pictures)
    }

    fun startFrame() {
        missingBudget = MISSING_PER_FRAME
        sharperBudget = SHARPER_PER_FRAME
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
        if (slot == null && mayAsk && spend(hasPicture = stand != null)) {
            ask(medium, columnCount, key)?.let { return it }
        }

        return stand
    }

    private fun spend(hasPicture: Boolean) = if (hasPicture) {
        sharperBudget-- > 0
    } else {
        missingBudget-- > 0
    }

    /**
     * Asks for pictures of its own for [media] at [columnCount], whatever the frame's allowance. For
     * a count whose tiles are about to be rebound with the very pictures that were borrowed: asking
     * for them while the tiles still hold them costs no decode, and afterwards they are not there.
     */
    fun keep(media: Collection<Medium>, columnCount: Int) {
        val key = loader.keyOf(columnCount)
        media.forEach {
            if (slots[key]?.containsKey(it.path) != true) {
                ask(it, columnCount, key)
            }
        }
    }

    /** From here on only pictures asked for by the zoom itself are drawn. */
    fun stopBorrowing() {
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
        try {
            Perf.section("zoom.ask") { loader.load(medium, columnCount, slot) }
        } finally {
            isAsking = false
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
