package org.fossify.gallery.activities

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.net.toUri
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.CreateNewFolderDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.deleteFiles
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getIsPathDirectory
import org.fossify.commons.extensions.getLatestMediaByDateId
import org.fossify.commons.extensions.getLatestMediaId
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.IS_FROM_GALLERY
import org.fossify.commons.helpers.REQUEST_EDIT_IMAGE
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.adapters.MediaGridNavigator
import org.fossify.gallery.asynctasks.GetMediaAsynctask
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.databinding.ActivityMediaBinding
import org.fossify.gallery.dialogs.ChangeGroupingDialog
import org.fossify.gallery.dialogs.ChangeSortingDialog
import org.fossify.gallery.dialogs.ChangeViewTypeDialog
import org.fossify.gallery.dialogs.FilterMediaDialog
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.extensions.applyEdgeFade
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.deleteDBPath
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.emptyAndDisableTheRecycleBin
import org.fossify.gallery.extensions.emptyTheRecycleBin
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.getCachedMedia
import org.fossify.gallery.extensions.getHumanizedFilename
import org.fossify.gallery.extensions.isDownloadsFolder
import org.fossify.gallery.extensions.launchAbout
import org.fossify.gallery.extensions.launchCamera
import org.fossify.gallery.extensions.launchSettings
import org.fossify.gallery.extensions.launchGesturePlayer
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.mediaGridZoom
import org.fossify.gallery.extensions.movePathsInRecycleBin
import org.fossify.gallery.extensions.openPath
import org.fossify.gallery.extensions.openRecycleBin
import org.fossify.gallery.extensions.removeCustomMediaOrder
import org.fossify.gallery.extensions.restoreRecycleBinPaths
import org.fossify.gallery.extensions.saveCustomMediaOrder
import org.fossify.gallery.extensions.showRecycleBinEmptyingDialog
import org.fossify.gallery.extensions.showRestoreConfirmationDialog
import org.fossify.gallery.extensions.tryDeleteFileDirItem
import org.fossify.gallery.extensions.updateWidgets
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.FloatingTopBar
import org.fossify.gallery.helpers.GET_ANY_INTENT
import org.fossify.gallery.helpers.GET_IMAGE_INTENT
import org.fossify.gallery.helpers.GET_VIDEO_INTENT
import org.fossify.gallery.helpers.GridPinchZoom
import org.fossify.gallery.helpers.GridSpacingItemDecoration
import org.fossify.gallery.helpers.GridZoom
import org.fossify.gallery.helpers.IS_IN_RECYCLE_BIN
import org.fossify.gallery.helpers.MEDIA_GRID_MENU
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.PICKED_PATHS
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ReorderBar
import org.fossify.gallery.helpers.SET_WALLPAPER_INTENT
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_FAVORITES
import org.fossify.gallery.helpers.SHOW_RECYCLE_BIN
import org.fossify.gallery.helpers.SHOW_TEMP_HIDDEN_DURATION
import org.fossify.gallery.helpers.SKIP_AUTHENTICATION
import org.fossify.gallery.helpers.SLIDESHOW_START_ON_ENTER
import org.fossify.gallery.helpers.VIDEO_PLAYER_APP
import org.fossify.gallery.helpers.VIDEO_PLAYER_SYSTEM
import org.fossify.gallery.helpers.ViewerReturn
import org.fossify.gallery.interfaces.MediaOperationsListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import org.fossify.gallery.views.GlassMenu
import java.io.File
import java.io.IOException

class MediaActivity : SimpleActivity(), MediaOperationsListener {
    override var isSearchBarEnabled = true
    
    private val LAST_MEDIA_CHECK_PERIOD = 3000L

    private var mPath = ""
    private var mIsGetImageIntent = false
    private var mIsGetVideoIntent = false
    private var mIsGetAnyIntent = false
    private var mIsGettingMedia = false
    private var mAllowPickingMultiple = false
    private var mShowAll = false
    private var mLoadedInitialPhotos = false
    private var mShowLoadingIndicator = true
    private var mWasFullscreenViewOpen = false
    private var mLastSearchedText = ""
    private var mIsReordering = false
    private var mGridBottomPadding = 0
    private var mGridPositionToRestore: MediaGridNavigator.GridPosition? = null
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mCurrAsyncTask: GetMediaAsynctask? = null
    private var mWasGridIdleOnTouch = false
    private var mDefaultItemAnimator: RecyclerView.ItemAnimator? = null

    /**
     * The item a zoom is keeping in place and how far down the grid to keep it, held as a path
     * rather than a position - the grouping headers dropped on the way into the simplified counts
     * shift every position after them.
     */
    private var mZoomAnchor: Pair<String, Float>? = null

    // the ladder of counts the grid can be pinched through. Built on first use rather than here,
    // where there is no context to read yet, and dropped when the scroll direction changes it
    private var mCachedGridZoom: GridZoom? = null
    private val mGridZoom: GridZoom
        get() = mCachedGridZoom ?: mediaGridZoom().also { mCachedGridZoom = it }

    private val mPinchZoom by lazy {
        GridPinchZoom(
            recyclerView = binding.mediaGrid,
            onZoomIn = { stepColumnCount(mGridZoom.zoomIn(config.mediaColumnCnt)) },
            onZoomOut = { stepColumnCount(mGridZoom.zoomOut(config.mediaColumnCnt)) },
            onPinchStart = ::captureZoomAnchor
        )
    }

    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mStoredShowFileTypes = true
    private var mStoredRoundedCorners = false
    private var mStoredMarkFavoriteItems = true
    private var mStoredShowRatings = true
    private var mStoredTextColor = 0
    private var mStoredPrimaryColor = 0
    private var mStoredThumbnailSpacing = 0

    private val binding by viewBinding(ActivityMediaBinding::inflate)
    private val floatingTopBar by lazy { FloatingTopBar(binding.mediaMenu, binding.mediaHolder) }
    private val reorderBar by lazy { ReorderBar(binding.mediaReorderBar) }
    private val viewerReturn = ViewerReturn()

    companion object {
        var mMedia = ArrayList<ThumbnailItem>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        intent.apply {
            mIsGetImageIntent = getBooleanExtra(GET_IMAGE_INTENT, false)
            mIsGetVideoIntent = getBooleanExtra(GET_VIDEO_INTENT, false)
            mIsGetAnyIntent = getBooleanExtra(GET_ANY_INTENT, false)
            mAllowPickingMultiple = getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }

        binding.mediaRefreshLayout.setOnRefreshListener { getMedia() }
        try {
            mPath = intent.getStringExtra(DIRECTORY) ?: ""
        } catch (e: Exception) {
            showErrorToast(e)
            finish()
            return
        }

        setupOptionsMenu()
        setupReorderBar()
        refreshMenuItems()
        storeStateVariables()
        setupInsetPadding()
        setupFloatingTopBar()
        // before the tap below, so a pinch is the first thing asked about the grid's touches
        mPinchZoom.isEnabled = isGridViewType()
        setupZoomInOnTap()
        mDefaultItemAnimator = binding.mediaGrid.itemAnimator

        if (mShowAll) {
            registerFileUpdateListener()
        }

        binding.mediaEmptyTextPlaceholder2.setOnClickListener {
            showFilterMediaDialog()
        }

        updateWidgets()
    }

    override fun onStart() {
        super.onStart()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        updateMenuColors()
        updateTopBarPanning()
        updateEdgeFades()
        if (mStoredAnimateGifs != config.animateGifs) {
            getMediaAdapter()?.updateAnimateGifs(config.animateGifs)
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            getMediaAdapter()?.updateCropThumbnails(config.cropThumbnails)
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            mLoadedInitialPhotos = false
            mCachedGridZoom = null
            binding.mediaGrid.adapter = null
            getMedia()
        }

        if (mStoredShowFileTypes != config.showThumbnailFileTypes) {
            getMediaAdapter()?.updateShowFileTypes(config.showThumbnailFileTypes)
        }

        if (mStoredShowRatings != config.showThumbnailRating) {
            // turning it on for the first time means no rating has ever been read, so the scan has
            // to run again rather than just repainting what is already there
            getMediaAdapter()?.updateShowRatings(config.showThumbnailRating)
            getMedia()
        }

        if (mStoredTextColor != getProperTextColor()) {
            getMediaAdapter()?.updateTextColor(getProperTextColor())
        }

        val primaryColor = getProperPrimaryColor()
        if (mStoredPrimaryColor != primaryColor) {
            getMediaAdapter()?.updatePrimaryColor()
        }

        if (
            mStoredThumbnailSpacing != config.thumbnailSpacing
            || mStoredRoundedCorners != config.fileRoundedCorners
            || mStoredMarkFavoriteItems != config.markFavoriteItems
        ) {
            binding.mediaGrid.adapter = null
            setupAdapter()
        }

        refreshMenuItems()

        binding.mediaFastscroller.updateColors(primaryColor)
        binding.mediaRefreshLayout.isEnabled = config.enablePullToRefresh
        getMediaAdapter()?.apply {
            dateFormat = config.dateFormat
            timeFormat = getTimeFormat()
        }

        binding.loadingIndicator.setIndicatorColor(getProperPrimaryColor())
        reorderBar.updateColors()
        binding.mediaEmptyTextPlaceholder.setTextColor(getProperTextColor())
        binding.mediaEmptyTextPlaceholder2.setTextColor(getProperPrimaryColor())
        binding.mediaEmptyTextPlaceholder2.bringToFront()

        // the grid still holds what it had when the viewer was opened, so point the item out now
        // rather than only once the refresh below comes back. it stays pending if it is not there
        viewerReturn.reveal(getMediaAdapter()?.gridNavigator)

        // do not refresh Random sorted files after opening a fullscreen image and going Back
        val isRandomSorting = config.getFolderSorting(mPath) and SORT_BY_RANDOM != 0
        if (mMedia.isEmpty() || !isRandomSorting || (isRandomSorting && !mWasFullscreenViewOpen)) {
            if (shouldSkipAuthentication()) {
                tryLoadGallery()
            } else {
                handleLockedFolderOpening(mPath) { success ->
                    if (success) {
                        tryLoadGallery()
                    } else {
                        finish()
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mIsGettingMedia = false
        binding.mediaRefreshLayout.isRefreshing = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)

        if (!mMedia.isEmpty()) {
            mCurrAsyncTask?.stopFetching()
        }
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
        return if (mIsReordering) {
            cancelReordering()
            true
        } else if (binding.mediaMenu.isSearchOpen) {
            binding.mediaMenu.closeSearch()
            true
        } else {
            if (config.showAll) {
                appLockManager.lock()
            }

            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == REQUEST_EDIT_IMAGE) {
            if (resultCode == RESULT_OK && resultData != null) {
                mMedia.clear()
                refreshItems()
            }
        } else if (requestCode == ViewerReturn.REQUEST_CODE) {
            viewerReturn.onViewerResult(resultData)
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun refreshMenuItems() {
        val menu = binding.mediaMenu.requireToolbar().menu
        // nothing in here should be reachable mid drag, every entry would pull the grid out from
        // under the arrangement the user is in the middle of making. the entries the rules below
        // never touch are always meant to be there, so put everything back before applying them
        val isVisibleByDefault = !mIsReordering
        for (index in 0 until menu.size()) {
            menu.getItem(index).isVisible = isVisibleByDefault
        }

        if (mIsReordering) {
            return
        }

        menu.apply {
            findItem(R.id.group).isVisible = !config.scrollHorizontally
            findItem(R.id.custom_order).isVisible = mPath != RECYCLE_BIN
            findItem(R.id.reset_custom_order).isVisible =
                mPath != RECYCLE_BIN && config.hasCustomMediaOrder(getPathToUse())

            findItem(R.id.empty_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.empty_disable_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.restore_all_files).isVisible = mPath == RECYCLE_BIN

            findItem(R.id.folder_view).isVisible = mShowAll
            findItem(R.id.open_camera).isVisible = mShowAll
            findItem(R.id.about).isVisible = mShowAll
            findItem(R.id.create_new_folder).isVisible =
                !mShowAll && mPath != RECYCLE_BIN && mPath != FAVORITES
            findItem(R.id.open_recycle_bin).isVisible = config.useRecycleBin && mPath != RECYCLE_BIN

            findItem(R.id.temporarily_show_hidden).isVisible = !config.shouldShowHidden
            findItem(R.id.stop_showing_hidden).isVisible =
                (!isRPlus() || isExternalStorageManager()) && config.temporarilyShowHidden

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            findItem(R.id.column_count).isVisible = viewType == VIEW_TYPE_GRID
            findItem(R.id.toggle_filename).isVisible = viewType == VIEW_TYPE_GRID
        }
    }

    private fun setupOptionsMenu() {
        binding.mediaMenu.requireToolbar().inflateMenu(R.menu.menu_media)
        binding.mediaMenu.setupMenu()
        updateTopBarPanning()
        GlassMenu.replaceOverflow(
            binding.mediaMenu.requireToolbar(), MEDIA_GRID_MENU, binding.mediaHolder
        )

        binding.mediaMenu.onSearchTextChangedListener = { text ->
            mLastSearchedText = text
            searchQueryChanged(text)
            binding.mediaRefreshLayout.isEnabled = text.isEmpty() && config.enablePullToRefresh
        }

        binding.mediaMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> showSortingDialog()
                R.id.filter -> showFilterMediaDialog()
                R.id.empty_recycle_bin -> emptyRecycleBin()
                R.id.empty_disable_recycle_bin -> emptyAndDisableRecycleBin()
                R.id.restore_all_files -> restoreAllFiles()
                R.id.toggle_filename -> toggleFilenameVisibility()
                R.id.open_camera -> launchCamera()
                R.id.folder_view -> switchToFolderView()
                R.id.change_view_type -> changeViewType()
                R.id.group -> showGroupByDialog()
                R.id.custom_order -> startReordering()
                R.id.reset_custom_order -> resetCustomOrder()
                R.id.create_new_folder -> createNewFolder()
                R.id.open_recycle_bin -> openRecycleBin()
                R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
                R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
                R.id.column_count -> changeColumnCount()
                R.id.slideshow -> startSlideshow()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun startSlideshow() {
        if (mMedia.isNotEmpty()) {
            hideKeyboard()
            Intent(this, ViewPagerActivity::class.java).apply {
                val item = mMedia.firstOrNull { it is Medium } as? Medium ?: return
                putExtra(SKIP_AUTHENTICATION, shouldSkipAuthentication())
                putExtra(PATH, item.path)
                putExtra(SHOW_ALL, mShowAll)
                putExtra(SLIDESHOW_START_ON_ENTER, true)
                putExtra(IS_FROM_GALLERY, true)
                viewerReturn.opening(item.path)
                startActivityForResult(this, ViewerReturn.REQUEST_CODE)
            }
        }
    }

    private fun updateMenuColors() {
        binding.mediaMenu.updateColors()
        // updateColors() paints the band back in every time, so undo it right behind it
        floatingTopBar.makeFloating()
    }

    private fun setupFloatingTopBar() {
        floatingTopBar.floatOver(binding.mediaGrid, binding.mediaRefreshLayout)
    }

    // repainted on every resume rather than set once: the fades are drawn in the theme's own
    // background colour, and the theme can change while this screen is in the back stack
    private fun updateEdgeFades() {
        binding.mediaTopFade.applyEdgeFade(atTop = true)
        binding.mediaBottomFade.applyEdgeFade(atTop = false)
    }

    // sideways scrolling has no room to pan the bar out of, and while a group is being arranged
    // the bar is the way out of that mode
    private fun updateTopBarPanning() {
        floatingTopBar.isPanningEnabled = !config.scrollHorizontally && !mIsReordering
    }

    /**
     * Keeps the grid clear of the navigation bar - except while the reorder bar is up, where the
     * bar sits between the two and does that job itself, and asking for the room twice would only
     * open an empty band above it. Insets are dispatched again whenever the bar comes or goes, so
     * this has to be what the inset handling is told rather than padding set once behind its back.
     */
    private fun setupInsetPadding() {
        setupEdgeToEdge(
            // the grid gets no top inset of its own - keepGridClearOfTopBar() pads it by the whole
            // height of the bar, which already carries this inset
            padTopSystem = listOf(binding.mediaMenu, binding.mediaEmptyTextPlaceholder),
            padBottomImeAndSystem = if (mIsReordering) {
                listOf(reorderBar.root)
            } else {
                listOf(binding.mediaGrid, reorderBar.root)
            }
        )
    }

    // the folder a custom order is keyed by, matching what the fetching and grouping code uses
    private fun getPathToUse() = (if (mShowAll) SHOW_ALL else mPath).ifEmpty { SHOW_ALL }

    private fun setupReorderBar() {
        reorderBar.onMoveToEdge = { toTop -> getMediaAdapter()?.reorderMode?.moveSelectionToEdge(toTop) }
        reorderBar.onCancel = ::cancelReordering
        reorderBar.onSave = ::saveReordering
    }

    /**
     * Puts the grid into drag-to-reorder mode. The list is flattened first - grouping headers have
     * no place in a hand made order - and the search is closed so the arrangement covers the whole
     * folder rather than whatever was filtered into view.
     */
    private fun startReordering() {
        if (mIsReordering) {
            return
        }

        hideKeyboard()
        if (binding.mediaMenu.isSearchOpen) {
            binding.mediaMenu.closeSearch()
        }

        // arranging at a count where no single item can be picked out is not arranging anything
        if (mGridZoom.isSimplified(config.mediaColumnCnt)) {
            setColumnCount(mGridZoom.interactiveMax)
        }

        val flatMedia = mMedia.filterIsInstance<Medium>().toMutableList() as ArrayList<ThumbnailItem>
        if (flatMedia.size < 2) {
            toast(R.string.reorder_needs_more_items)
            return
        }

        mIsReordering = true
        binding.mediaRefreshLayout.isEnabled = false
        reorderBar.show()
        // the bar brings its own solid background, so the darkening behind it would only muddy it
        binding.mediaBottomFade.beGone()
        updateTopBarPanning()
        // hand the room the grid was keeping for the navigation bar over to the bar taking its place
        mGridBottomPadding = binding.mediaGrid.paddingBottom
        setupInsetPadding()
        binding.mediaGrid.updatePadding(bottom = 0)
        getMediaAdapter()?.reorderMode?.onSelectionChanged = reorderBar::setMarkedCount
        getMediaAdapter()?.reorderMode?.setActive(true, flatMedia)
        handleGridSpacing(flatMedia)
        setupLayoutManager()
        refreshMenuItems()
    }

    private fun cancelReordering() {
        if (!mIsReordering) {
            return
        }

        stopReordering()
        // hand the untouched list back, whatever was dragged around only ever lived in the adapter
        getMediaAdapter()?.reorderMode?.setActive(false, mMedia)
        handleGridSpacing()
        setupLayoutManager()
    }

    private fun saveReordering() {
        val orderedPaths = getMediaAdapter()?.reorderMode?.orderedPaths()
        if (orderedPaths.isNullOrEmpty()) {
            cancelReordering()
            return
        }

        val pathToUse = getPathToUse()
        // the reload at the end of this builds the grid over again, which would drop it back at the
        // top of a folder the arrangement was as likely as not made well down
        mGridPositionToRestore = getMediaAdapter()?.gridNavigator?.currentPosition()
        stopReordering()
        // keep the dragged order on screen, reloadMedia() below replaces it with the saved one
        getMediaAdapter()?.reorderMode?.setActive(false)

        ensureBackgroundThread {
            saveCustomMediaOrder(pathToUse, orderedPaths)
            runOnUiThread {
                // a folder that was just arranged should come up in that order from now on
                config.saveCustomSorting(pathToUse, SORT_BY_CUSTOM)
                toast(R.string.custom_order_saved)
                // the menu was put back before this arrangement was on file, so Reset was still
                // hidden then - the folder has one to reset now
                refreshMenuItems()
                reloadMedia()
            }
        }
    }

    private fun stopReordering() {
        mIsReordering = false
        reorderBar.hide()
        binding.mediaBottomFade.beVisible()
        updateTopBarPanning()
        setupInsetPadding()
        binding.mediaGrid.updatePadding(bottom = mGridBottomPadding)
        binding.mediaRefreshLayout.isEnabled = config.enablePullToRefresh
        refreshMenuItems()
    }

    // an arrangement can be a lot of work to make and nothing brings it back, so ask first
    private fun resetCustomOrder() {
        ConfirmationDialog(
            this,
            "",
            R.string.reset_custom_order_confirmation,
            org.fossify.commons.R.string.yes,
            org.fossify.commons.R.string.no
        ) {
            doResetCustomOrder()
        }
    }

    private fun doResetCustomOrder() {
        val pathToUse = getPathToUse()
        ensureBackgroundThread {
            removeCustomMediaOrder(pathToUse)
            runOnUiThread {
                if (config.getFolderSorting(pathToUse) and SORT_BY_CUSTOM != 0) {
                    config.removeCustomSorting(pathToUse)
                }

                toast(R.string.custom_order_reset)
                refreshMenuItems()
                reloadMedia()
            }
        }
    }

    private fun reloadMedia() {
        mLoadedInitialPhotos = false
        binding.mediaGrid.adapter = null
        getMedia()
    }

    private fun storeStateVariables() {
        mStoredTextColor = getProperTextColor()
        mStoredPrimaryColor = getProperPrimaryColor()
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredShowFileTypes = showThumbnailFileTypes
            mStoredMarkFavoriteItems = markFavoriteItems
            mStoredShowRatings = showThumbnailRating
            mStoredThumbnailSpacing = thumbnailSpacing
            mStoredRoundedCorners = fileRoundedCorners
            mShowAll = showAll && mPath != RECYCLE_BIN
        }
    }

    private fun searchQueryChanged(text: String) {
        ensureBackgroundThread {
            try {
                val filtered = mMedia
                    .filter { it is Medium && it.name.contains(text, true) } as ArrayList
                filtered.sortBy { it is Medium && !it.name.startsWith(text, true) }
                val grouped = MediaFetcher(applicationContext).groupMedia(
                    media = filtered as ArrayList<Medium>, path = mPath
                )
                runOnUiThread {
                    if (grouped.isEmpty()) {
                        binding.mediaEmptyTextPlaceholder.text =
                            getString(org.fossify.commons.R.string.no_items_found)
                        binding.mediaEmptyTextPlaceholder.beVisible()
                        binding.mediaFastscroller.beGone()
                    } else {
                        binding.mediaEmptyTextPlaceholder.beGone()
                        binding.mediaFastscroller.beVisible()
                    }

                    val shown = mediaForGrid(grouped)
                    handleGridSpacing(shown)
                    getMediaAdapter()?.updateMedia(shown)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun tryLoadGallery() {
        requestMediaPermissions {
            val dirName = when (mPath) {
                FAVORITES -> getString(org.fossify.commons.R.string.favorites)
                RECYCLE_BIN -> getString(org.fossify.commons.R.string.recycle_bin)
                config.OTGPath -> getString(org.fossify.commons.R.string.usb)
                else -> getHumanizedFilename(mPath)
            }

            val searchHint = if (mShowAll) {
                getString(org.fossify.commons.R.string.search_files)
            } else {
                getString(org.fossify.commons.R.string.search_in_placeholder, dirName)
            }

            binding.mediaMenu.updateHintText(searchHint)
            if (!mShowAll) {
                binding.mediaMenu.toggleForceArrowBackIcon(true)
                binding.mediaMenu.onNavigateBackClickListener = {
                    performDefaultBack()
                }
            }

            if (mShowLoadingIndicator) {
                binding.loadingIndicator.show()
                mShowLoadingIndicator = false
            }

            getMedia()
            setupLayoutManager()
        }
    }

    private fun getMediaAdapter() = binding.mediaGrid.adapter as? MediaAdapter

    private fun setupAdapter() {
        if (!mShowAll && isDirEmpty()) {
            return
        }

        // a background refresh must not disturb an arrangement in progress, the adapter holds the
        // only copy of it and updateMedia() ignores us anyway
        if (mIsReordering) {
            return
        }

        val currAdapter = binding.mediaGrid.adapter
        if (currAdapter == null) {
            MediaAdapter(
                activity = this,
                media = mediaForGrid().clone() as ArrayList<ThumbnailItem>,
                listener = this,
                isAGetIntent = mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent,
                allowMultiplePicks = mAllowPickingMultiple,
                path = mPath,
                recyclerView = binding.mediaGrid,
                swipeRefreshLayout = binding.mediaRefreshLayout
            ) {
                if (it is Medium && !isFinishing) {
                    itemClicked(it.path)
                }
            }.apply {
                setSimplified(isGridSimplified(), media)
                binding.mediaGrid.adapter = this
            }

            applyGridPerformanceTuning()

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            if (viewType == VIEW_TYPE_LIST && areSystemAnimationsEnabled) {
                binding.mediaGrid.scheduleLayoutAnimation()
            }

            setupLayoutManager()
            handleGridSpacing()
        } else if (mLastSearchedText.isEmpty()) {
            (currAdapter as MediaAdapter).updateMedia(mediaForGrid())
            handleGridSpacing()
        } else {
            searchQueryChanged(mLastSearchedText)
        }

        setupScrollDirection()
        restoreGridPosition()
        viewerReturn.reveal(getMediaAdapter()?.gridNavigator)
    }

    // a reload starts the grid at the top, which is no use to someone who was just arranging the
    // middle of a folder - put it back where the arranging was going on
    private fun restoreGridPosition() {
        val gridPosition = mGridPositionToRestore ?: return
        val adapter = getMediaAdapter() ?: return
        mGridPositionToRestore = null
        adapter.gridNavigator.restore(gridPosition)
    }

    private fun setupScrollDirection() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        val scrollHorizontally = config.scrollHorizontally && viewType == VIEW_TYPE_GRID
        binding.mediaFastscroller.setScrollVertically(!scrollHorizontally)
    }

    private fun checkLastMediaChanged() {
        if (isDestroyed || config.getFolderSorting(mPath) and SORT_BY_RANDOM != 0) {
            return
        }

        mLastMediaHandler.removeCallbacksAndMessages(null)
        mLastMediaHandler.postDelayed({
            ensureBackgroundThread {
                val mediaId = getLatestMediaId()
                val mediaDateId = getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    runOnUiThread {
                        getMedia()
                    }
                } else {
                    checkLastMediaChanged()
                }
            }
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, false, true, mPath) {
            reloadMedia()
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            mLoadedInitialPhotos = false
            binding.mediaRefreshLayout.isRefreshing = true
            binding.mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun emptyRecycleBin() {
        showRecycleBinEmptyingDialog {
            emptyTheRecycleBin {
                finish()
            }
        }
    }

    private fun emptyAndDisableRecycleBin() {
        showRecycleBinEmptyingDialog {
            emptyAndDisableTheRecycleBin {
                finish()
            }
        }
    }

    private fun restoreAllFiles() {
        val paths = mMedia.filter { it is Medium }.map { (it as Medium).path } as ArrayList<String>
        showRestoreConfirmationDialog(paths.size) {
            restoreRecycleBinPaths(paths) {
                ensureBackgroundThread {
                    directoryDB.deleteDirPath(RECYCLE_BIN)
                }
                finish()
            }
        }
    }

    private fun toggleFilenameVisibility() {
        config.displayFileNames = !config.displayFileNames
        getMediaAdapter()?.updateDisplayFilenames(config.displayFileNames)
    }

    private fun switchToFolderView() {
        hideKeyboard()
        config.showAll = false
        // leaving the all-folders view for good, so a startup setting still pointing at it would
        // drop the user straight back in on the next launch with no way out of the loop
        if (config.defaultFolder == SHOW_ALL) {
            config.defaultFolder = ""
        }

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this, false, mPath) {
            refreshMenuItems()
            setupLayoutManager()
            binding.mediaGrid.adapter = null
            setupAdapter()
        }
    }

    private fun showGroupByDialog() {
        ChangeGroupingDialog(this, mPath) {
            reloadMedia()
        }
    }

    private fun deleteDirectoryIfEmpty() {
        if (config.deleteEmptyFolders) {
            val fileDirItem = FileDirItem(mPath, mPath.getFilenameFromPath(), true)
            if (!fileDirItem.isDownloadsFolder() && fileDirItem.isDirectory) {
                ensureBackgroundThread {
                    if (fileDirItem.getProperFileCount(this, true) == 0) {
                        tryDeleteFileDirItem(fileDirItem, true, true)
                    }
                }
            }
        }
    }

    private fun getMedia() {
        if (mIsGettingMedia) {
            return
        }

        mIsGettingMedia = true
        if (mLoadedInitialPhotos) {
            startAsyncTask()
        } else {
            getCachedMedia(
                mPath,
                mIsGetVideoIntent && !mIsGetImageIntent,
                mIsGetImageIntent && !mIsGetVideoIntent
            ) {
                if (it.isEmpty()) {
                    runOnUiThread {
                        binding.mediaRefreshLayout.isRefreshing = true
                    }
                } else {
                    gotMedia(it, true)
                }
                startAsyncTask()
            }
        }

        mLoadedInitialPhotos = true
    }

    private fun startAsyncTask() {
        mCurrAsyncTask?.stopFetching()
        mCurrAsyncTask = GetMediaAsynctask(
            context = applicationContext,
            mPath = mPath,
            isPickImage = mIsGetImageIntent && !mIsGetVideoIntent,
            isPickVideo = mIsGetVideoIntent && !mIsGetImageIntent,
            showAll = mShowAll
        ) {
            ensureBackgroundThread {
                val oldMedia = mMedia.clone() as ArrayList<ThumbnailItem>
                val newMedia = it
                try {
                    gotMedia(newMedia, false)

                    // remove cached files that are no longer valid for whatever reason
                    val newPaths = newMedia.mapNotNullTo(HashSet(newMedia.size)) { (it as? Medium)?.path }
                    oldMedia
                        .mapNotNull { it as? Medium }
                        .filter { !newPaths.contains(it.path) }
                        .forEach {
                            if (mPath == FAVORITES && getDoesFilePathExist(it.path)) {
                                favoritesDB.deleteFavoritePath(it.path)
                                mediaDB.updateFavorite(it.path, false)
                            } else {
                                mediaDB.deleteMediumPath(it.path)
                            }
                        }
                } catch (e: Exception) {
                }
            }
        }

        mCurrAsyncTask!!.execute()
    }

    private fun isDirEmpty(): Boolean {
        return if (mMedia.isEmpty() && config.filterMedia > 0) {
            if (mPath != FAVORITES && mPath != RECYCLE_BIN) {
                deleteDirectoryIfEmpty()
                deleteDBDirectory()
            }

            if (mPath == FAVORITES) {
                ensureBackgroundThread {
                    directoryDB.deleteDirPath(FAVORITES)
                }
            }

            if (mPath == RECYCLE_BIN) {
                binding.mediaEmptyTextPlaceholder.setText(org.fossify.commons.R.string.no_items_found)
                binding.mediaEmptyTextPlaceholder.beVisible()
                binding.mediaEmptyTextPlaceholder2.beGone()
            } else {
                finish()
            }

            true
        } else {
            false
        }
    }

    private fun deleteDBDirectory() {
        ensureBackgroundThread {
            try {
                directoryDB.deleteDirPath(mPath)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun createNewFolder() {
        CreateNewFolderDialog(this, mPath) {
            config.tempFolderPath = it
        }
    }

    private fun tryToggleTemporarilyShowHidden() {
        if (config.temporarilyShowHidden) {
            toggleTemporarilyShowHidden(false)
        } else {
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
            } else {
                handleHiddenFolderPasswordProtection {
                    toggleTemporarilyShowHidden(true)
                }
            }
        }
    }

    private fun toggleTemporarilyShowHidden(show: Boolean) {
        mLoadedInitialPhotos = false
        config.temporarilyShowHidden = show
        getMedia()
        refreshMenuItems()
    }

    private fun setupLayoutManager() {
        val isGrid = isGridViewType()
        // arranging by hand is a one-finger business, and reordering forces a count that can be
        // dragged at anyway
        mPinchZoom.isEnabled = isGrid && !mIsReordering
        if (isGrid) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            binding.mediaRefreshLayout.layoutParams = fillRemainingHeightParams(
                width = ViewGroup.LayoutParams.WRAP_CONTENT
            )
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            binding.mediaRefreshLayout.layoutParams = fillRemainingHeightParams(
                width = ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // a count stored before this ladder existed, or taken from the other orientation, is not
        // necessarily one of its rungs
        config.mediaColumnCnt = mGridZoom.snap(config.mediaColumnCnt)
        layoutManager.spanCount = config.mediaColumnCnt
        val adapter = getMediaAdapter()
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter?.isASectionTitle(position) == true) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
        binding.mediaRefreshLayout.layoutParams = fillRemainingHeightParams(
            width = ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    // the grid shares a column with the reorder bar, so it takes the height that bar leaves over
    private fun fillRemainingHeightParams(width: Int) = LinearLayout.LayoutParams(width, 0, 1f)

    private fun handleGridSpacing(media: ArrayList<ThumbnailItem> = mediaForGrid()) {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            val spanCount = config.mediaColumnCnt
            val spacing = config.thumbnailSpacing
            val useGridPosition = media.firstOrNull() is ThumbnailSection

            var currentGridDecoration: GridSpacingItemDecoration? = null
            if (binding.mediaGrid.itemDecorationCount > 0) {
                currentGridDecoration =
                    binding.mediaGrid.getItemDecorationAt(0) as GridSpacingItemDecoration
                currentGridDecoration.items = media
            }

            val newGridDecoration = GridSpacingItemDecoration(
                spanCount = spanCount,
                spacing = spacing,
                isScrollingHorizontally = config.scrollHorizontally,
                addSideSpacing = config.fileRoundedCorners,
                items = media,
                useGridPosition = useGridPosition
            )
            if (currentGridDecoration.toString() != newGridDecoration.toString()) {
                if (currentGridDecoration != null) {
                    binding.mediaGrid.removeItemDecoration(currentGridDecoration)
                }
                binding.mediaGrid.addItemDecoration(newGridDecoration)
            }
        }
    }

    private fun changeColumnCount() {
        val items = mGridZoom.rungs.map {
            RadioItem(
                id = it,
                title = resources.getQuantityString(
                    org.fossify.commons.R.plurals.column_counts, it, it
                )
            )
        } as ArrayList<RadioItem>

        val currentColumnCount = (binding.mediaGrid.layoutManager as MyGridLayoutManager).spanCount
        RadioGroupDialog(this, items, currentColumnCount) {
            setColumnCount(it as Int)
        }
    }

    /** One count along in a pinch, keeping whatever the fingers came down on where it was. */
    private fun stepColumnCount(columnCount: Int) {
        if (columnCount == config.mediaColumnCnt) {
            return
        }

        setColumnCount(columnCount)
        restoreZoomAnchor()
    }

    private fun setColumnCount(columnCount: Int) {
        if (columnCount == config.mediaColumnCnt) {
            return
        }

        config.mediaColumnCnt = columnCount
        getMediaAdapter()?.finishActMode()
        columnCountChanged()
    }

    private fun columnCountChanged() {
        (binding.mediaGrid.layoutManager as MyGridLayoutManager).spanCount = config.mediaColumnCnt
        applyGridPerformanceTuning()

        // crossing into or out of the simplified counts swaps the item every position is drawn with
        // and drops the grouping headers with them, so the grid is handed a different list
        val adapter = getMediaAdapter()
        val simplified = isGridSimplified()
        if (adapter != null && adapter.isSimplified != simplified) {
            adapter.setSimplified(simplified, mediaForGrid())
        } else {
            adapter?.apply { notifyItemRangeChanged(0, media.size) }
        }

        handleGridSpacing()
        refreshMenuItems()
    }

    /**
     * Sizes the grid's caches to the count it is drawing, and takes the change animation away once
     * simplified - at these counts it is hundreds of overlapping fades over a rebind that redraws
     * everything anyway.
     */
    private fun applyGridPerformanceTuning() {
        // the change animation has to stay on wherever a thumbnail is sized from its tile: it is
        // what binds the new count onto a fresh view, and Glide takes its size from the view it is
        // handed. Rebinding the old view in place hands it the size the tile used to be, and the
        // picture comes back too small for the tile now drawing it
        binding.mediaGrid.itemAnimator = if (isGridSimplified()) null else mDefaultItemAnimator
        getMediaAdapter()?.tuneCachesForColumnCount(config.mediaColumnCnt)
    }

    private fun isGridViewType() =
        config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath) == VIEW_TYPE_GRID

    /** Whether the grid is zoomed out past what a thumbnail can be read or tapped at. */
    private fun isGridSimplified() = isGridViewType()
        && !mIsReordering
        && mGridZoom.isSimplified(config.mediaColumnCnt)

    /**
     * The list the grid draws: the media as fetched, minus the grouping headers once the grid is
     * zoomed out far enough that they would only leave ragged gaps between the tiles.
     */
    private fun mediaForGrid(source: ArrayList<ThumbnailItem> = mMedia): ArrayList<ThumbnailItem> {
        return if (isGridSimplified()) {
            source.filterTo(ArrayList()) { it is Medium }
        } else {
            source
        }
    }

    /**
     * A tap in the simplified grid steps one rung back down the ladder, keeping whatever was under
     * the finger under it - the only way back in, since nothing there is tappable in its own right.
     */
    private fun setupZoomInOnTap() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                // a tap that only stops a fling is not one asking to zoom in
                mWasGridIdleOnTouch = binding.mediaGrid.scrollState == RecyclerView.SCROLL_STATE_IDLE
                return false
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (mWasGridIdleOnTouch) {
                    zoomInAt(e.x, e.y)
                }

                return false
            }
        })

        binding.mediaGrid.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (getMediaAdapter()?.isSimplified == true) {
                    detector.onTouchEvent(e)
                }

                // never steals the event: the grid still scrolls, and a simplified item has no
                // listener of its own for this to compete with
                return false
            }
        })
    }

    private fun zoomInAt(x: Float, y: Float) {
        val columnCount = mGridZoom.zoomIn(config.mediaColumnCnt)
        if (columnCount == config.mediaColumnCnt) {
            return
        }

        captureZoomAnchor(x, y)
        setColumnCount(columnCount)
        restoreZoomAnchor()
    }

    /**
     * Marks whatever is under ([x], [y]) as the item the grid is to be rebuilt around. A pinch takes
     * this once, at the start: retaken each step, it would follow the item's own drift as the tiles
     * resize rather than hold it still.
     */
    private fun captureZoomAnchor(x: Float, y: Float) {
        val path = binding.mediaGrid.findChildViewUnder(x, y)
            ?.let { binding.mediaGrid.getChildAdapterPosition(it) }
            ?.let { getMediaAdapter()?.media?.getOrNull(it) as? Medium }
            ?.path

        val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
        val offset = if (layoutManager.orientation == RecyclerView.HORIZONTAL) {
            x - binding.mediaGrid.paddingLeft
        } else {
            y - binding.mediaGrid.paddingTop
        }

        mZoomAnchor = path?.to(offset.coerceAtLeast(0f))
    }

    /** Puts the anchored item back where it was found. */
    private fun restoreZoomAnchor() {
        val (path, offset) = mZoomAnchor ?: return
        // posted: the count it is being put back at has only just reached the layout manager, and
        // the list it is being looked up in may have just lost or gained its grouping headers
        binding.mediaGrid.post {
            val position = getMediaAdapter()?.getItemKeyPosition(path.hashCode()) ?: return@post
            if (position == -1) {
                return@post
            }

            (binding.mediaGrid.layoutManager as MyGridLayoutManager)
                .scrollToPositionWithOffset(position, offset.toInt())
        }
    }

    private fun isSetWallpaperIntent() = intent.getBooleanExtra(SET_WALLPAPER_INTENT, false)

    private fun itemClicked(path: String) {
        hideKeyboard()
        if (isSetWallpaperIntent()) {
            toast(R.string.setting_wallpaper)

            val wantedWidth = wallpaperDesiredMinimumWidth
            val wantedHeight = wallpaperDesiredMinimumHeight
            val ratio = wantedWidth.toFloat() / wantedHeight

            val options = RequestOptions()
                .override((wantedWidth * ratio).toInt(), wantedHeight)
                .fitCenter()

            Glide.with(this)
                .asBitmap()
                .load(File(path))
                .apply(options)
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        try {
                            WallpaperManager.getInstance(applicationContext).setBitmap(resource)
                            setResult(RESULT_OK)
                        } catch (ignored: IOException) {
                        }

                        finish()
                    }
                })
        } else if (mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent) {
            Intent().apply {
                data = path.toUri()
                setResult(RESULT_OK, this)
            }
            finish()
        } else {
            mWasFullscreenViewOpen = true
            viewerReturn.opening(path)
            if (!path.isVideoFast()) {
                openInViewPager(path)
                return
            }

            when (config.videoPlayerType) {
                VIDEO_PLAYER_SYSTEM -> openSystemDefaultPlayer(path)
                VIDEO_PLAYER_APP -> if (config.gestureVideoPlayer) launchGesturePlayer(path) else openInViewPager(path)
                else -> openInViewPager(path) // unreachable by design
            }
        }
    }

    private fun openInViewPager(path: String) {
        Intent(this, ViewPagerActivity::class.java).apply {
            putExtra(SKIP_AUTHENTICATION, shouldSkipAuthentication())
            putExtra(PATH, path)
            putExtra(SHOW_ALL, mShowAll)
            putExtra(SHOW_FAVORITES, mPath == FAVORITES)
            putExtra(SHOW_RECYCLE_BIN, mPath == RECYCLE_BIN)
            putExtra(IS_FROM_GALLERY, true)
            startActivityForResult(this, ViewerReturn.REQUEST_CODE)
        }
    }

    private fun openSystemDefaultPlayer(path: String) {
        openPath(
            path = path,
            forceChooser = false,
            extras = hashMapOf(SHOW_FAVORITES to (mPath == FAVORITES)).apply {
                if (path.startsWith(recycleBinPath)) put(IS_IN_RECYCLE_BIN, true)
                if (shouldSkipAuthentication()) put(SKIP_AUTHENTICATION, true)
            }
        )
    }

    private fun gotMedia(media: ArrayList<ThumbnailItem>, isFromCache: Boolean) {
        mIsGettingMedia = false
        checkLastMediaChanged()
        mMedia = media

        runOnUiThread {
            binding.loadingIndicator.hide()
            binding.mediaRefreshLayout.isRefreshing = false
            binding.mediaEmptyTextPlaceholder.beVisibleIf(media.isEmpty() && !isFromCache)
            binding.mediaEmptyTextPlaceholder2.beVisibleIf(media.isEmpty() && !isFromCache)

            if (binding.mediaEmptyTextPlaceholder.isVisible()) {
                binding.mediaEmptyTextPlaceholder.text = getString(R.string.no_media_with_filters)
            }
            binding.mediaFastscroller.beVisibleIf(binding.mediaEmptyTextPlaceholder.isGone())
            setupAdapter()
        }

        mLatestMediaId = getLatestMediaId()
        mLatestMediaDateId = getLatestMediaByDateId()
        if (!isFromCache) {
            val mediaToInsert = mMedia
                .filter { it is Medium && it.deletedTS == 0L }.map { it as Medium }
            Thread {
                try {
                    mediaDB.insertAll(mediaToInsert)
                } catch (e: Exception) {
                }
            }.start()
        }
    }

    override fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>, skipRecycleBin: Boolean) {
        val filtered = fileDirItems
            .filter { !getIsPathDirectory(it.path) && it.path.isMediaFile() } as ArrayList
        if (filtered.isEmpty()) {
            return
        }

        if (
            config.useRecycleBin
            && !skipRecycleBin
            && !filtered.first().path.startsWith(recycleBinPath)
        ) {
            val movingItems = resources.getQuantityString(
                org.fossify.commons.R.plurals.moving_items_into_bin,
                filtered.size,
                filtered.size
            )
            toast(movingItems)

            movePathsInRecycleBin(filtered.map { it.path } as ArrayList<String>) {
                if (it) {
                    deleteFilteredFiles(filtered)
                } else {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
            }
        } else {
            val deletingItems = resources.getQuantityString(
                org.fossify.commons.R.plurals.deleting_items,
                filtered.size,
                filtered.size
            )
            toast(deletingItems)
            deleteFilteredFiles(filtered)
        }
    }

    private fun shouldSkipAuthentication(): Boolean {
        return intent.getBooleanExtra(SKIP_AUTHENTICATION, false)
    }

    private fun deleteFilteredFiles(filtered: ArrayList<FileDirItem>) {
        deleteFiles(filtered) {
            if (!it) {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                return@deleteFiles
            }

            val deletedPaths = filtered.mapTo(HashSet(filtered.size)) { it.path }
            mMedia.removeAll { deletedPaths.contains((it as? Medium)?.path) }

            ensureBackgroundThread {
                val useRecycleBin = config.useRecycleBin
                filtered.forEach {
                    if (it.path.startsWith(recycleBinPath) || !useRecycleBin) {
                        deleteDBPath(it.path)
                    }
                }
            }

            if (mMedia.isEmpty()) {
                deleteDirectoryIfEmpty()
                deleteDBDirectory()
                finish()
            }
        }
    }

    override fun refreshItems() {
        getMedia()
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        Intent().apply {
            putExtra(PICKED_PATHS, paths)
            setResult(RESULT_OK, this)
        }
        finish()
    }

    override fun updateMediaGridDecoration(media: ArrayList<ThumbnailItem>) {
        var currentGridPosition = 0
        media.forEach {
            if (it is Medium) {
                it.gridPosition = currentGridPosition++
            } else if (it is ThumbnailSection) {
                currentGridPosition = 0
            }
        }

        if (binding.mediaGrid.itemDecorationCount > 0) {
            val currentGridDecoration =
                binding.mediaGrid.getItemDecorationAt(0) as GridSpacingItemDecoration
            currentGridDecoration.items = media
        }
    }

}
