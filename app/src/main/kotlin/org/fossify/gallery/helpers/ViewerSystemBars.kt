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
 * The system bars following a viewer's chrome, except in the landscape layout, where the status bar
 * stays hidden. Split off BaseViewerActivity for detekt's function-count threshold.
 */
class ViewerSystemBars(
    private val activity: AppCompatActivity,
    private val hasLandscapeLayout: () -> Boolean,
    private val isChromeShown: () -> Boolean,
    private val onLandscapeLayoutChanged: () -> Unit,
) {
    /** Never in multi-window mode, where the system bars are not the app's to hide. */
    val isInLandscapeLayout: Boolean
        get() = hasLandscapeLayout() && !activity.isInMultiWindowMode &&
            activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private var wasInLandscapeLayout = false

    /** Called from onCreate, so the status bar is already leaving as the photo grows in. */
    fun attach() {
        wasInLandscapeLayout = isInLandscapeLayout
        if (wasInLandscapeLayout) {
            update(chromeShown = true)
        }
    }

    /** Called on configuration and multi-window changes, in either order; acts only on a change of layout. */
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
     * Shows the bars with the chrome and hides them without it - except the landscape status bar, which
     * a swipe only brings back for a moment, over the top of the screen.
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

    /** Ignoring visibility, so nothing jumps as the bars come and go, and without the landscape status bar. */
    fun layoutInsets(insets: WindowInsetsCompat): Insets {
        val bars = insets.getInsetsIgnoringVisibility(Type.systemBars())
        return if (isInLandscapeLayout) Insets.of(bars.left, 0, bars.right, bars.bottom) else bars
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
