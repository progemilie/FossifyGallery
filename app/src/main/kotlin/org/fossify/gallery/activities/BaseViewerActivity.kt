package org.fossify.gallery.activities

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.updateMarginWithBase
import org.fossify.commons.extensions.updatePaddingWithBase
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.ViewerSystemBars
import org.fossify.gallery.helpers.holdWindowStill

abstract class BaseViewerActivity : SimpleActivity() {
    private companion object {
        /** Above this a background is light enough to need dark icons drawn over it. */
        const val LIGHT_LUMINANCE = 0.5
    }

    override val padCutout: Boolean = false
    abstract val contentHolder: View
    abstract val appBarLayout: AppBarLayout

    /** Opts into the landscape layout, which hides the status bar. Off for the separate video player. */
    protected open val hasLandscapeLayout = false

    protected open val isChromeShown = true

    /** Anything laid out around the system bars asks this rather than reading the insets itself. */
    val systemBars = ViewerSystemBars(this, { hasLandscapeLayout }, { isChromeShown }) { onLandscapeLayoutChanged() }

    val isInLandscapeLayout: Boolean
        get() = systemBars.isInLandscapeLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the tile growing into the photo is the only motion there should be here
        holdWindowStill()
        val contentRoot = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { _, insets ->
            setupEdgeToEdge(insets)
            insets
        }
        registerShowNotchCollector(contentRoot)
        systemBars.attach()
    }

    /** Whether a panel of the app's own is currently drawn over the navigation bar. */
    protected open val isPanelCoveringNavigationBar: Boolean = false

    /**
     * The metadata of the file on screen has been written from a panel of this viewer's own - the
     * description sheet - so anything the screen shows about it has to be read again.
     */
    open fun onCurrentFileChanged() = Unit

    override fun onResume() {
        super.onResume()
        // a viewer's chrome is white over the photo whichever theme the app is in, so the system's
        // own icons at either end of the screen have to be light to match it
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        // ...unless a panel left open across a trip to another app is still covering them
        updateNavigationBarIconsForPanel(isPanelCoveringNavigationBar)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemBars.onWindowChanged()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        systemBars.onWindowChanged()
    }

    protected open fun onLandscapeLayoutChanged() = Unit

    /**
     * Hands the navigation bar back its normal icons while a panel covers it.
     *
     * The viewer forces light icons because they sit over the photo, but the metadata sheet paints
     * the app's own background under them - and in a light theme that leaves white icons on a white
     * panel, which is no icons at all.
     */
    fun updateNavigationBarIconsForPanel(panelCoversNavigationBar: Boolean) {
        val backgroundIsLight = ColorUtils.calculateLuminance(getProperBackgroundColor()) > LIGHT_LUMINANCE
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars =
            panelCoversNavigationBar && backgroundIsLight
    }

    private fun registerShowNotchCollector(view: View) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                config.showNotchFlow.collect {
                    view.requestApplyInsets()
                }
            }
        }
    }

    private fun setupEdgeToEdge(insets: WindowInsetsCompat) {
        val system = systemBars.layoutInsets(insets)
        val cutout = insets.getInsetsIgnoringVisibility(Type.displayCutout())
        if (config.showNotch) {
            val systemAndCutout = Insets.max(system, cutout)
            appBarLayout.updatePaddingWithBase(
                top = systemAndCutout.top,
                left = systemAndCutout.left,
                right = systemAndCutout.right
            )

            contentHolder.updatePaddingWithBase(left = 0, top = 0, right = 0, bottom = 0)
        } else {
            appBarLayout.updatePaddingWithBase(
                top = if (cutout.top > 0) 0 else system.top,
                left = if (cutout.left > 0) 0 else system.left,
                right = if (cutout.right > 0) 0 else system.right
            )

            contentHolder.updatePaddingWithBase(
                left = cutout.left,
                top = cutout.top,
                right = cutout.right,
                bottom = cutout.bottom
            )
        }
    }

    fun applyProperHorizontalInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val sides = systemBars.sideInsets(insets)
            view.updateMarginWithBase(left = sides.left, right = sides.right)
            insets
        }
    }
}
