package org.fossify.gallery.views

import android.app.Activity.RESULT_OK
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.view.GestureDetector
import android.view.Menu
import androidx.core.view.children
import android.view.MotionEvent
import android.view.View
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
import java.io.File
import java.io.IOException
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.CreateNewFolderDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.deleteFiles
import org.fossify.commons.extensions.ensureBasePadding
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
import org.fossify.commons.extensions.toast
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
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.activities.PeekViewerActivity
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.adapters.MediaGridNavigator
import org.fossify.gallery.asynctasks.GetMediaAsynctask
import org.fossify.gallery.databinding.PaneMediaGridBinding
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
import org.fossify.gallery.extensions.launchGesturePlayer
import org.fossify.gallery.extensions.launchSettings
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
import org.fossify.gallery.helpers.GridPinchZoom
import org.fossify.gallery.helpers.GridSpacingItemDecoration
import org.fossify.gallery.helpers.GridZoom
import org.fossify.gallery.helpers.IS_IN_RECYCLE_BIN
import org.fossify.gallery.helpers.MEDIA_GRID_MENU
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.ViewerTransition
import org.fossify.gallery.helpers.PICKED_PATHS
import org.fossify.gallery.helpers.PeekSession
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ReorderBar
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_FAVORITES
import org.fossify.gallery.helpers.SHOW_RECYCLE_BIN
import org.fossify.gallery.helpers.SKIP_AUTHENTICATION
import org.fossify.gallery.helpers.SLIDESHOW_START_ON_ENTER
import org.fossify.gallery.helpers.VIDEO_PLAYER_APP
import org.fossify.gallery.helpers.VIDEO_PLAYER_SYSTEM
import org.fossify.gallery.helpers.ViewerReturn
import org.fossify.gallery.interfaces.GridPane
import org.fossify.gallery.interfaces.MediaOperationsListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection

/** What a third party app asked for, when the grid was opened to pick something for one. */
data class PickRequest(
    val image: Boolean = false,
    val video: Boolean = false,
    val any: Boolean = false,
    val wallpaper: Boolean = false,
    val allowMultiple: Boolean = false,
) {
    /** Whether the grid is up for another app to pick something out of rather than to be browsed. */
    val isPicking get() = image || video || any || wallpaper
}

/** How commons keeps a view's own padding, before the window insets are added to it. */
private const val BASE_PADDING_SIDES = 4
private const val BASE_PADDING_BOTTOM = 3

private const val LAST_MEDIA_CHECK_PERIOD = 3000L

/** Kept apart from [ViewerReturn.REQUEST_CODE]: a peek is not a trip to the full viewer. */
private const val REQUEST_PEEK = 2002

/**
 * A grid of media - one folder's, or the whole library's - as one pane of a browsing screen rather
 * than a screen of its own.
 *
 * All of this was [MediaActivity] until the two top level grids had to share a window, which they
 * can only do if neither of them is an activity of its own. What stayed behind is the window and
 * the chrome floating over it; what came here is the grid and every last thing done to it.
 */
class MediaGridPane(
    private val activity: SimpleActivity,
    private val binding: PaneMediaGridBinding,
    private val host: Host,
    private val mPath: String,
    showAll: Boolean,
    private val pick: PickRequest = PickRequest(),
    private val skipAuthentication: Boolean = false,
) : GridPane, MediaOperationsListener {

    /** The parts of a screen a pane cannot answer for, belonging to the screen around it. */
    interface Host {
        val topBar: MySearchMenu

        /** The toolbar's entries follow what the grid is showing, so this asks for them again. */
        fun refreshMenu()

        /** What keeps clear of the navigation bar changes as the reorder bar comes and goes. */
        fun applyInsets()

        /** Arranging or selecting takes the screen over, and the chrome has to answer for it. */
        fun onPaneStateChanged()

        /** The way back out of a folder, which only the screen holding the pane knows. */
        fun navigateUp()
    }

    // read off the activity, so the code that moved here reads the way it did inside one
    private val config get() = activity.config
    private val resources get() = activity.resources

    // the one copy of what is on screen, still kept where the viewer and the peek look for it
    private var mMedia
        get() = MediaActivity.mMedia
        set(value) {
            MediaActivity.mMedia = value
        }

    private val mShowAll = showAll && mPath != RECYCLE_BIN
    private val mIsGetImageIntent = pick.image
    private val mIsGetVideoIntent = pick.video
    private val mIsGetAnyIntent = pick.any
    private val mAllowPickingMultiple = pick.allowMultiple

    private var mIsGettingMedia = false

    /**
     * Whether this pane is the one on screen. Its own [activity] being alive used to answer that -
     * the grid you left was an activity of its own and was destroyed - and since the two grids share
     * a window it answers nothing, so the loop below would keep an invisible grid rescanning.
     */
    private var mIsActive = false
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
    private var mCurrAsyncTask: GetMediaAsynctask? = null
    private var mDefaultItemAnimator: RecyclerView.ItemAnimator? = null

    // what a search narrowed the grid down to, null when no search is open. Anything rebuilding the
    // grid has to work from this rather than mMedia, or it puts the whole library back on screen
    private var mSearchResults: ArrayList<ThumbnailItem>? = null

    /**
     * The item a zoom is keeping in place and how far down the grid to keep it. Held as a path
     * rather than a position, which the dropped grouping headers shift.
     */
    private var mZoomAnchor: Pair<String, Float>? = null

    // built on first use rather than here, where there is no context to read yet, and dropped when
    // the scroll direction changes which axis it divides
    private var mCachedGridZoom: GridZoom? = null
    private val mGridZoom: GridZoom
        get() = mCachedGridZoom ?: activity.mediaGridZoom().also { mCachedGridZoom = it }

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

    // the pill navigates away, so it has to go while a selection it would drop is on
    private var mIsSelecting = false
    private val reorderBar by lazy { ReorderBar(binding.mediaReorderBar) }
    private val viewerReturn = ViewerReturn()

    override val root: View get() = binding.root
    override val grid get() = binding.mediaGrid
    override val refreshLayout get() = binding.mediaRefreshLayout
    override val menuRes = R.menu.menu_media
    override val menuSpec = MEDIA_GRID_MENU

    /** Whether an arrangement is being made, which takes the bottom of the screen over. */
    val isReordering get() = mIsReordering

    /**
     * Opens the viewer on [path] straight away, which is what a tab sitting on a file asks for.
     *
     * Deliberately not held back until this grid has its media: the viewer fetches its own either
     * way, and waiting only means showing a grid the tab was never on for as long as the scan
     * takes. The grid still loads behind it, ready to be come back out onto.
     */
    fun openViewer(path: String) {
        mWasFullscreenViewOpen = true
        viewerReturn.opening(path)
        openInViewPager(path)
    }

    /** Where a tab left this grid, put back on the pass that fills it. */
    fun restoreScrollTo(path: String, offset: Int) {
        mGridPositionToRestore = MediaGridNavigator.GridPosition(path, offset)
    }

    /** Where the grid is sitting, for the tab that is up to remember it by. */
    fun currentGridPosition(): Pair<String, Int>? {
        val position = getMediaAdapter()?.gridNavigator?.currentPosition() ?: return null
        return position.path to position.offset
    }

    /** Whether a selection is on, which the pill must not be able to navigate away from. */
    val isSelecting get() = mIsSelecting

    /** How much room the grid is currently keeping at its foot for a pill floating over it. */
    private var mBottomRoom = 0

    /**
     * Makes room at the foot of the grid for the selection's pill, where the screen showing this
     * pane has none of its own to spare - a folder opened as a screen keeps no navigation pill, so
     * without this its last row would sit under the pill with nothing left to scroll.
     *
     * The room goes into the base commons rebuilds the padding from as well as into the padding
     * itself: anything merely added on top is dropped the next time the window insets come round.
     */
    fun reserveBottomRoom(reserve: Boolean) {
        val room = if (reserve) resources.getDimensionPixelSize(R.dimen.nav_pill_reserved_height) else 0
        if (room == mBottomRoom) {
            return
        }

        binding.mediaGrid.ensureBasePadding().let { base ->
            if (base.size == BASE_PADDING_SIDES) {
                base[BASE_PADDING_BOTTOM] = room
            }
        }

        binding.mediaGrid.updatePadding(bottom = binding.mediaGrid.paddingBottom - mBottomRoom + room)
        mBottomRoom = room
    }

    init {
        binding.mediaRefreshLayout.setOnRefreshListener { getMedia() }
        setupReorderBar()
        storeStateVariables()
        // registering the pinch (which the lazy does) before the tap gives it first refusal on the
        // grid's touches - item touch listeners are asked in the order they were added
        mPinchZoom.isEnabled = isGridViewType()
        setupZoomInOnTap()
        mDefaultItemAnimator = binding.mediaGrid.itemAnimator
        binding.mediaEmptyTextPlaceholder2.setOnClickListener {
            showFilterMediaDialog()
        }
    }

    override fun onActivated() {
        mIsActive = true
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

        if (mStoredTextColor != activity.getProperTextColor()) {
            getMediaAdapter()?.updateTextColor(activity.getProperTextColor())
        }

        val primaryColor = activity.getProperPrimaryColor()
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
            timeFormat = activity.getTimeFormat()
        }

        binding.loadingIndicator.setIndicatorColor(activity.getProperPrimaryColor())
        reorderBar.updateColors()
        binding.mediaEmptyTextPlaceholder.setTextColor(activity.getProperTextColor())
        binding.mediaEmptyTextPlaceholder2.setTextColor(activity.getProperPrimaryColor())
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
                activity.handleLockedFolderOpening(mPath) { success ->
                    if (success) {
                        tryLoadGallery()
                    } else {
                        activity.finish()
                    }
                }
            }
        }
    }

    override fun onDeactivated() {
        mIsActive = false
        mIsGettingMedia = false
        binding.mediaRefreshLayout.isRefreshing = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)

        if (!mMedia.isEmpty()) {
            mCurrAsyncTask?.stopFetching()
        }
    }

    override fun handleBack(): Boolean {
        return if (mIsReordering) {
            cancelReordering()
            true
        } else if (host.topBar.isSearchOpen) {
            host.topBar.closeSearch()
            true
        } else {
            false
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == REQUEST_EDIT_IMAGE) {
            if (resultCode == RESULT_OK && resultData != null) {
                mMedia.clear()
                refreshItems()
            }
        } else if (requestCode == ViewerReturn.REQUEST_CODE) {
            viewerReturn.onViewerResult(resultData)
        } else if (requestCode == REQUEST_PEEK) {
            viewerReturn.onViewerResult(resultData)
            // the peek is a selection tool, so what it picked out is what the action mode now holds
            getMediaAdapter()?.applySelection(PeekSession.selectedPaths)
            PeekSession.clear()
        }
    }

    /** Asks the screen for the toolbar's entries again, which comes straight back below. */
    private fun refreshMenuItems() {
        host.refreshMenu()
    }

    override fun refreshMenuItems(menu: Menu) {
        // nothing in here should be reachable mid drag, every entry would pull the grid out from
        // under the arrangement the user is in the middle of making. the entries the rules below
        // never touch are always meant to be there, so put everything back before applying them
        val isVisibleByDefault = !mIsReordering
        menu.children.forEach { it.isVisible = isVisibleByDefault }

        if (mIsReordering) {
            return
        }

        menu.apply {
            findItem(R.id.custom_order).isVisible = mPath != RECYCLE_BIN && !isAllMediaGrid()
            // Reset outlives Custom order here: a grid arranged before it went away is the one
            // thing that still needs the way back out
            findItem(R.id.reset_custom_order).isVisible =
                mPath != RECYCLE_BIN && config.hasCustomMediaOrder(getPathToUse())

            findItem(R.id.empty_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.empty_disable_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.restore_all_files).isVisible = mPath == RECYCLE_BIN

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

    /**
     * The hint names what is being searched, and a folder reached by tapping into one wears the way
     * back out of it - the all media grid is a top level screen with nowhere above it to go.
     */
    override fun dressTopBar(topBar: MySearchMenu) {
        val dirName = when (mPath) {
            FAVORITES -> activity.getString(org.fossify.commons.R.string.favorites)
            RECYCLE_BIN -> activity.getString(org.fossify.commons.R.string.recycle_bin)
            config.OTGPath -> activity.getString(org.fossify.commons.R.string.usb)
            else -> activity.getHumanizedFilename(mPath)
        }

        // the folder's own name rather than "Search in <folder>": the bar is where you are as much
        // as it is a search box, and the open folder group's name reads the same way on the pill
        topBar.updateHintText(
            if (mShowAll) activity.getString(org.fossify.commons.R.string.search_files) else dirName
        )

        topBar.toggleForceArrowBackIcon(!mShowAll)
        if (!mShowAll) {
            topBar.onNavigateBackClickListener = { host.navigateUp() }
        }
    }

    override fun onSearchToggled(isOpen: Boolean) {
        host.onPaneStateChanged()
    }

    override fun onSearchTextChanged(text: String) {
        mLastSearchedText = text
        searchQueryChanged(text)
        binding.mediaRefreshLayout.isEnabled = text.isEmpty() && config.enablePullToRefresh
    }

    override fun onMenuItemClick(itemId: Int): Boolean {
        when (itemId) {
            R.id.sort -> showSortingDialog()
            R.id.filter -> showFilterMediaDialog()
            R.id.empty_recycle_bin -> emptyRecycleBin()
            R.id.empty_disable_recycle_bin -> emptyAndDisableRecycleBin()
            R.id.restore_all_files -> restoreAllFiles()
            R.id.toggle_filename -> toggleFilenameVisibility()
            // no menu offers this any more, the id lives in ids.xml - kept so it can be put back
            R.id.open_camera -> activity.launchCamera()
            R.id.change_view_type -> changeViewType()
            R.id.custom_order -> startReordering()
            R.id.reset_custom_order -> resetCustomOrder()
            R.id.create_new_folder -> createNewFolder()
            R.id.open_recycle_bin -> activity.openRecycleBin()
            R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
            R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
            R.id.column_count -> changeColumnCount()
            R.id.slideshow -> startSlideshow()
            R.id.settings -> activity.launchSettings()
            R.id.about -> activity.launchAbout()
            else -> return false
        }

        return true
    }

    private fun startSlideshow() {
        if (mMedia.isNotEmpty()) {
            activity.hideKeyboard()
            Intent(activity, ViewPagerActivity::class.java).apply {
                val item = mMedia.firstOrNull { it is Medium } as? Medium ?: return
                putExtra(SKIP_AUTHENTICATION, shouldSkipAuthentication())
                putExtra(PATH, item.path)
                putExtra(SHOW_ALL, mShowAll)
                putExtra(SLIDESHOW_START_ON_ENTER, true)
                putExtra(IS_FROM_GALLERY, true)
                viewerReturn.opening(item.path)
                activity.startActivityForResult(this, ViewerReturn.REQUEST_CODE)
            }
        }
    }

    // repainted on every resume rather than set once: the fades are drawn in the theme's own
    // background colour, and the theme can change while this screen is in the back stack
    private fun updateEdgeFades() {
        binding.mediaTopFade.applyEdgeFade(atTop = true)
        binding.mediaBottomFade.applyEdgeFade(atTop = false)
    }

    // the folder a custom order is keyed by, matching what the fetching and grouping code uses
    private fun getPathToUse() = (if (mShowAll) SHOW_ALL else mPath).ifEmpty { SHOW_ALL }

    /**
     * Whether the grid is every folder at once rather than one folder of its own - the Pictures
     * pane, or any folder opened while "show all folders content" is on.
     *
     * Nothing here can be arranged by hand. An order is a folder's own and this grid belongs to no
     * folder - it is the whole library, which grows and shrinks under any arrangement made of it.
     */
    private fun isAllMediaGrid() = getPathToUse() == SHOW_ALL

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
        if (mIsReordering || isAllMediaGrid()) {
            return
        }

        activity.hideKeyboard()
        if (host.topBar.isSearchOpen) {
            host.topBar.closeSearch()
        }

        // arranging at a count where no single item can be picked out is not arranging anything
        if (mGridZoom.isSimplified(config.mediaColumnCnt)) {
            setColumnCount(mGridZoom.largestInteractive)
        }

        val flatMedia = mMedia.filterIsInstance<Medium>().toMutableList() as ArrayList<ThumbnailItem>
        if (flatMedia.size < 2) {
            activity.toast(R.string.reorder_needs_more_items)
            return
        }

        mIsReordering = true
        binding.mediaRefreshLayout.isEnabled = false
        reorderBar.show()
        // the bar brings its own solid background, so the darkening behind it would only muddy it
        binding.mediaBottomFade.beGone()
        host.onPaneStateChanged()
        // hand the room the grid was keeping for the navigation bar over to the bar taking its place
        mGridBottomPadding = binding.mediaGrid.paddingBottom
        host.applyInsets()
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
            activity.saveCustomMediaOrder(pathToUse, orderedPaths)
            activity.runOnUiThread {
                // a folder that was just arranged should come up in that order from now on
                config.saveCustomSorting(pathToUse, SORT_BY_CUSTOM)
                activity.toast(R.string.custom_order_saved)
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
        host.onPaneStateChanged()
        host.applyInsets()
        binding.mediaGrid.updatePadding(bottom = mGridBottomPadding)
        binding.mediaRefreshLayout.isEnabled = config.enablePullToRefresh
        refreshMenuItems()
    }

    // an arrangement can be a lot of work to make and nothing brings it back, so ask first
    private fun resetCustomOrder() {
        ConfirmationDialog(
            activity,
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
            activity.removeCustomMediaOrder(pathToUse)
            activity.runOnUiThread {
                if (config.getFolderSorting(pathToUse) and SORT_BY_CUSTOM != 0) {
                    config.removeCustomSorting(pathToUse)
                }

                activity.toast(R.string.custom_order_reset)
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
        mStoredTextColor = activity.getProperTextColor()
        mStoredPrimaryColor = activity.getProperPrimaryColor()
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredShowFileTypes = showThumbnailFileTypes
            mStoredMarkFavoriteItems = markFavoriteItems
            mStoredShowRatings = showThumbnailRating
            mStoredThumbnailSpacing = thumbnailSpacing
            mStoredRoundedCorners = fileRoundedCorners
        }
    }

    private fun searchQueryChanged(text: String) {
        ensureBackgroundThread {
            try {
                val filtered = mMedia
                    .filter { it is Medium && it.name.contains(text, true) } as ArrayList
                filtered.sortBy { it is Medium && !it.name.startsWith(text, true) }
                val grouped = MediaFetcher(activity.applicationContext).groupMedia(
                    media = filtered as ArrayList<Medium>, path = mPath
                )
                activity.runOnUiThread {
                    if (grouped.isEmpty()) {
                        binding.mediaEmptyTextPlaceholder.text =
                            activity.getString(org.fossify.commons.R.string.no_items_found)
                        binding.mediaEmptyTextPlaceholder.beVisible()
                        binding.mediaFastscroller.beGone()
                    } else {
                        binding.mediaEmptyTextPlaceholder.beGone()
                        binding.mediaFastscroller.beVisible()
                    }

                    mSearchResults = if (text.isEmpty()) null else grouped
                    val shown = mediaForGrid(grouped)
                    handleGridSpacing(shown)
                    getMediaAdapter()?.updateMedia(shown)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun tryLoadGallery() {
        activity.requestMediaPermissions {
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
                activity = activity,
                media = mediaForGrid().clone() as ArrayList<ThumbnailItem>,
                listener = this,
                isAGetIntent = mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent,
                allowMultiplePicks = mAllowPickingMultiple,
                path = mPath,
                recyclerView = binding.mediaGrid,
                swipeRefreshLayout = binding.mediaRefreshLayout
            ) {
                if (it is Medium && !activity.isFinishing) {
                    itemClicked(it.path)
                }
            }.apply {
                // the media handed in above is already simplified, so only the flag is left to set
                setSimplifiedInitially(isGridSimplified())
                onPeekRequested = ::openPeekViewer
                onSelectionModeChanged = { selecting ->
                    mIsSelecting = selecting
                    host.onPaneStateChanged()
                }
                binding.mediaGrid.adapter = this
            }

            applyGridPerformanceTuning()

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            if (viewType == VIEW_TYPE_LIST && activity.areSystemAnimationsEnabled) {
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
        if (!mIsActive || activity.isDestroyed) {
            return
        }

        if (config.getFolderSorting(mPath) and SORT_BY_RANDOM != 0) {
            return
        }

        mLastMediaHandler.removeCallbacksAndMessages(null)
        mLastMediaHandler.postDelayed({
            ensureBackgroundThread {
                val mediaId = activity.getLatestMediaId()
                val mediaDateId = activity.getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    // asked again on the way back out: the check above was made before this
                    // ran, and the pane can have been swapped away from in between
                    activity.runOnUiThread {
                        if (mIsActive) {
                            getMedia()
                        }
                    }
                } else {
                    checkLastMediaChanged()
                }
            }
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(activity, isDirectorySorting = false, path = mPath) {
            reloadMedia()
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(activity) {
            mLoadedInitialPhotos = false
            binding.mediaRefreshLayout.isRefreshing = true
            binding.mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun emptyRecycleBin() {
        activity.showRecycleBinEmptyingDialog {
            activity.emptyTheRecycleBin {
                activity.finish()
            }
        }
    }

    private fun emptyAndDisableRecycleBin() {
        activity.showRecycleBinEmptyingDialog {
            activity.emptyAndDisableTheRecycleBin {
                activity.finish()
            }
        }
    }

    private fun restoreAllFiles() {
        val paths = mMedia.filter { it is Medium }.map { (it as Medium).path } as ArrayList<String>
        activity.showRestoreConfirmationDialog(paths.size) {
            activity.restoreRecycleBinPaths(paths) {
                ensureBackgroundThread {
                    activity.directoryDB.deleteDirPath(RECYCLE_BIN)
                }
                activity.finish()
            }
        }
    }

    private fun toggleFilenameVisibility() {
        config.displayFileNames = !config.displayFileNames
        getMediaAdapter()?.updateDisplayFilenames(config.displayFileNames)
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(activity, false, mPath) {
            refreshMenuItems()
            setupLayoutManager()
            binding.mediaGrid.adapter = null
            setupAdapter()
        }
    }

    private fun deleteDirectoryIfEmpty() {
        if (config.deleteEmptyFolders) {
            val fileDirItem = FileDirItem(mPath, mPath.getFilenameFromPath(), true)
            if (!fileDirItem.isDownloadsFolder() && fileDirItem.isDirectory) {
                ensureBackgroundThread {
                    if (fileDirItem.getProperFileCount(activity, true) == 0) {
                        activity.tryDeleteFileDirItem(fileDirItem, true, true)
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
            activity.getCachedMedia(
                mPath,
                mIsGetVideoIntent && !mIsGetImageIntent,
                mIsGetImageIntent && !mIsGetVideoIntent
            ) {
                if (it.isEmpty()) {
                    activity.runOnUiThread {
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
            context = activity.applicationContext,
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
                            if (mPath == FAVORITES && activity.getDoesFilePathExist(it.path)) {
                                activity.favoritesDB.deleteFavoritePath(it.path)
                                activity.mediaDB.updateFavorite(it.path, false)
                            } else {
                                activity.mediaDB.deleteMediumPath(it.path)
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
                    activity.directoryDB.deleteDirPath(FAVORITES)
                }
            }

            if (mPath == RECYCLE_BIN) {
                binding.mediaEmptyTextPlaceholder.setText(org.fossify.commons.R.string.no_items_found)
                binding.mediaEmptyTextPlaceholder.beVisible()
                binding.mediaEmptyTextPlaceholder2.beGone()
            } else {
                activity.finish()
            }

            true
        } else {
            false
        }
    }

    private fun deleteDBDirectory() {
        ensureBackgroundThread {
            try {
                activity.directoryDB.deleteDirPath(mPath)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun createNewFolder() {
        CreateNewFolderDialog(activity, mPath) {
            config.tempFolderPath = it
        }
    }

    private fun tryToggleTemporarilyShowHidden() {
        if (config.temporarilyShowHidden) {
            toggleTemporarilyShowHidden(false)
        } else {
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(activity)
            } else {
                activity.handleHiddenFolderPasswordProtection {
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
        val items = mGridZoom.rungs.mapTo(ArrayList()) {
            RadioItem(
                id = it,
                title = resources.getQuantityString(
                    org.fossify.commons.R.plurals.column_counts, it, it
                )
            )
        }

        val currentColumnCount = (binding.mediaGrid.layoutManager as MyGridLayoutManager).spanCount
        RadioGroupDialog(activity, items, currentColumnCount) {
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

        // crossing into or out of the simplified counts swaps every item's view type and drops the
        // grouping headers, so the grid is handed a different list
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

    private fun applyGridPerformanceTuning() {
        if (!isGridViewType()) {
            return
        }

        // the change animation must stay on for full thumbnails: it is what binds the new count onto
        // a fresh view, and Glide sizes the picture from the view it is handed - rebinding the old
        // one in place asks for the size the tile used to be
        binding.mediaGrid.itemAnimator = if (isGridSimplified()) null else mDefaultItemAnimator
        getMediaAdapter()?.applyColumnCount(config.mediaColumnCnt)
    }

    private fun isGridViewType() =
        config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath) == VIEW_TYPE_GRID

    /** Whether the grid is zoomed out past what a thumbnail can be read or tapped at. */
    private fun isGridSimplified() = isGridViewType()
        && !mIsReordering
        && mGridZoom.isSimplified(config.mediaColumnCnt)

    /** Whatever the grid is currently built from - a search narrows it, everything else is [mMedia]. */
    private fun gridSource() = mSearchResults ?: mMedia

    /**
     * The list the grid draws: its source minus the grouping headers once simplified, where they
     * would only leave ragged gaps between the tiles.
     */
    private fun mediaForGrid(source: ArrayList<ThumbnailItem> = gridSource()): ArrayList<ThumbnailItem> {
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
        var wasIdleOnTouch = false
        val detector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                // a tap that only stops a fling is not one asking to zoom in
                wasIdleOnTouch = binding.mediaGrid.scrollState == RecyclerView.SCROLL_STATE_IDLE
                return false
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (wasIdleOnTouch) {
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
     * Marks whatever is under ([x], [y]) as the item to rebuild the grid around. A pinch takes this
     * once, at the start - retaken each step it would follow the item's own drift.
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
        // posted: the new count has only just reached the layout manager, and the list may have just
        // lost or gained its grouping headers
        binding.mediaGrid.post {
            val position = getMediaAdapter()?.getItemKeyPosition(path.hashCode()) ?: return@post
            if (position == -1) {
                return@post
            }

            (binding.mediaGrid.layoutManager as MyGridLayoutManager)
                .scrollToPositionWithOffset(position, offset.toInt())
        }
    }

    private fun isSetWallpaperIntent() = pick.wallpaper

    private fun itemClicked(path: String) {
        activity.hideKeyboard()
        if (isSetWallpaperIntent()) {
            activity.toast(R.string.setting_wallpaper)

            val wantedWidth = activity.wallpaperDesiredMinimumWidth
            val wantedHeight = activity.wallpaperDesiredMinimumHeight
            val ratio = wantedWidth.toFloat() / wantedHeight

            val options = RequestOptions()
                .override((wantedWidth * ratio).toInt(), wantedHeight)
                .fitCenter()

            Glide.with(activity)
                .asBitmap()
                .load(File(path))
                .apply(options)
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        try {
                            WallpaperManager.getInstance(activity.applicationContext).setBitmap(resource)
                            activity.setResult(RESULT_OK)
                        } catch (ignored: IOException) {
                        }

                        activity.finish()
                    }
                })
        } else if (mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent) {
            Intent().apply {
                data = path.toUri()
                activity.setResult(RESULT_OK, this)
            }
            activity.finish()
        } else {
            mWasFullscreenViewOpen = true
            viewerReturn.opening(path)
            // grows the tapped tile into the fullscreen picture, see ViewerTransition
            ViewerTransition.beginFlight(activity, getMediaAdapter(), mMedia, path)
            if (!path.isVideoFast()) {
                openInViewPager(path)
                return
            }

            when (config.videoPlayerType) {
                VIDEO_PLAYER_SYSTEM -> openSystemDefaultPlayer(path)
                VIDEO_PLAYER_APP -> if (config.gestureVideoPlayer) {
                    activity.launchGesturePlayer(path)
                } else {
                    openInViewPager(path)
                }
                else -> openInViewPager(path) // unreachable by design
            }
        }
    }

    /**
     * Opens the peek viewer on [path]: a look at a picture too small to judge in the grid, without
     * having to leave the selection to take it. What it hands back is read in [onActivityResult].
     */
    private fun openPeekViewer(media: List<Medium>, selectedPaths: Set<String>, path: String) {
        PeekSession.open(media, selectedPaths, path)
        viewerReturn.opening(path)
        ViewerTransition.beginFlight(activity, getMediaAdapter(), mMedia, path)
        activity.startActivityForResult(Intent(activity, PeekViewerActivity::class.java), REQUEST_PEEK)
    }

    private fun openInViewPager(path: String) {
        Intent(activity, ViewPagerActivity::class.java).apply {
            putExtra(SKIP_AUTHENTICATION, shouldSkipAuthentication())
            putExtra(PATH, path)
            putExtra(SHOW_ALL, mShowAll)
            putExtra(SHOW_FAVORITES, mPath == FAVORITES)
            putExtra(SHOW_RECYCLE_BIN, mPath == RECYCLE_BIN)
            putExtra(IS_FROM_GALLERY, true)
            activity.startActivityForResult(this, ViewerReturn.REQUEST_CODE)
        }
    }

    private fun openSystemDefaultPlayer(path: String) {
        activity.openPath(
            path = path,
            forceChooser = false,
            extras = hashMapOf(SHOW_FAVORITES to (mPath == FAVORITES)).apply {
                if (path.startsWith(activity.recycleBinPath)) put(IS_IN_RECYCLE_BIN, true)
                if (shouldSkipAuthentication()) put(SKIP_AUTHENTICATION, true)
            }
        )
    }

    private fun gotMedia(media: ArrayList<ThumbnailItem>, isFromCache: Boolean) {
        mIsGettingMedia = false
        checkLastMediaChanged()
        mMedia = media

        activity.runOnUiThread {
            binding.loadingIndicator.hide()
            binding.mediaRefreshLayout.isRefreshing = false
            binding.mediaEmptyTextPlaceholder.beVisibleIf(media.isEmpty() && !isFromCache)
            binding.mediaEmptyTextPlaceholder2.beVisibleIf(media.isEmpty() && !isFromCache)

            if (binding.mediaEmptyTextPlaceholder.isVisible()) {
                binding.mediaEmptyTextPlaceholder.text = activity.getString(R.string.no_media_with_filters)
            }
            binding.mediaFastscroller.beVisibleIf(binding.mediaEmptyTextPlaceholder.isGone())
            setupAdapter()
        }

        mLatestMediaId = activity.getLatestMediaId()
        mLatestMediaDateId = activity.getLatestMediaByDateId()
        if (!isFromCache) {
            val mediaToInsert = mMedia
                .filter { it is Medium && it.deletedTS == 0L }.map { it as Medium }
            Thread {
                try {
                    activity.mediaDB.insertAll(mediaToInsert)
                } catch (e: Exception) {
                }
            }.start()
        }
    }

    override fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>, skipRecycleBin: Boolean) {
        val filtered = fileDirItems
            .filter { !activity.getIsPathDirectory(it.path) && it.path.isMediaFile() } as ArrayList
        if (filtered.isEmpty()) {
            return
        }

        if (
            config.useRecycleBin
            && !skipRecycleBin
            && !filtered.first().path.startsWith(activity.recycleBinPath)
        ) {
            val movingItems = resources.getQuantityString(
                org.fossify.commons.R.plurals.moving_items_into_bin,
                filtered.size,
                filtered.size
            )
            activity.toast(movingItems)

            activity.movePathsInRecycleBin(filtered.map { it.path } as ArrayList<String>) {
                if (it) {
                    deleteFilteredFiles(filtered)
                } else {
                    activity.toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
            }
        } else {
            val deletingItems = resources.getQuantityString(
                org.fossify.commons.R.plurals.deleting_items,
                filtered.size,
                filtered.size
            )
            activity.toast(deletingItems)
            deleteFilteredFiles(filtered)
        }
    }

    private fun shouldSkipAuthentication(): Boolean {
        return skipAuthentication
    }

    private fun deleteFilteredFiles(filtered: ArrayList<FileDirItem>) {
        activity.deleteFiles(filtered) {
            if (!it) {
                activity.toast(org.fossify.commons.R.string.unknown_error_occurred)
                return@deleteFiles
            }

            val deletedPaths = filtered.mapTo(HashSet(filtered.size)) { it.path }
            mMedia.removeAll { deletedPaths.contains((it as? Medium)?.path) }

            ensureBackgroundThread {
                val useRecycleBin = config.useRecycleBin
                filtered.forEach {
                    if (it.path.startsWith(activity.recycleBinPath) || !useRecycleBin) {
                        activity.deleteDBPath(it.path)
                    }
                }
            }

            if (mMedia.isEmpty()) {
                deleteDirectoryIfEmpty()
                deleteDBDirectory()
                activity.finish()
            }
        }
    }

    override fun refreshItems() {
        getMedia()
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        Intent().apply {
            putExtra(PICKED_PATHS, paths)
            activity.setResult(RESULT_OK, this)
        }
        activity.finish()
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
