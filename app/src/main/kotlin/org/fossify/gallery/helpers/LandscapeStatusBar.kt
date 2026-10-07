package org.fossify.gallery.helpers

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.core.graphics.Insets
import androidx.core.util.Consumer
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.fossify.commons.activities.EdgeToEdgeActivity
import org.fossify.commons.extensions.updatePaddingWithBase
import org.fossify.commons.views.MyAppBarLayout
import org.fossify.gallery.activities.BaseViewerActivity

/**
 * No screen shows the status bar in landscape, where a window has height to spare least of all. Every
 * screen hides it as it starts and lays itself out as though it was not there, so a swipe from the top
 * brings it back over the screen for a moment and moves nothing. The viewers put it the way their chrome
 * is instead, through [ViewerSystemBars]. Never in multi-window mode, where the bars are not the app's to
 * take away.
 */
object LandscapeStatusBar {

    /** Puts each screen's status bar the way [isHidden] says as it starts - registered once, in App. */
    val everyScreen: Application.ActivityLifecycleCallbacks = OnStarted(::onStarted)

    /** Whether [activity] fills a landscape screen, where the status bar never shows. */
    fun isHidden(activity: Activity) = !activity.isInMultiWindowMode &&
        activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /**
     * Keeps a dialog filling [activity]'s screen to the same: being the window on top, it is the one the
     * system asks about the status bar, and would put it back over the screen. Turned with it, too.
     */
    fun follow(dialog: ComponentDialog, activity: ComponentActivity) {
        val window = dialog.window ?: return
        val onTurned = Consumer<Configuration> { put(window, isHidden(activity)) }
        onTurned.accept(activity.resources.configuration)
        activity.addOnConfigurationChangedListener(onTurned)
        dialog.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                activity.removeOnConfigurationChangedListener(onTurned)
            }
        })
    }

    private fun put(window: Window, hidden: Boolean) {
        WindowInsetsControllerCompat(window, window.decorView).run {
            if (hidden) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(Type.statusBars())
            } else {
                show(Type.statusBars())
            }
        }
    }

    private fun onStarted(activity: Activity) {
        if (activity is BaseViewerActivity) {
            return
        }

        put(activity.window, isHidden(activity))

        // commons reads the status bar whether it shows or not, from a listener on the decor view - so
        // it is taken out here, just below, and everything the screen lays out sees the insets without it
        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(android.R.id.content)) { _, insets ->
            if (isHidden(activity)) withoutStatusBar(insets) else insets
        }
    }

    private fun withoutStatusBar(insets: WindowInsetsCompat) = WindowInsetsCompat.Builder(insets)
        .setInsets(Type.statusBars(), Insets.NONE)
        .setInsetsIgnoringVisibility(Type.statusBars(), Insets.NONE)
        .build()
}

/** The one moment of a screen's life the status bar is put at. */
private class OnStarted(private val started: (Activity) -> Unit) : Application.ActivityLifecycleCallbacks {
    override fun onActivityStarted(activity: Activity) = started(activity)

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}

/**
 * Commons' setupEdgeToEdge, with [padTopSystem] padded from below [LandscapeStatusBar] rather than from
 * the decor view, where commons would leave the hidden status bar's room empty above them. Each pads
 * itself from the insets that reach it - as commons' bars do on their own already, so those are left be.
 */
fun EdgeToEdgeActivity.fitSystemBars(
    padTopSystem: List<View> = emptyList(),
    padBottomSystem: List<View> = emptyList(),
    padBottomImeAndSystem: List<View> = emptyList(),
) {
    setupEdgeToEdge(padBottomSystem = padBottomSystem, padBottomImeAndSystem = padBottomImeAndSystem)
    padTopSystem.filterNot { it is MyAppBarLayout }.forEach { view ->
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            view.updatePaddingWithBase(top = insets.getInsetsIgnoringVisibility(Type.systemBars()).top)
            insets
        }
    }
}
