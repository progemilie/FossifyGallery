package org.fossify.gallery.views

import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.helpers.ZoomLayer

/** How long the grid takes to fade out from over a zoom as it starts, and back in once it ends. */
private const val FADE_OUT_MS = 120f
private const val FADE_IN_MS = 150L

/** The longest the grid is given to show its pictures before it fades in regardless. */
private const val PICTURES_WAIT_MS = 350L

/**
 * The grid's side of a zoom: getting out of the way as one starts and coming back as it ends. See
 * MediaGridZoom.
 *
 * It is drawn over the zoom rather than hidden outright, fading out while its tiles follow where the
 * zoom draws them, so that their badges go gently rather than all at once. It comes back the same
 * way, faded in over the zoom's last frame once its tiles have their pictures - both show the same
 * grid, so all that changes is the badges returning.
 */
internal class GridHandover(private val grid: MyRecyclerView) {

    private var session: ZoomSession? = null
    private var fadeStartedAt = 0L
    private var onBack: (() -> Unit)? = null
    private var waitUntil = 0L
    private val square = RectF()

    private val fadeOut = object : Runnable {
        override fun run() {
            val session = session ?: return
            val elapsed = SystemClock.uptimeMillis() - fadeStartedAt
            val layer = shownLayer(session)
            if (elapsed >= FADE_OUT_MS || layer == null) {
                grid.alpha = 0f
                settleTiles()
                return
            }

            grid.alpha = 1f - elapsed / FADE_OUT_MS
            follow(layer, session)
            grid.postOnAnimation(this)
        }
    }

    private val waitForPictures = object : Runnable {
        override fun run() {
            if (grid.isLayoutRequested || (!picturesShown() && SystemClock.uptimeMillis() < waitUntil)) {
                grid.postOnAnimation(this)
            } else {
                grid.animate().alpha(1f).setDuration(FADE_IN_MS).withEndAction { handedBack() }
            }
        }
    }

    // anything moving the grid while it comes back would leave the zoom's last frame behind it
    private val onScroll = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dx != 0 || dy != 0) {
                finishNow()
            }
        }
    }

    fun start(session: ZoomSession) {
        this.session = session
        // a simplified tile is only its picture, which the zoom draws just the same: nothing to fade
        if (session.drawing.counts.isSimplified(session.startRung)) {
            grid.alpha = 0f
            return
        }

        fadeStartedAt = SystemClock.uptimeMillis()
        grid.alpha = 1f
        grid.postOnAnimation(fadeOut)
    }

    /**
     * Waits for the grid to have its pictures, fades it in, and calls [onBack] once it is. A grid the
     * zoom left [changed] starts from nothing; one it did not fades back from wherever it had got to.
     */
    fun handBack(changed: Boolean, onBack: () -> Unit) {
        this.onBack = onBack
        grid.removeCallbacks(fadeOut)
        settleTiles()
        if (changed) {
            grid.alpha = 0f
        }

        waitUntil = SystemClock.uptimeMillis() + PICTURES_WAIT_MS
        grid.addOnScrollListener(onScroll)
        grid.postOnAnimation(waitForPictures)
    }

    /** Puts the grid back at once, skipping whatever of the fade was left. */
    fun finishNow() {
        grid.removeCallbacks(fadeOut)
        grid.removeCallbacks(waitForPictures)
        grid.animate().cancel()
        settleTiles()
        grid.alpha = 1f
        handedBack()
    }

    private fun handedBack() {
        grid.removeOnScrollListener(onScroll)
        session = null
        val done = onBack
        onBack = null
        done?.invoke()
    }

    /** The count the grid itself is showing, while the zoom still draws it. */
    private fun shownLayer(session: ZoomSession): ZoomLayer? {
        val scene = session.scene
        return listOfNotNull(scene.under, scene.over).firstOrNull { it.rung == session.startRung }
    }

    /**
     * Puts each of the grid's tiles and headers over where the zoom draws it. One by one rather than
     * the grid as a whole: the zoom scales rows and leaves headers their length, which no single
     * transform of the grid can follow - it would drift from the zoom by every header above it.
     */
    private fun follow(layer: ZoomLayer, session: ZoomSession) {
        val layout = layer.layout
        val sections = layout.sections
        val frame = session.frame
        for (child in grid.children) {
            val position = grid.getChildAdapterPosition(child)
            val section = if (position == RecyclerView.NO_POSITION) -1 else sections.sectionOf(position)
            if (section < 0) {
                continue
            }

            if (sections.isHeaded(section) && position == sections.headerPosition(section)) {
                // a header keeps its size, and its place across
                place(child, child.left.toFloat(), layer.headerStart(section), 1f)
            } else {
                val index = position - sections.firstMedium(section)
                val span = index % layout.columns
                val along = layer.tileAlong(section, index / layout.columns)
                frame.square(along, layer.tileAcross(span), layer.tileSize(span), square)
                place(child, square.left - frame.left, square.top - frame.top, layer.scaleAcross)
            }
        }
    }

    /** Scales [child] by [scale] and moves it to [left], [top], about whatever pivot it has. */
    private fun place(child: View, left: Float, top: Float, scale: Float) {
        child.scaleX = scale
        child.scaleY = scale
        child.translationX = left - child.left - child.pivotX * (1 - scale)
        child.translationY = top - child.top - child.pivotY * (1 - scale)
    }

    private fun settleTiles() {
        for (child in grid.children) {
            child.scaleX = 1f
            child.scaleY = 1f
            child.translationX = 0f
            child.translationY = 0f
        }
    }

    /** Whether every tile on screen has its picture, or at least whatever stands in for a failed one. */
    private fun picturesShown() = grid.children.all { child ->
        val image = child.findViewById<ImageView>(R.id.medium_thumbnail) ?: return@all true
        image.drawable.let { it != null && it !is ColorDrawable }
    }
}
