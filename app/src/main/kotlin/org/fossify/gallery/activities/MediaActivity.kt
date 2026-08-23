package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.core.view.updatePadding
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.databinding.ActivityMediaBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.updateWidgets
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.GET_ANY_INTENT
import org.fossify.gallery.helpers.GET_IMAGE_INTENT
import org.fossify.gallery.helpers.GET_VIDEO_INTENT
import org.fossify.gallery.helpers.GridChrome
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.SET_WALLPAPER_INTENT
import org.fossify.gallery.helpers.SHOW_TEMP_HIDDEN_DURATION
import org.fossify.gallery.helpers.SKIP_AUTHENTICATION
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.views.MediaGridPane
import org.fossify.gallery.views.PickRequest

/**
 * The window a [MediaGridPane] is shown in when it is a screen of its own - a folder tapped into,
 * the recycle bin, the favourites, or a grid another app is picking from. The two top level grids
 * are not among these: they are two panes of [MainActivity], with no window of their own.
 *
 * Everything that was ever done to the grid lives in the pane; what is left here is the window, the
 * chrome floating over it, and the handful of things only an activity can answer for.
 */
class MediaActivity : SimpleActivity(), MediaGridPane.Host {
    override var isSearchBarEnabled = true

    private val binding by viewBinding(ActivityMediaBinding::inflate)

    private var mPath = ""
    private var mShowAll = false
    private var mTempShowHiddenHandler = Handler()

    private lateinit var pane: MediaGridPane
    private lateinit var chrome: GridChrome

    override val topBar: MySearchMenu get() = binding.mediaMenu

    companion object {
        var mMedia = ArrayList<ThumbnailItem>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        try {
            mPath = intent.getStringExtra(DIRECTORY) ?: ""
        } catch (e: Exception) {
            showErrorToast(e)
            finish()
            return
        }

        mShowAll = config.showAll && mPath != RECYCLE_BIN
        pane = MediaGridPane(
            activity = this,
            binding = binding.mediaPane,
            host = this,
            mPath = mPath,
            showAll = config.showAll,
            pick = intent.pickRequest(),
            skipAuthentication = intent.getBooleanExtra(SKIP_AUTHENTICATION, false)
        )

        // the pane's grid reserves the navigation pill's room, and there is no pill on this screen
        binding.mediaPane.mediaGrid.updatePadding(bottom = 0)
        setupInsetPadding()
        chrome = GridChrome(binding.mediaMenu, binding.contentHolder)
        chrome.attach(pane)

        if (mShowAll) {
            registerFileUpdateListener()
        }

        onPaneStateChanged()
        updateWidgets()
    }

    override fun onStart() {
        super.onStart()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        chrome.updateColors()
        onPaneStateChanged()
        pane.onActivated()
    }

    override fun onPause() {
        super.onPause()
        pane.onDeactivated()
    }

    override fun onStop() {
        super.onStop()

        if (config.temporarilyShowHidden || config.tempSkipDeleteConfirmation) {
            mTempShowHiddenHandler.postDelayed({
                config.temporarilyShowHidden = false
                config.tempSkipDeleteConfirmation = false
                config.tempSkipRecycleBin = false
            }, SHOW_TEMP_HIDDEN_DURATION)
        } else {
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (config.showAll && !isChangingConfigurations) {
            config.temporarilyShowHidden = false
            config.tempSkipDeleteConfirmation = false
            config.tempSkipRecycleBin = false
            unregisterFileUpdateListener()
            GalleryDatabase.destroyInstance()
        }

        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onBackPressedCompat(): Boolean {
        if (pane.handleBack()) {
            return true
        }

        if (config.showAll) {
            appLockManager.lock()
        }

        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        pane.onActivityResult(requestCode, resultCode, resultData)
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    // ---------------------------------------------------------- the pane's host ----

    override fun refreshMenu() = chrome.refreshMenuItems()

    override fun applyInsets() = setupInsetPadding()

    override fun navigateUp() = performDefaultBack()

    // sideways scrolling has no room to pan the bar out of, and while an arrangement is being made
    // the bar is the way out of that mode
    override fun onPaneStateChanged() {
        chrome.floatingTopBar.isPanningEnabled = !config.scrollHorizontally && !pane.isReordering
    }

    // --------------------------------------------------------------- the screen ----

    /**
     * Keeps the grid clear of the navigation bar - except while the reorder bar is up, where the
     * bar sits between the two and does that job itself, and asking for the room twice would only
     * open an empty band above it. Insets are dispatched again whenever the bar comes or goes, so
     * this has to be what the inset handling is told rather than padding set once behind its back.
     */
    private fun setupInsetPadding() {
        val reorderBar = binding.mediaPane.mediaReorderBar.root
        setupEdgeToEdge(
            // the grid gets no top inset of its own - keepGridClear() pads it by the whole height
            // of the bar, which already carries this inset
            padTopSystem = listOf(binding.mediaMenu, binding.mediaPane.mediaEmptyTextPlaceholder),
            padBottomImeAndSystem = if (pane.isReordering) {
                listOf(reorderBar)
            } else {
                listOf(binding.mediaPane.mediaGrid, reorderBar)
            }
        )
    }

}

/** What another app asked this grid to pick for it, if anything. */
private fun Intent.pickRequest() = PickRequest(
    image = getBooleanExtra(GET_IMAGE_INTENT, false),
    video = getBooleanExtra(GET_VIDEO_INTENT, false),
    any = getBooleanExtra(GET_ANY_INTENT, false),
    wallpaper = getBooleanExtra(SET_WALLPAPER_INTENT, false),
    allowMultiple = getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
)
