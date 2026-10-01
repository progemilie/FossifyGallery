package org.fossify.gallery.helpers

import android.content.res.Configuration
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.WindowInsetsControllerCompat
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.hideSystemUI
import org.fossify.gallery.extensions.showSystemUI
import kotlin.math.max

/**
 * The system bars as a viewer keeps them: up and away with its own chrome, except in the viewer's
 * landscape layout, where the status bar stays away for good and its room goes to the chrome -
 * height being what a landscape window has least of.
 *
 * Split off BaseViewerActivity, which would otherwise cross detekt's function-count threshold.
 */
class ViewerSystemBars(
    private val activity: AppCompatActivity,
    private val hasLandscapeLayout: () -> Boolean,
    private val isChromeShown: () -> Boolean,
    private val onLandscapeLayoutChanged: () -> Unit,
) {
    /**
     * Whether the window is in the landscape layout. Not in multi-window mode, where it has only a
     * share of the screen and the system bars are not the app's to take away.
     */
    val isInLandscapeLayout: Boolean
        get() = hasLandscapeLayout() && !activity.isInMultiWindowMode &&
            activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // the configuration change callbacks come for rotations that leave the layout as it was too
    private var wasInLandscapeLayout = false

    /** Asked from onCreate, before the first frame, so the status bar is already leaving as the photo grows in. */
    fun attach() {
        wasInLandscapeLayout = isInLandscapeLayout
        if (wasInLandscapeLayout) {
            update(chromeShown = true)
        }
    }

    /**
     * The configuration or the multi-window mode has changed. Which of the two callbacks comes first
     * differs, so both ask, and only a change of layout does anything.
     */
    fun onWindowChanged() {
        if (isInLandscapeLayout == wasInLandscapeLayout) {
            return
        }

        wasInLandscapeLayout = isInLandscapeLayout
        update(isChromeShown())
        onLandscapeLayoutChanged()
        activity.findViewById<View>(android.R.id.content).requestApplyInsets()
    }

    /**
     * Puts the bars the way the viewer's own chrome is: up with it, and away without it. In the
     * landscape layout the status bar stays away either way, and a swipe brings it back only for a
     * moment, over the top of the screen rather than pushing anything down.
     */
    fun update(chromeShown: Boolean) {
        when {
            !chromeShown -> activity.hideSystemUI()
            isInLandscapeLayout -> WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                show(Type.navigationBars())
                hide(Type.statusBars())
            }

            else -> activity.showSystemUI()
        }
    }

    /**
     * Where the bars are, as everything in a viewer is laid out around them: whether or not they are
     * up, so nothing jumps when they come and go, and without the status bar in the landscape layout.
     */
    fun layoutInsets(insets: WindowInsetsCompat): Insets {
        val bars = insets.getInsetsIgnoringVisibility(Type.systemBars())
        return if (isInLandscapeLayout) Insets.of(bars.left, 0, bars.right, bars.bottom) else bars
    }

    /**
     * How far above the bottom of the screen a strip along the very foot ends: on the navigation bar,
     * or - where none lies along the foot, as in landscape with buttons at the side - clear of the edge
     * by the room a video's playhead hangs below its frames.
     */
    fun footInset(insets: WindowInsetsCompat) =
        max(layoutInsets(insets).bottom, activity.resources.getDimensionPixelSize(R.dimen.viewer_strip_edge_gap))

    /**
     * How far in from either side the viewer's chrome keeps: clear of a navigation bar at the side,
     * and of a cutout unless the content is already padded clear of it - which it is with the notch
     * area left unused, see BaseViewerActivity.
     */
    fun sideInsets(insets: WindowInsetsCompat): Insets {
        val system = insets.getInsetsIgnoringVisibility(Type.systemBars())
        val cutout = insets.getInsetsIgnoringVisibility(Type.displayCutout())
        return if (activity.config.showNotch) {
            Insets.of(max(system.left, cutout.left), 0, max(system.right, cutout.right), 0)
        } else {
            Insets.of(if (cutout.left > 0) 0 else system.left, 0, if (cutout.right > 0) 0 else system.right, 0)
        }
    }
}
