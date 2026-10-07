package org.fossify.gallery.helpers

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
 * The system bars following a viewer's chrome, except the status bar in landscape, which stays hidden
 * as it does on every screen ([LandscapeStatusBar]). Split off BaseViewerActivity for detekt's
 * function-count threshold.
 */
class ViewerSystemBars(
    private val activity: AppCompatActivity,
    private val hasLandscapeLayout: () -> Boolean,
    private val isChromeShown: () -> Boolean,
    private val onLandscapeLayoutChanged: () -> Unit,
) {
    private val isStatusBarHidden get() = LandscapeStatusBar.isHidden(activity)

    /** Never in multi-window mode, where the system bars are not the app's to hide. */
    val isInLandscapeLayout: Boolean
        get() = hasLandscapeLayout() && isStatusBarHidden

    private var wasStatusBarHidden = false

    /** Called from onCreate, so the status bar is already leaving as the photo grows in. */
    fun attach() {
        wasStatusBarHidden = isStatusBarHidden
        if (wasStatusBarHidden) {
            update(chromeShown = true)
        }
    }

    /** Called on configuration and multi-window changes, in either order; acts only as the status bar comes or goes. */
    fun onWindowChanged() {
        if (isStatusBarHidden == wasStatusBarHidden) {
            return
        }

        wasStatusBarHidden = isStatusBarHidden
        update(isChromeShown())
        if (hasLandscapeLayout()) {
            onLandscapeLayoutChanged()
        }

        activity.findViewById<View>(android.R.id.content).requestApplyInsets()
    }

    /**
     * Shows the bars with the chrome and hides them without it - except the landscape status bar, which
     * a swipe only brings back for a moment, over the top of the screen.
     */
    fun update(chromeShown: Boolean) {
        when {
            !chromeShown -> activity.hideSystemUI()
            isStatusBarHidden -> WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                show(Type.navigationBars())
                hide(Type.statusBars())
            }

            else -> activity.showSystemUI()
        }
    }

    /** Ignoring visibility, so nothing jumps as the bars come and go, and without the landscape status bar. */
    fun layoutInsets(insets: WindowInsetsCompat): Insets {
        val bars = insets.getInsetsIgnoringVisibility(Type.systemBars())
        return if (isStatusBarHidden) Insets.of(bars.left, 0, bars.right, bars.bottom) else bars
    }

    /**
     * Where a strip along the foot ends: on the navigation bar, or where none lies along the foot, clear
     * of the edge by the room a video's playhead hangs below its frames.
     */
    fun footInset(insets: WindowInsetsCompat) =
        max(layoutInsets(insets).bottom, activity.resources.getDimensionPixelSize(R.dimen.viewer_strip_edge_gap))

    /** Clear of side bars, and of a cutout unless the content is already padded clear of it. */
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
