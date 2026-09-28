package org.fossify.gallery.views

import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.helpers.GridPinchZoom
import org.fossify.gallery.helpers.GridZoom
import org.fossify.gallery.helpers.Perf
import org.fossify.gallery.models.ThumbnailItem
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * How much the fingers' separation has to grow, or shrink, to bring the grid a whole count along.
 * More than a count's growth in the tiles themselves at most counts, so that a pinch has to mean it:
 * a small movement never runs through several counts.
 */
private const val SPREAD_PER_STEP = 1.6f

/** Settling onto a count: the least it takes, what each step still to go adds, and the most. */
private const val SETTLE_MIN_MS = 160f
private const val SETTLE_PER_STEP_MS = 160f
private const val SETTLE_MAX_MS = 320f

/** Fingers lifting faster than this many counts a second carry the zoom on to the next count. */
private const val FLING_STEPS_PER_SECOND = 3f

/** Fingers held still this long before lifting were not flinging, whatever they did before. */
private const val STILL_MS = 80L

/** A zoom coming to rest less than this far from where it began leaves the grid untouched. */
private const val HALF_PIXEL = 0.5f

private const val MS_PER_SECOND = 1000f

/**
 * The media grid zoomed from one column count to another by pinching, drawn as the fingers move
 * rather than stepped once they have moved far enough. [ZoomScene][org.fossify.gallery.helpers.ZoomScene]
 * says what is drawn between two counts, [GridZoomOverlay] draws it.
 *
 * The grid itself is left alone while a zoom is on - nothing about it changes until the fingers lift
 * and the zoom settles on a count. Only then is it put at that count and scrolled to lie exactly as
 * the zoom last drew it, and [GridHandover] brings it back over the zoom's last frame.
 */
class MediaGridZoom(
    grid: MyRecyclerView,
    private val overlay: GridZoomOverlay,
    private val host: Host,
) : GridPinchZoom.Listener {

    interface Host {
        val ladder: GridZoom
        val adapter: MediaAdapter?

        /** What the grid binds at the full counts, headers and all; the simplified ones drop them. */
        val items: List<ThumbnailItem>

        /** Puts the grid at [columnCount] with nothing animated: the zoom has drawn the change already. */
        fun applyColumnCount(columnCount: Int)

        fun scrollTo(position: Int, offset: Int)

        /** The grid is the grid's own again. */
        fun onZoomFinished()
    }

    private val setup = ZoomSetup(grid, overlay, host)
    private val handover = GridHandover(grid)
    private var session: ZoomSession? = null
    private var level = 0f

    /** The level the fingers' spread is measured from: where the zoom was as they came down. */
    private var pinchFrom = 0f
    private var velocity = 0f
    private var movedAt = 0L
    private var settle: ValueAnimator? = null
    private var isLanding = false

    /** Whether a zoom is drawing the grid, which nothing else should change underneath it. */
    val isActive get() = session != null

    override fun onPinchStart(focusX: Float, focusY: Float): Boolean {
        val session = session
        // one still settling is carried on from where it has got to, rather than landed and begun
        // afresh - a landed grid has a layout to catch up on before a zoom can be read off it
        if (session != null && !isLanding) {
            settle?.let {
                settle = null
                it.cancel()
            }

            session.focusOn(focusX, focusY)
        } else {
            // one handing back is seen to the end first
            finishNow()
            if (!begin(focusX, focusY)) {
                return false
            }
        }

        pinchFrom = level
        velocity = 0f
        movedAt = SystemClock.uptimeMillis()
        return true
    }

    override fun onPinch(spread: Float) {
        if (session == null || settle != null || isLanding) {
            return
        }

        val next = pinchFrom - ln(spread) / ln(SPREAD_PER_STEP)
        val now = SystemClock.uptimeMillis()
        val speed = (next - level) * MS_PER_SECOND / (now - movedAt).coerceAtLeast(1)
        velocity = (velocity + speed) / 2
        movedAt = now
        moveTo(next)
    }

    override fun onPinchEnd() {
        val session = session ?: return
        if (settle != null || isLanding) {
            return
        }

        if (SystemClock.uptimeMillis() - movedAt > STILL_MS) {
            velocity = 0f
        }

        val target = when {
            velocity > FLING_STEPS_PER_SECOND -> ceil(level)
            velocity < -FLING_STEPS_PER_SECOND -> floor(level)
            else -> level.roundToInt().toFloat()
        }

        settleTo(target.toInt().coerceIn(0, session.rungs.lastIndex))
    }

    /** One count in around [x], [y]: a tap on the simplified grid, whose tiles can do nothing else. */
    fun zoomInAt(x: Float, y: Float) {
        finishNow()
        if (begin(x, y)) {
            session?.let { settleTo((it.startRung - 1).coerceAtLeast(0)) }
        }
    }

    /** Brings any zoom to rest, on the count it was settling on if any, and hands the grid straight back. */
    fun finishNow() {
        val session = session ?: return
        // ending it lands it
        settle?.end()
        if (!isLanding) {
            moveTo(level.roundToInt().coerceIn(0, session.rungs.lastIndex).toFloat())
            land()
        }

        handover.finishNow()
    }

    private fun begin(focusX: Float, focusY: Float): Boolean {
        val session = Perf.section("zoom.setup") {
            setup.start(focusX, focusY) { overlay.postInvalidateOnAnimation() }
        } ?: return false
        this.session = session
        level = session.startRung.toFloat()
        overlay.drawing = session.drawing
        overlay.visibility = View.VISIBLE
        handover.start(session)
        return true
    }

    private fun moveTo(next: Float) {
        level = next
        Perf.section("zoom.update") { session?.scene?.update(next) }
        overlay.invalidate()
    }

    private fun settleTo(target: Int) {
        val distance = abs(target - level)
        if (distance == 0f) {
            land()
            return
        }

        settle = ValueAnimator.ofFloat(level, target.toFloat()).apply {
            duration = (SETTLE_MIN_MS + SETTLE_PER_STEP_MS * distance).coerceAtMost(SETTLE_MAX_MS).toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { moveTo(it.animatedValue as Float) }
            doOnEnd {
                // a cancelled settle has been taken over by whatever cancelled it
                if (settle === it) {
                    settle = null
                    land()
                }
            }

            start()
        }
    }

    /** Puts the grid at the count the zoom came to rest on, lying exactly as the zoom last drew it. */
    private fun land() {
        val session = session ?: return
        isLanding = true
        val scene = session.scene
        val rung = scene.restRung
        val changed = rung != session.startRung || abs(scene.restOrigin - session.startOrigin) >= HALF_PIXEL
        val thumbnails = session.drawing.thumbnails
        thumbnails.stopSharpening()
        if (changed) {
            // the tiles are about to be rebound, and the pictures borrowed from them go with them
            thumbnails.stopBorrowing(session.restingMedia())
            if (rung != session.startRung) {
                host.applyColumnCount(session.rungs[rung])
            }

            session.restPosition()?.let { (position, offset) -> host.scrollTo(position, offset) }
        }

        handover.handBack(changed, ::finish)
    }

    private fun finish() {
        val session = session ?: return
        this.session = null
        isLanding = false
        overlay.drawing = null
        overlay.visibility = View.INVISIBLE
        session.drawing.thumbnails.release()
        host.onZoomFinished()
    }
}
