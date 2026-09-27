package org.fossify.gallery.views

import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
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
 * It is drawn over the zoom rather than hidden outright, fading out while it follows the count it is
 * showing, so that its badges go gently rather than all at once. It comes back the same way, faded in
 * over the zoom's last frame once its tiles have their pictures - both show the same grid, so all
 * that changes is the badges returning.
 */
internal class GridHandover(private val grid: MyRecyclerView) {

    private var session: ZoomSession? = null
    private var fadeStartedAt = 0L
    private var onBack: (() -> Unit)? = null
    private var waitUntil = 0L

    private val fadeOut = object : Runnable {
        override fun run() {
            val session = session ?: return
            val elapsed = SystemClock.uptimeMillis() - fadeStartedAt
            val layer = shownLayer(session)
            if (elapsed >= FADE_OUT_MS || layer == null) {
                grid.alpha = 0f
                resetTransform()
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
        fadeStartedAt = SystemClock.uptimeMillis()
        grid.alpha = 1f
        grid.postOnAnimation(fadeOut)
    }

    /** Waits for the grid to have its pictures, fades it in, and calls [onBack] once it is. */
    fun handBack(onBack: () -> Unit) {
        this.onBack = onBack
        grid.removeCallbacks(fadeOut)
        resetTransform()
        grid.alpha = 0f
        waitUntil = SystemClock.uptimeMillis() + PICTURES_WAIT_MS
        grid.addOnScrollListener(onScroll)
        grid.postOnAnimation(waitForPictures)
    }

    /** Puts the grid back at once, skipping whatever of the fade was left. */
    fun finishNow() {
        grid.removeCallbacks(fadeOut)
        grid.removeCallbacks(waitForPictures)
        grid.animate().cancel()
        resetTransform()
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

    /** Scales and moves the grid onto where the zoom draws the count it is showing. */
    private fun follow(layer: ZoomLayer, session: ZoomSession) {
        val frame = session.frame
        val origin = session.startOrigin
        grid.pivotX = 0f
        grid.pivotY = 0f
        if (frame.horizontal) {
            grid.scaleX = layer.scaleAlong
            grid.scaleY = layer.scaleAcross
            grid.translationX = if (frame.reversed) {
                frame.width - layer.originAlong - layer.scaleAlong * (frame.width - origin)
            } else {
                layer.originAlong - layer.scaleAlong * origin
            }

            grid.translationY = layer.originAcross
        } else {
            grid.scaleX = layer.scaleAcross
            grid.scaleY = layer.scaleAlong
            grid.translationX = layer.originAcross
            grid.translationY = layer.originAlong - layer.scaleAlong * origin
        }
    }

    private fun resetTransform() {
        grid.scaleX = 1f
        grid.scaleY = 1f
        grid.translationX = 0f
        grid.translationY = 0f
    }

    /** Whether every tile on screen has its picture, or at least whatever stands in for a failed one. */
    private fun picturesShown() = grid.children.all { child ->
        val image = child.findViewById<ImageView>(R.id.medium_thumbnail) ?: return@all true
        image.drawable.let { it != null && it !is ColorDrawable }
    }
}
