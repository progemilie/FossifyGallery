package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.lowResPhotoRequest
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

/**
 * The hand-off between a media grid and the fullscreen viewer, so the two of them draw one
 * continuous motion across the window boundary: the tile grows into the photo on the way in, and
 * the photo shrinks back into whichever tile was swiped to on the way out.
 *
 * Static for the same reason [PeekSession] is - these are separate activities, and what has to
 * cross between them is a live bitmap rather than a second decode of it, which no Intent will carry.
 *
 * **A flight is drawn with the photo's own picture, never with the tile's.** With crop thumbnails
 * on - the default - a tile's bitmap has no edges left to unfold into a fullscreen photo, so
 * [flightPicture] fetches the uncropped copy the viewer itself paints first
 * ([org.fossify.gallery.extensions.lowResPhotoRequest]) - making the hand-over at the end of a
 * flight nothing happening at all rather than a cross-fade.
 *
 * The illusion only holds while the grid is still drawn underneath, so the viewer's window is
 * translucent - and a translucent activity may not ask for an orientation before API 28, which the
 * viewer does. Below that [isSupported] is false and every screen falls back to what it did before.
 */
object ViewerTransition {
    val isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /**
     * A tile as the viewer needs to see it.
     *
     * [frame] is the thumbnail view's whole rect, crop and all, and [isCropped] says how the tile
     * fills it - which is the shape of the motion, since a flight has to start drawn exactly as the
     * tile is and end drawn exactly as the fullscreen photo is.
     *
     * [image] is what the tile has this instant, kept only as the stand-in a flight sets off with
     * where [flightPicture] has not arrived yet.
     */
    class Tile(val frame: RectF, val image: Bitmap?, val isCropped: Boolean)

    /**
     * Answers where the tile for a path is, having first put the grid onto it. Asked of a grid that
     * is paused behind the viewer, so the scrolling it does to answer is never seen.
     *
     * Asynchronous because a grid that had to scroll has no laid-out tile to measure until the pass
     * after it does.
     */
    fun interface Anchor {
        fun locate(path: String, onLocated: (Tile?) -> Unit)
    }

    private var pending: Tile? = null
    private var anchor: Anchor? = null

    /**
     * The uncropped picture the tapped photo is flown with, fetched from the moment of the tap so
     * that it is usually already in hand by the time the viewer is laid out and the flight begins.
     */
    private var flightPicture: Bitmap? = null
    private var flightPath = ""
    private var flightTarget: CustomTarget<Drawable>? = null

    /**
     * Whether the viewer flew back into a tile. The grid points out where it landed otherwise, and
     * doing both would be a bounce on the end of an otherwise continuous motion.
     */
    private var didShrink = false

    /** Said by the viewer as it lands, and read once by the grid it landed in. */
    fun shrank() {
        didShrink = true
    }

    fun takeDidShrink(): Boolean = didShrink.also { didShrink = false }

    /** The tile tapped, taken by the viewer as it comes up. */
    fun takeOpening(): Tile? = pending.also { pending = null }

    /** The uncropped picture for [path], if the fetch begun at the tap has finished. */
    fun takeFlightPicture(path: String): Bitmap? =
        flightPicture.takeIf { flightPath == path }

    /** The grid holds this for as long as the viewer it opened is up, and no longer. */
    private fun setAnchor(anchor: Anchor?) {
        this.anchor = anchor
        if (anchor == null) {
            pending = null
            flightPicture = null
            flightPath = ""
            flightTarget = null
        }
    }

    fun locate(path: String, onLocated: (Tile?) -> Unit) {
        val anchor = anchor
        if (anchor == null) {
            onLocated(null)
        } else {
            anchor.locate(path, onLocated)
        }
    }

    /**
     * Hands the tile showing [path] over to whichever fullscreen screen is about to be started, and
     * puts the picture it will be flown with on its way. Answers whether there is a flight to make:
     * false where the tile is not on screen, where it has drawn nothing yet, or where the platform
     * will not let the viewer's window be translucent.
     *
     * [adapter]'s grid stays on as the [Anchor] for as long as the screen it opened is up, which is
     * what answers where to fly a photo back to however far it was swiped from the tile it opened.
     * Nothing here says how to start that screen - see res/anim/viewer_hold.xml.
     */
    fun beginFlight(context: Context, adapter: MediaAdapter?, items: List<ThumbnailItem>, path: String): Boolean {
        val medium = items.filterIsInstance<Medium>().firstOrNull { it.path == path } ?: return false
        val navigator = adapter?.gridNavigator?.takeIf { isSupported } ?: return false
        val isCropped = context.config.cropThumbnails
        // asked without a scroll, so the answer comes back before this line does
        var tapped: Tile? = null
        navigator.locateTile(medium.path, isCropped, scroll = false) { tapped = it }
        val tile = tapped ?: return false
        val flightAnchor = Anchor { wantedPath, onLocated ->
            navigator.locateTile(wantedPath, isCropped, scroll = true, onLocated = onLocated)
        }

        pending = tile
        didShrink = false
        setAnchor(flightAnchor)
        fetchFlightPicture(context, medium)
        dropWhenDestroyed(context, flightAnchor)
        return true
    }

    /**
     * Starts the uncropped picture decoding now rather than when the flight wants it. The viewer is
     * a good hundred milliseconds off being laid out, which is time enough for this to land first
     * and for the flight to set off already knowing the photo's proportions - and so already aimed
     * at exactly where the photo will come to rest.
     */
    private fun fetchFlightPicture(context: Context, medium: Medium) {
        flightPicture = null
        flightPath = medium.path
        flightTarget?.let { Glide.with(context).clear(it) }

        // loaded through the shared request so the viewer finds it in memory under the same key.
        // The pixels are copied out all the same: Glide hands its own back to the pool as soon as
        // the grid is cleared, which a flight outlives
        val target = object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                if (flightPath != medium.path) {
                    return
                }

                flightPicture = runCatching {
                    val shown = (resource as? BitmapDrawable)?.bitmap
                    shown?.copy(shown.config ?: Bitmap.Config.RGB_565, false)
                        ?: resource.toBitmap()
                }.getOrNull()
            }

            override fun onLoadCleared(placeholder: Drawable?) = Unit
        }

        flightTarget = target
        context.lowResPhotoRequest(medium.path, medium.getKey()).into(target)
    }

    /**
     * The grid screens drop their own anchor as they come back up, which covers every ordinary way
     * out of a viewer. One destroyed behind it instead - a theme change, a trimmed process - never
     * does, and this being static its adapter would hold the whole activity alive.
     */
    private fun dropWhenDestroyed(context: Context, flightAnchor: Anchor) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle ?: return
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (anchor === flightAnchor) {
                    setAnchor(null)
                }

                lifecycle.removeObserver(this)
            }
        })
    }

    /** Where a picture of [aspect] proportions comes to rest, fitted inside [bounds]. */
    fun restingRect(aspect: Float, bounds: RectF): RectF {
        if (aspect <= 0f || bounds.isEmpty) {
            return RectF(bounds)
        }

        val scale = minOf(bounds.width() / aspect, bounds.height())
        val height = scale
        val width = scale * aspect
        return RectF(
            bounds.centerX() - width / 2,
            bounds.centerY() - height / 2,
            bounds.centerX() + width / 2,
            bounds.centerY() + height / 2
        )
    }
}
