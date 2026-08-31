package org.fossify.gallery.helpers

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import org.fossify.gallery.extensions.screenRect
import org.fossify.gallery.views.ContinuityOverlay
import org.fossify.commons.R as commonsR

/** The picture the viewer is showing this instant, and where on screen it is showing it. */
class DisplayedMedia(val rect: RectF, val image: Bitmap?)

/**
 * A viewer's half of the continuity transition: the tile's picture growing into the fullscreen one
 * on the way in, and the fullscreen one shrinking back into a tile on the way out. Worn by all
 * three fullscreen screens, which differ only in what they hand in below.
 *
 * Everything it moves is drawn over a grid that is still there - the viewer's window is translucent
 * - so the two windows read as one surface. See [ViewerTransition] for the hand-off between them.
 *
 * **A flight is measured against the photo, never against the screen.** A photo fitted inside the
 * screen often covers little more than half of it, so a flight sized by the screen overruns it and
 * is yanked back - see [landing].
 */
class ContinuityTransition(
    private val activity: Activity,
    private val overlay: ContinuityOverlay,
    /** Where the fullscreen picture sits, and so the bounds a flight is measured inside. */
    private val stage: View,
    /** Everything painting over the grid, faded together so the grid comes back through. */
    backdrops: () -> List<Drawable>,
    chrome: () -> List<View>,
    /** What the viewer is drawing and where, once it is drawing anything. */
    private val displayed: () -> DisplayedMedia?
) {
    private val scrim = Scrim(backdrops, chrome)

    private var animator: ValueAnimator? = null

    /** The tile the current media would fly back into, looked up ahead of being needed. */
    private var exitTile: ViewerTransition.Tile? = null
    private var exitPath = ""

    /** Set once the viewer is on its way out, so nothing left running puts the chrome back. */
    private var isClosing = false

    /** The proportions of the picture in flight, which is what a landing rect is measured from. */
    private var flightAspect = 1f

    /** Whether the flight is still drawn with the tile's own picture rather than the photo's. */
    private var awaitingPicture = false

    /** When the wait for the viewer to paint something began, so it cannot go on for ever. */
    private var settleStartedAt = 0L

    /**
     * Grows the tapped tile into the photo. Does nothing at all where there is nothing to grow
     * from - an external intent, a restored screen, or a platform too old to draw a window over a
     * live one - leaving the screen exactly as it was.
     */
    fun enter(path: String) {
        val tile = ViewerTransition.takeOpening()?.takeIf { ViewerTransition.isSupported }
        val picture = tile?.let { ViewerTransition.takeFlightPicture(path) }
        val flying = picture ?: tile?.image
        if (tile == null || flying == null) {
            // the window is see-through by theme, and nothing is going to be drawn through it
            activity.letGridShowThrough(false)
            return
        }

        activity.letGridShowThrough(true)
        scrim.backdrop = 0f
        scrim.chromeAlpha = 0f
        stage.alpha = 0f
        awaitingPicture = picture == null
        flightAspect = flying.aspect()

        // the overlay maps screen coordinates through its own, so it has to be placed first
        overlay.doOnLayout {
            if (isClosing) {
                return@doOnLayout
            }

            // a tile drawn cropped has to start cropped and unfold as it flies; one that has not
            // been handed the photo's own picture yet has nothing to unfold, so it holds its crop
            // until the picture turns up and unfolds over whatever is left of the flight
            val tileCrop = if (tile.isCropped) 1f else 0f
            val endCrop = if (awaitingPicture) tileCrop else 0f
            overlay.fly(flying, tile.frame, landing(), tileCrop, endCrop)

            animate(from = 0f, to = 1f) { t ->
                pickUpPicture(path)
                overlay.progress = t
                overlay.retarget(landing())
                scrim.backdrop = t
                scrim.chromeAlpha = ramp(t, CONTINUITY_CHROME_IN, 1f)
            }.doOnEnd { settle() }
        }
    }

    /**
     * Where the flight is heading: the photo's own rect once the viewer is drawing one, and until
     * then the flying picture's proportions fitted into the stage - which is the same rect, as long
     * as the two are the same picture.
     */
    private fun landing() = displayed()?.rect
        ?: ViewerTransition.restingRect(flightAspect, stage.screenRect())

    /**
     * Trades the tile's picture for the photo's the moment the fetch begun at the tap finishes.
     *
     * They are the same picture, so nothing about this is visible: the flight simply stops being
     * held at the tile's crop and starts unfolding, and gains proportions to aim by.
     */
    private fun pickUpPicture(path: String) {
        if (!awaitingPicture) {
            return
        }

        val picture = ViewerTransition.takeFlightPicture(path) ?: return
        awaitingPicture = false
        flightAspect = picture.aspect()
        overlay.handOver(picture, landing(), cropAtEnd = 0f)
    }

    /**
     * Hands the screen back to the viewer once it has something to hand it to - the same picture at
     * the same rect by then, so the swap is nothing at all. A slow photo simply leaves the flown
     * picture holding the screen, and one that lands unexpectedly is moved onto rather than cut to.
     */
    private fun settle() {
        if (isClosing) {
            return
        }

        scrim.backdrop = 1f
        scrim.chromeAlpha = 1f
        if (settleStartedAt == 0L) {
            settleStartedAt = SystemClock.uptimeMillis()
        }

        val shown = displayed()
        val gaveUp = SystemClock.uptimeMillis() - settleStartedAt > CONTINUITY_SETTLE_WAIT_MS
        if (shown == null) {
            if (gaveUp) {
                revealStage()
            } else {
                stage.postOnAnimation { settle() }
            }

            return
        }

        // where the picture actually is, which is not where it was aimed if the photo turned up
        // late and landed somewhere the flying thumbnail's proportions did not predict
        val from = overlay.currentRect()
        if (from == shown.rect) {
            revealStage()
            return
        }

        animate(from = 0f, to = 1f, duration = CONTINUITY_SETTLE_MS) { t ->
            overlay.retarget(lerp(from, shown.rect, t))
        }.doOnEnd { revealStage() }
    }

    private fun revealStage() {
        stage.alpha = 1f
        overlay.clear()
        activity.letGridShowThrough(false)
    }

    /**
     * The media on screen has changed, so the tile it would fly back into has too. Looked up now
     * rather than at the moment of closing: the grid has to scroll and lay out to answer, and a
     * finger already lifted cannot wait a frame for it.
     */
    fun onPathChanged(path: String) {
        if (!ViewerTransition.isSupported || path.isEmpty() || path == exitPath) {
            return
        }

        exitPath = path
        exitTile = null
        ViewerTransition.locate(path) { tile ->
            if (path == exitPath) {
                exitTile = tile
            }
        }
    }

    /**
     * Shrinks the media back into its tile and runs [finishNow] once it lands. Where there is
     * nothing to shrink - no tile, the item deleted or filtered out from under the grid, nothing
     * drawn to shrink with - the screen closes at once, naming the slide the theme leaves out: its
     * own close animation is nothing at all, so that a shrink can be drawn over the grid.
     */
    fun finishThrough(finishNow: () -> Unit) {
        if (!close(finishNow)) {
            activity.slideOutOnClose(finishNow)
        }
    }

    private fun close(onFinish: () -> Unit): Boolean {
        if (isClosing) {
            return true
        }

        val tile = exitTile?.takeIf { ViewerTransition.isSupported } ?: return false
        val shown = displayed() ?: return false
        val picture = shown.image ?: return false

        isClosing = true
        animator?.cancel()
        activity.letGridShowThrough(true)
        // the overlay takes over drawing the very picture the stage was drawing, at the very rect
        // it was drawing it at, so trading one for the other changes nothing on screen
        overlay.fly(picture, shown.rect, tile.frame, 0f, if (tile.isCropped) 1f else 0f)
        stage.alpha = 0f

        val backdropFrom = scrim.backdrop
        val chromeFrom = scrim.chromeAlpha
        animate(from = 0f, to = 1f) { t ->
            overlay.progress = t
            scrim.backdrop = backdropFrom * (1f - t)
            scrim.chromeAlpha = chromeFrom * (1f - ramp(t, 0f, CONTINUITY_CHROME_IN))
        }.doOnEnd {
            ViewerTransition.shrank()
            onFinish()
        }

        return true
    }

    private fun animate(
        from: Float,
        to: Float,
        duration: Long = CONTINUITY_DURATION_MS,
        onFrame: (Float) -> Unit
    ): ValueAnimator {
        return ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { onFrame(it.animatedValue as Float) }
            doOnEnd { if (animator === this) animator = null }
            animator = this
            start()
        }
    }

    /**
     * The layers standing between the eye and the grid behind this window, taken up and down
     * together: the backdrop the viewer paints over everything, and the chrome drawn on top of it.
     */
    private class Scrim(
        private val backdrops: () -> List<Drawable>,
        private val chrome: () -> List<View>
    ) {
        var backdrop: Float
            get() = (backdrops().firstOrNull()?.alpha ?: OPAQUE) / OPAQUE.toFloat()
            set(value) {
                val alpha = (value.coerceIn(0f, 1f) * OPAQUE).toInt()
                backdrops().forEach { it.alpha = alpha }
            }

        var chromeAlpha: Float
            get() = chrome().firstOrNull { it.isVisible }?.alpha ?: 1f
            set(value) {
                val alpha = value.coerceIn(0f, 1f)
                chrome().forEach { it.alpha = alpha }
            }
    }

    companion object {
        /**
         * Puts an overlay over the whole window, clear of the cutout padding the screens apply to
         * their own content - a flight starts at a tile that may well be under the notch.
         */
        fun overlayOver(activity: Activity): ContinuityOverlay {
            return ContinuityOverlay(activity).apply {
                isInvisible = true
                activity.findViewById<ViewGroup>(android.R.id.content).addView(
                    this,
                    ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                )
            }
        }

        private const val OPAQUE = 255

        private fun ramp(value: Float, start: Float, end: Float) =
            ((value - start) / (end - start)).coerceIn(0f, 1f)
    }
}

private fun Bitmap.aspect() = if (height == 0) 1f else width.toFloat() / height

/**
 * Asks for no window animation at all, in whichever terms the platform actually reads.
 *
 * res/anim/viewer_hold.xml says the same thing through the theme, which was the only way to say it
 * before API 34; from there up the system drives activity transitions itself and ignores the theme,
 * sliding the whole window up on the way in and down on the way out, over the top of a flight. Both
 * are needed - the theme below 34, this from 34 - and either alone leaves the flight buried under a
 * slide on some version or other.
 */
fun Activity.holdWindowStill() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
}

/**
 * Gives a viewer window's surface an alpha channel, without which the grid behind it is not
 * composited at all - being translucent by theme is not enough on its own. Only on while something
 * is flying: an opaque surface is a cheaper one to draw a fullscreen photo into.
 */
private fun Activity.letGridShowThrough(letThrough: Boolean) {
    if (ViewerTransition.isSupported) {
        window.setFormat(if (letThrough) PixelFormat.TRANSLUCENT else PixelFormat.OPAQUE)
    }
}

/**
 * Closes on a slide, this being the one exit that wants a window animation - the theme and
 * [holdWindowStill] between them leave the viewer none, so that every other exit is a shrink drawn
 * over the grid. Named through whichever API the platform honours.
 */
private fun Activity.slideOutOnClose(finishNow: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, commonsR.anim.slide_down)
        finishNow()
    } else {
        finishNow()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, commonsR.anim.slide_down)
    }
}

/** A rect [fraction] of the way from [from] to [to]. */
private fun lerp(from: RectF, to: RectF, fraction: Float) = RectF(
    from.left + (to.left - from.left) * fraction,
    from.top + (to.top - from.top) * fraction,
    from.right + (to.right - from.right) * fraction,
    from.bottom + (to.bottom - from.bottom) * fraction
)
