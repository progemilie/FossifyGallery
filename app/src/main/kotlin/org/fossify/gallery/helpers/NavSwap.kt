package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isNotEmpty
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.realScreenSize

private const val ANIMATION_DURATION = 200L

/** How long the arriving grid is given to find something to carry before the slide runs anyway. */
private const val CONTENT_WAIT_CAP = 800L

/** Carries which side the arriving screen's content should come in from. */
const val NAV_SWAP_FROM_LEFT = "nav_swap_from_left"

/**
 * The swap between the folder grid and the all media grid, which are two activities rather than two
 * pages of one screen.
 *
 * The navigation pill is drawn again by whichever of them arrives, so the only way it can appear to
 * have stayed still across the handover is for the window itself never to move: the transition is
 * taken off entirely and the arriving screen slides its own content in from the side the pill was
 * tapped towards, leaving both the pill and the search bar exactly where they were.
 */
fun Activity.startNavSwap(intent: Intent, fromLeft: Boolean) {
    intent.putExtra(NAV_SWAP_FROM_LEFT, fromLeft)
    startActivity(intent)
    suppressSwapTransition(isEntering = false)
    finish()
}

/**
 * Slides [content] in, if this screen was opened by a swap. Called with the content rather than the
 * whole window: the chrome over it is what has to hold still, and the content is inside the holder
 * the pill frosts, so the movement shows through the glass along with everything else.
 *
 * [grid] is what has to have something in it first. Both screens fill theirs off a query that
 * outlives `onCreate`, and a slide that ran before that came back would carry an empty grid past -
 * the swap would look like nothing moving at all, and the pictures would simply appear afterwards.
 * The cap is for a grid that is legitimately empty, which is never going to have anything to wait
 * for and still has to be brought back on screen.
 */
fun Activity.playNavSwapEntry(content: View, grid: RecyclerView) {
    if (!intent.hasExtra(NAV_SWAP_FROM_LEFT)) {
        return
    }

    val fromLeft = intent.getBooleanExtra(NAV_SWAP_FROM_LEFT, false)
    // an activity coming back to this one must not slide a second time
    intent.removeExtra(NAV_SWAP_FROM_LEFT)
    suppressSwapTransition(isEntering = true)

    // parked before the first frame is drawn, and clear of the screen rather than of its own width:
    // nothing has been measured this early
    val offScreen = realScreenSize.x.toFloat()
    content.translationX = if (fromLeft) -offScreen else offScreen

    var hasSlid = false
    val slide = {
        if (!hasSlid) {
            hasSlid = true
            content.animate()
                .translationX(0f)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    grid.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            if (hasSlid || grid.isNotEmpty()) {
                grid.viewTreeObserver.removeOnPreDrawListener(this)
                slide()
            }

            return true
        }
    })

    content.postDelayed({ slide() }, CONTENT_WAIT_CAP)
}

/**
 * Takes the window animation off one end of a swap. Below Android 14 the leaving screen speaks for
 * the whole transition; from 14 on an activity can only say what its own opening or closing looks
 * like, so both ends have to ask for themselves.
 */
private fun Activity.suppressSwapTransition(isEntering: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val transition = if (isEntering) {
            Activity.OVERRIDE_TRANSITION_OPEN
        } else {
            Activity.OVERRIDE_TRANSITION_CLOSE
        }

        overrideActivityTransition(transition, 0, 0)
    } else if (!isEntering) {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
