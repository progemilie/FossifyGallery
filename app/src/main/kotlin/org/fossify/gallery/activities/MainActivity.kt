package org.fossify.gallery.activities

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.provider.MediaStore.Video
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import org.fossify.commons.dialogs.CreateNewFolderDialog
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkWhatsNew
import org.fossify.commons.extensions.deleteFiles
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileCount
import org.fossify.commons.extensions.getFilePublicUri
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getLatestMediaByDateId
import org.fossify.commons.extensions.getLatestMediaId
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperSize
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getStorageDirectories
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.commons.extensions.hasAllPermissions
import org.fossify.commons.extensions.hasOTGConnected
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.launchMoreAppsFromUsIntent
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.sdCardPath
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toFileDirItem
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.DAY_SECONDS
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.PERMISSION_READ_STORAGE
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.Release
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.R
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.databinding.ActivityMainBinding
import org.fossify.gallery.dialogs.ChangeSortingDialog
import org.fossify.gallery.dialogs.ChangeViewTypeDialog
import org.fossify.gallery.dialogs.FilterMediaDialog
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.extensions.addTempFolderIfNeeded
import org.fossify.gallery.extensions.applyEdgeFade
import org.fossify.gallery.extensions.applyFolderGroups
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.createDirectoryFromMedia
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.expandFolderGroups
import org.fossify.gallery.extensions.folderGroups
import org.fossify.gallery.extensions.getCachedDirectories
import org.fossify.gallery.extensions.getCachedMedia
import org.fossify.gallery.extensions.getDirectorySortingValue
import org.fossify.gallery.extensions.getDirsToShow
import org.fossify.gallery.extensions.getDistinctPath
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.getNoMediaFoldersSync
import org.fossify.gallery.extensions.getOTGFolderChildrenNames
import org.fossify.gallery.extensions.getSortedDirectories
import org.fossify.gallery.extensions.handleExcludedFolderPasswordProtection
import org.fossify.gallery.extensions.handleMediaManagementPrompt
import org.fossify.gallery.extensions.isDownloadsFolder
import org.fossify.gallery.extensions.isStartupTargetGone
import org.fossify.gallery.extensions.launchAbout
import org.fossify.gallery.extensions.launchCamera
import org.fossify.gallery.extensions.launchSettings
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.movePathsInRecycleBin
import org.fossify.gallery.extensions.movePinnedDirectoriesToFront
import org.fossify.gallery.extensions.openRecycleBin
import org.fossify.gallery.extensions.pruneFolderGroups
import org.fossify.gallery.extensions.removeInvalidDBDirectories
import org.fossify.gallery.extensions.startupGroupId
import org.fossify.gallery.extensions.storeDirectoryItems
import org.fossify.gallery.extensions.tryDeleteFileDirItem
import org.fossify.gallery.extensions.updateDBDirectory
import org.fossify.gallery.extensions.updateWidgets
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.FOLDER_GRID_MENU
import org.fossify.gallery.helpers.GET_ANY_INTENT
import org.fossify.gallery.helpers.GET_IMAGE_INTENT
import org.fossify.gallery.helpers.GET_VIDEO_INTENT
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_DAILY
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_MONTHLY
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_DAILY
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_MONTHLY
import org.fossify.gallery.helpers.GROUP_DESCENDING
import org.fossify.gallery.helpers.GridChrome
import org.fossify.gallery.helpers.GridPinchZoom
import org.fossify.gallery.helpers.LOCATION_INTERNAL
import org.fossify.gallery.helpers.MAX_COLUMN_COUNT
import org.fossify.gallery.helpers.MONTH_MILLISECONDS
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.PICKED_PATHS
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.SET_WALLPAPER_INTENT
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_TEMP_HIDDEN_DURATION
import org.fossify.gallery.helpers.SKIP_AUTHENTICATION
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.getDefaultFileFilter
import org.fossify.gallery.helpers.getPermissionToRequest
import org.fossify.gallery.helpers.getPermissionsToRequest
import org.fossify.gallery.interfaces.DirectoryOperationsListener
import org.fossify.gallery.interfaces.GridPane
import org.fossify.gallery.jobs.NewPhotoFetcher
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import org.fossify.gallery.views.MediaGridPane
import org.fossify.gallery.views.NavDestination
import org.fossify.gallery.views.NavPill

/** Where through a pane swap the search bar hands its buttons from one grid to the other. */
private const val HALFWAY = 0.5f

class MainActivity : SimpleActivity(), DirectoryOperationsListener, GridPane, MediaGridPane.Host {
    override var isSearchBarEnabled = true
    
    companion object {
        private const val PICK_MEDIA = 2
        private const val PICK_WALLPAPER = 3
        private const val LAST_MEDIA_CHECK_PERIOD = 3000L
        private const val ADAPTER_REFRESH_INTERVAL = 500L
    }

    private var mIsPickImageIntent = false
    private var mIsPickVideoIntent = false
    private var mIsGetImageContentIntent = false
    private var mIsGetVideoContentIntent = false
    private var mIsGetAnyContentIntent = false
    private var mIsSetWallpaperIntent = false
    private var mAllowPickingMultiple = false
    private var mIsThirdPartyIntent = false
    private var mIsGettingDirs = false
    private var mLoadedInitialPhotos = false
    private var mShouldStopFetching = false
    private var mWasDefaultFolderChecked = false
    private var mWasMediaManagementPromptShown = false
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L
    private var mLastAdapterRefresh = 0L

    // used at "Group direct subfolders" for navigation
    private var mCurrentPathPrefix = ""

    // used at "Group direct subfolders" for navigating Up with the back button
    private var mOpenedSubfolders = arrayListOf("")

    private var mDateFormat = ""
    private var mTimeFormat = ""
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mLastMediaFetcher: MediaFetcher? = null

    // no ladder here, the folder grid draws every count the same way
    private val mPinchZoom by lazy {
        GridPinchZoom(
            recyclerView = binding.directoryPane.directoriesGrid,
            onZoomIn = {
                if (config.dirColumnCnt > 1) {
                    reduceColumnCount()
                    getRecyclerAdapter()?.finishActMode()
                }
            },
            onZoomOut = {
                if (config.dirColumnCnt < MAX_COLUMN_COUNT) {
                    increaseColumnCount()
                    getRecyclerAdapter()?.finishActMode()
                }
            }
        )
    }
    private var mDirs = ArrayList<Directory>()

    // the whole folder list the grid was last built from, before a search or an open folder group
    // narrowed it. Anything rebuilding the grid starts here - handing it what is on screen would
    // filter an already filtered list down and lose the rest of the library with it
    private var mDirsIgnoringSearch = ArrayList<Directory>()

    // the folder group whose contents the grid is showing, 0 while it is showing the root. Only
    // the view changes - the scan below keeps working on the real folders throughout. Written and
    // read on the main thread alone, so the grid can never be narrowed to a group it has left
    private var mCurrentGroupId = 0L

    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mStoredTextColor = 0
    private var mStoredPrimaryColor = 0
    private var mStoredStyleString = ""
    private val binding by viewBinding(ActivityMainBinding::inflate)
    private val navPill by lazy { NavPill(binding.navPill) }
    private lateinit var chrome: GridChrome

    /**
     * The all media grid, built the first time it is wanted and kept from then on. The swap is only
     * instant because nothing is being made while it runs.
     */
    private var mMediaPane: MediaGridPane? = null

    /** Which of the two grids is up. The folder grid is this screen's own. */
    private var activePane: GridPane = this
    private var mIsSwapping = false

    // the pill navigates away, so it has to go while a selection it would drop is on
    private var mIsSelecting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)

        if (savedInstanceState == null) {
            config.temporarilyShowHidden = false
            config.temporarilyShowExcluded = false
            config.tempSkipDeleteConfirmation = false
            config.tempSkipRecycleBin = false
            removeTempFolder()
            checkRecycleBinItems()
            startNewPhotoFetcher()
        }

        mIsPickImageIntent = isPickImageIntent(intent)
        mIsPickVideoIntent = isPickVideoIntent(intent)
        mIsGetImageContentIntent = isGetImageContentIntent(intent)
        mIsGetVideoContentIntent = isGetVideoContentIntent(intent)
        mIsGetAnyContentIntent = isGetAnyContentIntent(intent)
        mIsSetWallpaperIntent = isSetWallpaperIntent(intent)
        mAllowPickingMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        mIsThirdPartyIntent = mIsPickImageIntent
                || mIsPickVideoIntent
                || mIsGetImageContentIntent
                || mIsGetVideoContentIntent
                || mIsGetAnyContentIntent
                || mIsSetWallpaperIntent

        // the grids reserve the pill's room in the layout, which the inset is then added to. A
        // picker never puts the pill up, so it hands that room back before the base is taken
        if (mIsThirdPartyIntent) {
            binding.directoryPane.directoriesGrid.updatePadding(bottom = 0)
            binding.navPill.root.beGone()
        }

        setupInsetPadding()
        setupChrome()

        binding.directoryPane.directoriesRefreshLayout.setOnRefreshListener { getDirectories() }
        storeStateVariables()
        checkWhatsNewDialog()
        setupLatestMediaId()

        if (!config.wereFavoritesPinned) {
            config.addPinnedFolders(hashSetOf(FAVORITES))
            config.wereFavoritesPinned = true
        }

        if (!config.wasRecycleBinPinned) {
            config.addPinnedFolders(hashSetOf(RECYCLE_BIN))
            config.wasRecycleBinPinned = true
            config.saveFolderGrouping(SHOW_ALL, GROUP_BY_DATE_TAKEN_DAILY or GROUP_DESCENDING)
        }

        if (!config.wasSVGShowingHandled) {
            config.wasSVGShowingHandled = true
            if (config.filterMedia and TYPE_SVGS == 0) {
                config.filterMedia += TYPE_SVGS
            }
        }

        if (!config.wasSortingByNumericValueAdded) {
            config.wasSortingByNumericValueAdded = true
            config.sorting = config.sorting or SORT_USE_NUMERIC_VALUE
        }

        updateWidgets()
        registerFileUpdateListener()

        binding.directoryPane.directoriesSwitchSearching.setOnClickListener {
            launchSearchActivity()
        }

        // just request the permission, tryLoadGallery will then trigger in onResume
        handleMediaPermissions()
    }

    private fun handleMediaPermissions(callback: (() -> Unit)? = null) {
        requestMediaPermissions(enableRationale = true) {
            callback?.invoke()
            if (isRPlus() && !mWasMediaManagementPromptShown) {
                mWasMediaManagementPromptShown = true
                handleMediaManagementPrompt { }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        chrome.updateColors()
        onPaneStateChanged()
        config.isThirdPartyIntent = false
        mDateFormat = config.dateFormat
        mTimeFormat = getTimeFormat()
        activePane.onActivated()
    }

    /** The folder grid brought up: repaint it against the theme, and fetch what it shows. */
    override fun onActivated() {
        updateEdgeFades()
        refreshMenuItems()

        if (mStoredAnimateGifs != config.animateGifs) {
            getRecyclerAdapter()?.updateAnimateGifs(config.animateGifs)
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            getRecyclerAdapter()?.updateCropThumbnails(config.cropThumbnails)
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            mLoadedInitialPhotos = false
            binding.directoryPane.directoriesGrid.adapter = null
            getDirectories()
        }

        if (mStoredTextColor != getProperTextColor()) {
            getRecyclerAdapter()?.updateTextColor(getProperTextColor())
        }

        val primaryColor = getProperPrimaryColor()
        if (mStoredPrimaryColor != primaryColor) {
            getRecyclerAdapter()?.updatePrimaryColor()
        }

        val styleString =
            "${config.folderStyle}${config.showFolderMediaCount}${config.limitFolderTitle}"
        if (mStoredStyleString != styleString) {
            setupAdapter(mDirsIgnoringSearch, forceRecreate = true)
        }

        binding.directoryPane.directoriesFastscroller.updateColors(primaryColor)
        binding.directoryPane.directoriesRefreshLayout.isEnabled = config.enablePullToRefresh
        getRecyclerAdapter()?.apply {
            dateFormat = config.dateFormat
            timeFormat = getTimeFormat()
        }

        binding.directoryPane.directoriesEmptyPlaceholder.setTextColor(getProperTextColor())
        binding.directoryPane.directoriesEmptyPlaceholder2.setTextColor(primaryColor)
        binding.directoryPane.directoriesSwitchSearching.setTextColor(primaryColor)
        binding.directoryPane.directoriesSwitchSearching.underlineText()
        binding.directoryPane.directoriesEmptyPlaceholder2.bringToFront()

        if (!binding.mainMenu.isSearchOpen) {
            refreshMenuItems()
            tryLoadGallery()
        }

        updateTopBarForGroup()
    }

    /**
     * The search pill is the only chrome these screens have, so it doubles as where the grid says
     * it is: while a folder group is open it carries the group's name instead of the usual prompt,
     * and its magnifier becomes the way back out. Both are the pill's own hooks - it keeps drawing
     * its icon, including through a search opening and closing over the top.
     */
    private fun updateTopBarForGroup() {
        // the bar is worn by whichever grid is up, and this one is not always it - opening the app
        // straight into Pictures runs this screen's own startup with the other pane already showing,
        // and a swap in flight has not reached the frame where the bar changes over
        if (activePane === this && !mIsSwapping) {
            dressTopBar(binding.mainMenu)
        }

        onPaneStateChanged()
    }

    override fun onPause() {
        super.onPause()
        activePane.onDeactivated()
    }

    override fun onDeactivated() {
        binding.directoryPane.directoriesRefreshLayout.isRefreshing = false
        mIsGettingDirs = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)
    }

    override fun onStop() {
        super.onStop()

        if (config.temporarilyShowHidden || config.tempSkipDeleteConfirmation || config.temporarilyShowExcluded) {
            mTempShowHiddenHandler.postDelayed({
                config.temporarilyShowHidden = false
                config.temporarilyShowExcluded = false
                config.tempSkipDeleteConfirmation = false
                config.tempSkipRecycleBin = false
            }, SHOW_TEMP_HIDDEN_DURATION)
        } else {
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            config.temporarilyShowHidden = false
            config.temporarilyShowExcluded = false
            config.tempSkipDeleteConfirmation = false
            config.tempSkipRecycleBin = false
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
            removeTempFolder()
            unregisterFileUpdateListener()

            if (!config.showAll) {
                mLastMediaFetcher?.shouldStop = true
                GalleryDatabase.destroyInstance()
            }
        }
    }

    /**
     * Whichever grid is up gets first refusal, and what is left is leaving: the two are peers of one
     * screen rather than one stacked on the other, so Back out of Pictures is Back out of the app.
     */
    override fun onBackPressedCompat(): Boolean {
        if (activePane.handleBack()) {
            return true
        }

        appLockManager.lock()
        return false
    }

    override fun handleBack(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            true
        } else if (mCurrentGroupId != 0L) {
            closeFolderGroup()
            true
        } else if (config.groupDirectSubfolders && mCurrentPathPrefix.isNotEmpty()) {
            mOpenedSubfolders.removeAt(mOpenedSubfolders.lastIndex)
            mCurrentPathPrefix = mOpenedSubfolders.last()
            setupAdapter(mDirs)
            true
        } else {
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_MEDIA && resultData != null) {
                val resultIntent = Intent()
                var resultUri: Uri? = null
                if (mIsThirdPartyIntent) {
                    when {
                        intent.extras?.containsKey(MediaStore.EXTRA_OUTPUT) == true
                                && intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 -> {
                            resultUri = fillExtraOutput(resultData)
                        }

                        resultData.extras?.containsKey(PICKED_PATHS) == true -> {
                            fillPickedPaths(resultData, resultIntent)
                        }

                        else -> fillIntentPath(resultData, resultIntent)
                    }
                }

                if (resultUri != null) {
                    resultIntent.data = resultUri
                    resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                setResult(RESULT_OK, resultIntent)
                finish()
            } else if (requestCode == PICK_WALLPAPER) {
                setResult(RESULT_OK)
                finish()
            }
        }

        // its own codes, none of which this screen uses for anything of its own
        mMediaPane?.onActivityResult(requestCode, resultCode, resultData)
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    /** Asks the screen for the toolbar's entries again, which comes straight back below. */
    private fun refreshMenuItems() {
        chrome.refreshMenuItems()
    }

    override fun refreshMenuItems(menu: Menu) {
        if (!mIsThirdPartyIntent) {
            menu.apply {
                findItem(R.id.column_count).isVisible = config.viewTypeFolders == VIEW_TYPE_GRID
                findItem(R.id.open_recycle_bin).isVisible =
                    config.useRecycleBin && !config.showRecycleBinAtFolders
                findItem(R.id.more_apps_from_us).isVisible =
                    !resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)
            }
        }

        menu.apply {
            findItem(R.id.temporarily_show_hidden).isVisible = !config.shouldShowHidden
            findItem(R.id.stop_showing_hidden).isVisible =
                (!isRPlus() || isExternalStorageManager()) && config.temporarilyShowHidden

            findItem(R.id.temporarily_show_excluded).isVisible = !config.temporarilyShowExcluded
            findItem(R.id.stop_showing_excluded).isVisible = config.temporarilyShowExcluded
        }
    }

    override val root: View get() = binding.directoryPane.root
    override val grid get() = binding.directoryPane.directoriesGrid
    override val refreshLayout get() = binding.directoryPane.directoriesRefreshLayout
    override val menuSpec = FOLDER_GRID_MENU
    override val menuRes
        get() = if (mIsThirdPartyIntent) R.menu.menu_main_intent else R.menu.menu_main

    override fun dressTopBar(topBar: MySearchMenu) {
        // only ever reached while a folder group is open - that is when the pill wears the arrow
        topBar.onNavigateBackClickListener = { closeFolderGroup() }
        val openGroup = folderGroups().firstOrNull { it.id == mCurrentGroupId }
        topBar.updateHintText(
            when {
                openGroup != null -> openGroup.name
                config.searchAllFilesByDefault -> getString(org.fossify.commons.R.string.search_files)
                else -> getString(org.fossify.commons.R.string.search_folders)
            }
        )

        topBar.toggleForceArrowBackIcon(openGroup != null)
    }

    override fun onSearchToggled(isOpen: Boolean) {
        onPaneStateChanged()
        if (isOpen && config.searchAllFilesByDefault) {
            launchSearchActivity()
        }
    }

    override fun onSearchTextChanged(text: String) {
        setupAdapter(mDirsIgnoringSearch, text)
        binding.directoryPane.directoriesRefreshLayout.isEnabled =
            text.isEmpty() && config.enablePullToRefresh
        binding.directoryPane.directoriesSwitchSearching.beVisibleIf(text.isNotEmpty())
        chrome.floatingTopBar.keepGridClear()
    }

    // the switch-to-file-search link keeps clear of the bar itself and the list is laid out below
    // the link, so while it is up the list has no room left to make for itself
    override fun gridNeedsTopRoom() = !binding.directoryPane.directoriesSwitchSearching.isVisible()

    override fun onMenuItemClick(itemId: Int): Boolean {
        when (itemId) {
            R.id.sort -> showSortingDialog()
            R.id.filter -> showFilterMediaDialog()
            R.id.open_camera -> launchCamera()
            R.id.change_view_type -> changeViewType()
            R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
            R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
            R.id.temporarily_show_excluded -> tryToggleTemporarilyShowExcluded()
            R.id.stop_showing_excluded -> tryToggleTemporarilyShowExcluded()
            R.id.create_new_folder -> createNewFolder()
            R.id.open_recycle_bin -> openRecycleBin()
            R.id.column_count -> changeColumnCount()
            R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return false
        }

        return true
    }

    // repainted on every resume rather than set once: the fades are drawn in the theme's own
    // background colour, and the theme can change while this screen is in the back stack
    private fun updateEdgeFades() {
        binding.directoryPane.directoriesTopFade.applyEdgeFade(atTop = true)
        binding.directoryPane.directoriesBottomFade.applyEdgeFade(atTop = false)
    }

    // ---------------------------------------------------------- the media pane's host ----

    override val topBar: MySearchMenu get() = binding.mainMenu

    override fun refreshMenu() = chrome.refreshMenuItems()

    override fun applyInsets() = setupInsetPadding()

    // the all media grid is a top level screen: it never wears the arrow, so this is never asked for
    override fun navigateUp() = Unit

    /**
     * Sideways scrolling has no room to pan the chrome out of, and while an arrangement is being
     * made the bar is the way out of that mode. The pill is the way between the two grids, so it has
     * nothing to offer anywhere that is not one of them: a folder group stepped into, a search
     * narrowing a grid, an arrangement or a selection it would silently drop, or somebody else's app
     * asking us to pick a picture.
     */
    override fun onPaneStateChanged() {
        val media = mMediaPane
        chrome.floatingTopBar.isPanningEnabled =
            !config.scrollHorizontally && media?.isReordering != true
        if (mIsThirdPartyIntent) {
            return
        }

        navPill.isPanningEnabled = !config.scrollHorizontally
        navPill.isAvailable = mCurrentGroupId == 0L &&
                !binding.mainMenu.isSearchOpen &&
                !mIsSelecting &&
                media?.isReordering != true &&
                media?.isSelecting != true
    }

    // ----------------------------------------------------------------- the two panes ----

    /** The bar, and the pill wherever there are two grids for it to move between. */
    private fun setupChrome() {
        chrome = GridChrome(
            topBar = binding.mainMenu,
            contentBehind = binding.contentHolder,
            navPill = if (mIsThirdPartyIntent) null else navPill
        )

        chrome.attach(this)
        if (!mIsThirdPartyIntent) {
            setupNavPill()
            prewarmMediaPane()
        }
    }

    private fun setupNavPill() {
        navPill.setup(binding.contentHolder, NavDestination.ALBUMS)
        navPill.onDestination = ::swapTo
    }

    /**
     * Keeps the grids clear of the navigation bar - except while the reorder bar is up, where the
     * bar sits between the two and does that job itself, and asking for the room twice would only
     * open an empty band above it.
     */
    private fun setupInsetPadding() {
        val reorderBar = binding.mediaPane.mediaReorderBar.root
        val clearOfTheBottom = if (mMediaPane?.isReordering == true) {
            listOf(binding.directoryPane.directoriesGrid, reorderBar)
        } else {
            listOf(binding.directoryPane.directoriesGrid, binding.mediaPane.mediaGrid, reorderBar)
        }

        setupEdgeToEdge(
            // the grids get no top inset of their own - keepGridClear() pads whichever is up by the
            // whole height of the bar, which already carries this inset
            padTopSystem = listOf(
                binding.mainMenu,
                binding.directoryPane.directoriesSwitchSearching,
                binding.directoryPane.directoriesEmptyPlaceholder,
                binding.mediaPane.mediaEmptyTextPlaceholder
            ),
            padBottomImeAndSystem = clearOfTheBottom,
            padBottomSystem = listOf(binding.navPill.root)
        )
    }

    private fun mediaPane(): MediaGridPane {
        mMediaPane?.let { return it }
        return MediaGridPane(
            activity = this,
            binding = binding.mediaPane,
            host = this,
            mPath = "",
            showAll = true
        ).also { mMediaPane = it }
    }

    /**
     * Builds the other grid while nothing is asking for it, so the first tap on the pill is not the
     * one that pays for it. Its media waits until it is actually brought up.
     */
    private fun prewarmMediaPane() {
        Looper.myQueue().addIdleHandler {
            if (!isDestroyed && !isFinishing) {
                mediaPane()
            }

            false
        }
    }

    /**
     * The swap the pill asks for. Both grids are children of the one holder, so neither the pill nor
     * the search bar is part of what moves - the two panes are, one out and one in.
     */
    private fun swapTo(destination: NavDestination) {
        val toPictures = destination == NavDestination.PICTURES
        val incoming: GridPane = if (toPictures) mediaPane() else this
        if (mIsSwapping || incoming === activePane) {
            return
        }

        hideKeyboard()
        // held from here rather than from the slide below, so the activation in between knows the
        // bar is not this pane's to dress yet - the hand-over is halfway through the slide
        mIsSwapping = true
        config.showAll = toPictures
        // leaving the all media view for good, so a startup setting still pointing at it would drop
        // the user straight back in on the next launch with no way out of the loop
        if (!toPictures && config.defaultFolder == SHOW_ALL) {
            config.defaultFolder = ""
        }

        // the plate moves the moment it is tapped, whatever the grids are still doing about it
        navPill.selected = destination
        val outgoing = activePane
        activePane = incoming
        incoming.onActivated()
        slidePanes(outgoing, incoming, fromLeft = toPictures)
    }

    private fun slidePanes(outgoing: GridPane, incoming: GridPane, fromLeft: Boolean) {
        val travel = binding.contentHolder.width.toFloat()
        val duration = resources.getInteger(R.integer.nav_swap_duration).toLong()
        val curve = AnimationUtils.loadInterpolator(this, android.R.interpolator.fast_out_slow_in)

        incoming.root.translationX = if (fromLeft) -travel else travel
        incoming.root.beVisible()
        var handedOver = false
        incoming.root.animate()
            .translationX(0f)
            .setDuration(duration)
            .setInterpolator(curve)
            // the bar carries no part of the movement: its buttons change over in one frame, at the
            // halfway mark. Taken from the animation rather than timed alongside it, so it stays the
            // halfway mark under whatever animation scale the device is set to
            .setUpdateListener {
                if (!handedOver && it.animatedFraction >= HALFWAY) {
                    handedOver = true
                    chrome.bind(activePane)
                }
            }
            .start()

        outgoing.root.animate()
            .translationX(if (fromLeft) travel else -travel)
            .setDuration(duration)
            .setInterpolator(curve)
            .withEndAction {
                outgoing.root.beGone()
                outgoing.root.translationX = 0f
                outgoing.onDeactivated()
                mIsSwapping = false
            }
            .start()
    }

    private fun getRecyclerAdapter() = binding.directoryPane.directoriesGrid.adapter as? DirectoryAdapter

    private fun storeStateVariables() {
        mStoredTextColor = getProperTextColor()
        mStoredPrimaryColor = getProperPrimaryColor()
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredStyleString = "$folderStyle$showFolderMediaCount$limitFolderTitle"
        }
    }

    private fun startNewPhotoFetcher() {
        val photoFetcher = NewPhotoFetcher()
        if (!photoFetcher.isScheduled(applicationContext)) {
            photoFetcher.scheduleJob(applicationContext)
        }
    }

    private fun removeTempFolder() {
        if (config.tempFolderPath.isNotEmpty()) {
            val newFolder = File(config.tempFolderPath)
            if (getDoesFilePathExist(newFolder.absolutePath) && newFolder.isDirectory) {
                if (
                    newFolder.getProperSize(true) == 0L
                    && newFolder.getFileCount(true) == 0
                    && newFolder.list()?.isEmpty() == true
                ) {
                    toast(
                        String.format(
                            getString(org.fossify.commons.R.string.deleting_folder),
                            config.tempFolderPath
                        ), Toast.LENGTH_LONG
                    )
                    tryDeleteFileDirItem(newFolder.toFileDirItem(applicationContext), true, true)
                }
            }
            config.tempFolderPath = ""
        }
    }

    private fun checkOTGPath() {
        ensureBackgroundThread {
            if (!config.wasOTGHandled && hasPermission(getPermissionToRequest()) && hasOTGConnected() && config.OTGPath.isEmpty()) {
                getStorageDirectories().firstOrNull {
                    it.trimEnd('/') != internalStoragePath
                            && it.trimEnd('/') != sdCardPath
                }?.apply {
                    config.wasOTGHandled = true
                    val otgPath = trimEnd('/')
                    config.OTGPath = otgPath
                    config.addIncludedFolder(otgPath)
                }
            }
        }
    }

    private fun checkDefaultSpamFolders() {
        if (!config.spamFoldersChecked) {
            val spamFolders = arrayListOf(
                "/storage/emulated/0/Android/data/com.facebook.orca/files/stickers"
            )

            val OTGPath = config.OTGPath
            spamFolders.forEach {
                if (getDoesFilePathExist(it, OTGPath)) {
                    config.addExcludedFolder(it)
                }
            }
            config.spamFoldersChecked = true
        }
    }

    private fun tryLoadGallery() {
        // avoid calling anything right after granting the permission, it will be called from onResume()
        val wasMissingPermission =
            config.appRunCount == 1 && !hasAllPermissions(getPermissionsToRequest())
        handleMediaPermissions {
            if (wasMissingPermission) {
                return@handleMediaPermissions
            }

            var openedStartupScreen = false
            if (!mWasDefaultFolderChecked) {
                openedStartupScreen = openDefaultFolder()
                mWasDefaultFolderChecked = true
            }

            checkOTGPath()
            checkDefaultSpamFolders()

            // the folders are still fetched behind a startup screen that was opened over this one,
            // so the grid is ready for whenever it is come back to
            if (config.showAll && !openedStartupScreen) {
                showAllMedia()
            } else {
                getDirectories()
            }

            setupLayoutManager()
        }
    }

    private fun getDirectories() {
        if (mIsGettingDirs) {
            return
        }

        mShouldStopFetching = true
        mIsGettingDirs = true
        val getImages = mIsPickImageIntent || mIsGetImageContentIntent
        val getVideos = mIsPickVideoIntent || mIsGetVideoContentIntent

        getCachedDirectories(getVideos && !getImages, getImages && !getVideos) {
            gotDirectories(addTempFolderIfNeeded(it))
        }
    }

    private fun launchSearchActivity() {
        hideKeyboard()
        Intent(this, SearchActivity::class.java).apply {
            startActivity(this)
        }

        binding.mainMenu.postDelayed({
            binding.mainMenu.closeSearch()
        }, 500)
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, isDirectorySorting = true) {
            binding.directoryPane.directoriesGrid.adapter = null
            if (config.directorySorting and SORT_BY_DATE_MODIFIED != 0 || config.directorySorting and SORT_BY_DATE_TAKEN != 0) {
                getDirectories()
            } else {
                ensureBackgroundThread {
                    gotDirectories(getCurrentlyDisplayedDirs())
                }
            }

            getRecyclerAdapter()?.directorySorting = config.directorySorting
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            mShouldStopFetching = true
            binding.directoryPane.directoriesRefreshLayout.isRefreshing = true
            binding.directoryPane.directoriesGrid.adapter = null
            getDirectories()
        }
    }

    /**
     * Opens straight into the all media grid, with no swap to play: this runs before there is
     * anything on screen to swap away from. A picker is handed the grid as a screen of its own,
     * having no pill to come back with.
     */
    private fun showAllMedia() {
        config.showAll = true
        if (mIsThirdPartyIntent) {
            Intent(this, MediaActivity::class.java).apply {
                putExtra(DIRECTORY, "")
                handleMediaIntent(this)
            }

            return
        }

        val pane = mediaPane()
        if (activePane === pane) {
            return
        }

        navPill.selected = NavDestination.PICTURES
        activePane = pane
        binding.directoryPane.root.beGone()
        pane.root.beVisible()
        chrome.bind(pane)
        pane.onActivated()
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this, true) {
            refreshMenuItems()
            setupLayoutManager()
            binding.directoryPane.directoriesGrid.adapter = null
            setupAdapter(mDirsIgnoringSearch)
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
        binding.directoryPane.directoriesGrid.adapter = null
        getDirectories()
        refreshMenuItems()
    }

    private fun tryToggleTemporarilyShowExcluded() {
        if (config.temporarilyShowExcluded) {
            toggleTemporarilyShowExcluded(false)
        } else {
            handleExcludedFolderPasswordProtection {
                toggleTemporarilyShowExcluded(true)
            }
        }
    }

    private fun toggleTemporarilyShowExcluded(show: Boolean) {
        mLoadedInitialPhotos = false
        config.temporarilyShowExcluded = show
        binding.directoryPane.directoriesGrid.adapter = null
        getDirectories()
        refreshMenuItems()
    }

    override fun deleteFolders(folders: ArrayList<File>) {
        val fileDirItems = folders
            .asSequence()
            .filter { it.isDirectory }
            .map { FileDirItem(it.absolutePath, it.name, true) }
            .toMutableList() as ArrayList<FileDirItem>

        when {
            fileDirItems.isEmpty() -> return
            fileDirItems.size == 1 -> {
                try {
                    toast(
                        String.format(
                            getString(org.fossify.commons.R.string.deleting_folder),
                            fileDirItems.first().name
                        )
                    )
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }

            else -> {
                val baseString = if (config.useRecycleBin && !config.tempSkipRecycleBin) {
                    org.fossify.commons.R.plurals.moving_items_into_bin
                } else {
                    org.fossify.commons.R.plurals.delete_items
                }

                toast(
                    msg = resources.getQuantityString(
                        baseString, fileDirItems.size, fileDirItems.size
                    )
                )
            }
        }

        val itemsToDelete = ArrayList<FileDirItem>()
        val filter = config.filterMedia
        val showHidden = config.shouldShowHidden
        fileDirItems.filter { it.isDirectory }.forEach {
            val files = File(it.path).listFiles()
            files?.filter {
                it.absolutePath.isMediaFile()
                        && (showHidden || !it.name.startsWith('.'))
                        && ((it.isImageFast() && filter and TYPE_IMAGES != 0)
                        || (it.isVideoFast() && filter and TYPE_VIDEOS != 0)
                        || (it.isGif() && filter and TYPE_GIFS != 0)
                        || (it.isRawFast() && filter and TYPE_RAWS != 0)
                        || (it.isSvg() && filter and TYPE_SVGS != 0))
            }?.mapTo(itemsToDelete) { it.toFileDirItem(applicationContext) }
        }

        if (config.useRecycleBin && !config.tempSkipRecycleBin) {
            val pathsToDelete = ArrayList<String>()
            itemsToDelete.mapTo(pathsToDelete) { it.path }

            movePathsInRecycleBin(pathsToDelete) {
                if (it) {
                    deleteFilteredFileDirItems(itemsToDelete, folders)
                } else {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
            }
        } else {
            deleteFilteredFileDirItems(itemsToDelete, folders)
        }
    }

    private fun deleteFilteredFileDirItems(
        fileDirItems: ArrayList<FileDirItem>,
        folders: ArrayList<File>
    ) {
        val OTGPath = config.OTGPath
        deleteFiles(fileDirItems) {
            runOnUiThread {
                refreshItems()
            }

            ensureBackgroundThread {
                folders.filter { !getDoesFilePathExist(it.absolutePath, OTGPath) }.forEach {
                    directoryDB.deleteDirPath(it.absolutePath)
                }

                if (config.deleteEmptyFolders) {
                    folders.filter {
                        !it.absolutePath.isDownloadsFolder()
                                && it.isDirectory
                                && it.toFileDirItem(this).getProperFileCount(this, true) == 0
                    }
                        .forEach {
                            tryDeleteFileDirItem(it.toFileDirItem(this), true, true)
                        }
                }
            }
        }
    }

    private fun setupLayoutManager() {
        mPinchZoom.isEnabled = config.viewTypeFolders == VIEW_TYPE_GRID
        if (config.viewTypeFolders == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }

        (binding.directoryPane.directoriesRefreshLayout.layoutParams as RelativeLayout.LayoutParams)
            .addRule(RelativeLayout.BELOW, R.id.directories_switch_searching)
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.directoryPane.directoriesGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            binding.directoryPane.directoriesRefreshLayout.layoutParams =
                RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            binding.directoryPane.directoriesRefreshLayout.layoutParams =
                RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
        }

        layoutManager.spanCount = config.dirColumnCnt
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.directoryPane.directoriesGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
        binding.directoryPane.directoriesRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun createNewFolder() {
        FilePickerDialog(this, internalStoragePath, false, config.shouldShowHidden, false, true) {
            CreateNewFolderDialog(this, it) {
                config.tempFolderPath = it
                ensureBackgroundThread {
                    gotDirectories(addTempFolderIfNeeded(getCurrentlyDisplayedDirs()))
                }
            }
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..MAX_COLUMN_COUNT) {
            items.add(
                RadioItem(
                    id = i,
                    title = resources.getQuantityString(
                        org.fossify.commons.R.plurals.column_counts, i, i
                    )
                )
            )
        }

        val currentColumnCount =
            (binding.directoryPane.directoriesGrid.layoutManager as MyGridLayoutManager).spanCount
        RadioGroupDialog(this, items, currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.dirColumnCnt = newColumnCount
                columnCountChanged()
            }
        }
    }

    private fun increaseColumnCount() {
        config.dirColumnCnt += 1
        columnCountChanged()
    }

    private fun reduceColumnCount() {
        config.dirColumnCnt -= 1
        columnCountChanged()
    }

    private fun columnCountChanged() {
        (binding.directoryPane.directoriesGrid.layoutManager as MyGridLayoutManager).spanCount =
            config.dirColumnCnt
        refreshMenuItems()
        getRecyclerAdapter()?.apply {
            notifyItemRangeChanged(0, dirs.size)
        }
    }

    private fun isPickImageIntent(intent: Intent): Boolean {
        return isPickIntent(intent) && (hasImageContentData(intent) || isImageType(intent))
    }

    private fun isPickVideoIntent(intent: Intent): Boolean {
        return isPickIntent(intent) && (hasVideoContentData(intent) || isVideoType(intent))
    }

    private fun isPickIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_PICK
    }

    private fun isGetContentIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_GET_CONTENT && intent.type != null
    }

    private fun anyExtraMimeTypeStartingWith(intent: Intent, mimeTypePrefix: String): Boolean {
        return intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
            ?.any { it.startsWith(mimeTypePrefix) } == true
    }

    private fun isGetImageContentIntent(intent: Intent): Boolean {
        return isGetContentIntent(intent)
                && (intent.type!!.startsWith("image/")
                || intent.type == Images.Media.CONTENT_TYPE
                || anyExtraMimeTypeStartingWith(intent, "image/"))
    }

    private fun isGetVideoContentIntent(intent: Intent): Boolean {
        return isGetContentIntent(intent)
                && (intent.type!!.startsWith("video/")
                || intent.type == Video.Media.CONTENT_TYPE
                || anyExtraMimeTypeStartingWith(intent, "video/"))
    }

    private fun isGetAnyContentIntent(intent: Intent): Boolean {
        return isGetContentIntent(intent) && intent.type == "*/*"
    }

    private fun isSetWallpaperIntent(intent: Intent?): Boolean {
        return intent?.action == Intent.ACTION_SET_WALLPAPER
    }

    private fun hasImageContentData(intent: Intent): Boolean {
        return intent.data == Images.Media.EXTERNAL_CONTENT_URI
                || intent.data == Images.Media.INTERNAL_CONTENT_URI
    }

    private fun hasVideoContentData(intent: Intent): Boolean {
        return intent.data == Video.Media.EXTERNAL_CONTENT_URI
                || intent.data == Video.Media.INTERNAL_CONTENT_URI
    }

    private fun isImageType(intent: Intent): Boolean {
        return (intent.type?.startsWith("image/") == true
                || intent.type == Images.Media.CONTENT_TYPE)
    }

    private fun isVideoType(intent: Intent): Boolean {
        return (intent.type?.startsWith("video/") == true
                || intent.type == Video.Media.CONTENT_TYPE)
    }

    private fun fillExtraOutput(resultData: Intent): Uri? {
        val file = File(resultData.data!!.path!!)
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            val output = intent.extras!!.get(MediaStore.EXTRA_OUTPUT) as Uri
            inputStream = FileInputStream(file)
            outputStream = contentResolver.openOutputStream(output)
            inputStream.copyTo(outputStream!!)
        } catch (e: SecurityException) {
            showErrorToast(e)
        } catch (ignored: FileNotFoundException) {
            return getFilePublicUri(file, BuildConfig.APPLICATION_ID)
        } finally {
            inputStream?.close()
            outputStream?.close()
        }

        return null
    }

    private fun fillPickedPaths(resultData: Intent, resultIntent: Intent) {
        val paths = resultData.extras!!.getStringArrayList(PICKED_PATHS)
        val uris = paths!!
            .map { getFilePublicUri(File(it), BuildConfig.APPLICATION_ID) } as ArrayList
        val clipData = ClipData(
            "Attachment",
            arrayOf("image/*", "video/*"),
            ClipData.Item(uris.removeAt(0))
        )

        uris.forEach {
            clipData.addItem(ClipData.Item(it))
        }

        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        resultIntent.clipData = clipData
    }

    private fun fillIntentPath(resultData: Intent, resultIntent: Intent) {
        val data = resultData.data
        val path = if (data.toString().startsWith("/")) data.toString() else data!!.path
        val uri = getFilePublicUri(File(path!!), BuildConfig.APPLICATION_ID)
        val type = path.getMimeType()
        resultIntent.setDataAndTypeAndNormalize(uri, type)
        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun itemClicked(path: String) {
        handleLockedFolderOpening(path) { success ->
            if (success) {
                Intent(this, MediaActivity::class.java).apply {
                    putExtra(SKIP_AUTHENTICATION, true)
                    putExtra(DIRECTORY, path)
                    handleMediaIntent(this)
                }
            }
        }
    }

    private fun handleMediaIntent(intent: Intent) {
        hideKeyboard()
        intent.apply {
            if (mIsSetWallpaperIntent) {
                putExtra(SET_WALLPAPER_INTENT, true)
                startActivityForResult(this, PICK_WALLPAPER)
            } else {
                putExtra(GET_IMAGE_INTENT, mIsPickImageIntent || mIsGetImageContentIntent)
                putExtra(GET_VIDEO_INTENT, mIsPickVideoIntent || mIsGetVideoContentIntent)
                putExtra(GET_ANY_INTENT, mIsGetAnyContentIntent)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, mAllowPickingMultiple)
                startActivityForResult(this, PICK_MEDIA)
            }
        }
    }

    private fun gotDirectories(newDirs: ArrayList<Directory>) {
        mIsGettingDirs = false
        mShouldStopFetching = false

        // if hidden item showing is disabled but all Favorite items are hidden, hide the Favorites folder
        if (!config.shouldShowHidden) {
            val favoritesFolder = newDirs.firstOrNull { it.areFavorites() }
            if (
                favoritesFolder != null
                && favoritesFolder.tmb.getFilenameFromPath().startsWith('.')
            ) {
                newDirs.remove(favoritesFolder)
            }
        }

        val dirs = getSortedDirectories(newDirs)
        if (config.groupDirectSubfolders) {
            mDirs = dirs.clone() as ArrayList<Directory>
        }

        var isPlaceholderVisible = dirs.isEmpty()

        runOnUiThread {
            checkPlaceholderVisibility(dirs)
            setupAdapter(dirs.clone() as ArrayList<Directory>)
        }

        // cached folders have been loaded, recheck folders one by one starting with the first displayed
        mLastMediaFetcher?.shouldStop = true
        mLastMediaFetcher = MediaFetcher(applicationContext)
        val getImages = mIsPickImageIntent || mIsGetImageContentIntent
        val getVideos = mIsPickVideoIntent || mIsGetVideoContentIntent
        val getImagesOnly = getImages && !getVideos
        val getVideosOnly = getVideos && !getImages
        val favoritePaths = getFavoritePaths()
        val hiddenString = getString(R.string.hidden)
        val albumCovers = config.parseAlbumCovers()
        val includedFolders = config.includedFolders
        val noMediaFolders = getNoMediaFoldersSync()
        val tempFolderPath = config.tempFolderPath
        val getProperFileSize = config.directorySorting and SORT_BY_SIZE != 0
        val dirPathsToRemove = ArrayList<String>()
        val lastModifieds = mLastMediaFetcher!!.getLastModifieds()
        val dateTakens = mLastMediaFetcher!!.getDateTakens()

        if (
            config.showRecycleBinAtFolders
            && !config.showRecycleBinLast
            && !dirs.map { it.path }.contains(RECYCLE_BIN)
        ) {
            try {
                if (mediaDB.getDeletedMediaCount() > 0) {
                    val recycleBin = Directory().apply {
                        path = RECYCLE_BIN
                        name = getString(org.fossify.commons.R.string.recycle_bin)
                        location = LOCATION_INTERNAL
                    }

                    dirs.add(0, recycleBin)
                }
            } catch (ignored: Exception) {
            }
        }

        if (dirs.map { it.path }.contains(FAVORITES)) {
            if (mediaDB.getFavoritesCount() > 0) {
                val favorites = Directory().apply {
                    path = FAVORITES
                    name = getString(org.fossify.commons.R.string.favorites)
                    location = LOCATION_INTERNAL
                }

                dirs.add(0, favorites)
            }
        }

        // fetch files from MediaStore only, unless the app has the MANAGE_EXTERNAL_STORAGE permission on Android 11+
        val android11Files = mLastMediaFetcher?.getAndroid11FolderMedia(
            isPickImage = getImagesOnly,
            isPickVideo = getVideosOnly,
            favoritePaths = favoritePaths,
            getFavoritePathsOnly = false,
            getProperDateTaken = true,
            dateTakens = dateTakens
        )
        try {
            for (directory in dirs) {
                if (mShouldStopFetching || isDestroyed || isFinishing) {
                    return
                }

                val sorting = config.getFolderSorting(directory.path)
                val grouping = config.getFolderGrouping(directory.path)
                val getProperDateTaken = config.directorySorting and SORT_BY_DATE_TAKEN != 0
                        || sorting and SORT_BY_DATE_TAKEN != 0
                        || grouping and GROUP_BY_DATE_TAKEN_DAILY != 0
                        || grouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0

                val getProperLastModified =
                    config.directorySorting and SORT_BY_DATE_MODIFIED != 0
                            || sorting and SORT_BY_DATE_MODIFIED != 0
                            || grouping and GROUP_BY_LAST_MODIFIED_DAILY != 0
                            || grouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

                val curMedia = mLastMediaFetcher!!.getFilesFrom(
                    curPath = directory.path,
                    isPickImage = getImagesOnly,
                    isPickVideo = getVideosOnly,
                    getProperDateTaken = getProperDateTaken,
                    getProperLastModified = getProperLastModified,
                    getProperFileSize = getProperFileSize,
                    favoritePaths = favoritePaths,
                    getVideoDurations = false,
                    lastModifieds = lastModifieds,
                    dateTakens = dateTakens,
                    android11Files = android11Files
                )

                val newDir = if (curMedia.isEmpty()) {
                    if (directory.path != tempFolderPath) {
                        dirPathsToRemove.add(directory.path)
                    }
                    directory
                } else {
                    createDirectoryFromMedia(
                        path = directory.path,
                        curMedia = curMedia,
                        albumCovers = albumCovers,
                        hiddenString = hiddenString,
                        includedFolders = includedFolders,
                        getProperFileSize = getProperFileSize,
                        noMediaFolders = noMediaFolders
                    )
                }

                // we are looping through the already displayed folders looking for changes, do not do anything if nothing changed
                if (directory.copy(subfoldersCount = 0, subfoldersMediaCount = 0) == newDir) {
                    continue
                }

                directory.apply {
                    tmb = newDir.tmb
                    name = newDir.name
                    mediaCnt = newDir.mediaCnt
                    modified = newDir.modified
                    taken = newDir.taken
                    this@apply.size = newDir.size
                    types = newDir.types
                    sortValue = getDirectorySortingValue(curMedia, path, name, size, mediaCnt)
                }

                setupAdapterThrottled(dirs)

                // update directories and media files in the local db, delete invalid items. Intentionally creating a new thread
                updateDBDirectory(directory)
                if (!directory.isRecycleBin() && !directory.areFavorites()) {
                    Thread {
                        try {
                            mediaDB.insertAll(curMedia)
                        } catch (ignored: Exception) {
                        }
                    }.start()
                }

                if (!directory.isRecycleBin()) {
                    // by path, not by whole item - a scan that only refreshed a file's metadata
                    // would otherwise look like a removal and race the insert above into deleting
                    // the row it had just written. hashed so this stays linear in the folder size
                    val curPaths = curMedia.mapTo(HashSet(curMedia.size)) { it.path }
                    getCachedMedia(directory.path, getVideosOnly, getImagesOnly) {
                        val mediaToDelete = ArrayList<Medium>()
                        it.forEach {
                            val medium = it as? Medium
                            if (medium != null && !curPaths.contains(medium.path)) {
                                mediaToDelete.add(medium)
                            }
                        }
                        mediaDB.deleteMedia(*mediaToDelete.toTypedArray())
                    }
                }
            }

            if (dirPathsToRemove.isNotEmpty()) {
                val dirsToRemove = dirs.filter { dirPathsToRemove.contains(it.path) }
                dirsToRemove.forEach {
                    directoryDB.deleteDirPath(it.path)
                }
                dirs.removeAll(dirsToRemove)
                setupAdapter(dirs)
            }
        } catch (ignored: Exception) {
        }

        val foldersToScan = mLastMediaFetcher!!.getFoldersToScan()
        foldersToScan.remove(FAVORITES)
        foldersToScan.add(0, FAVORITES)
        if (config.showRecycleBinAtFolders) {
            if (foldersToScan.contains(RECYCLE_BIN)) {
                foldersToScan.remove(RECYCLE_BIN)
                foldersToScan.add(0, RECYCLE_BIN)
            } else {
                foldersToScan.add(0, RECYCLE_BIN)
            }
        } else {
            foldersToScan.remove(RECYCLE_BIN)
        }

        dirs.filterNot { it.path == RECYCLE_BIN || it.path == FAVORITES }.forEach {
            foldersToScan.remove(it.path)
        }

        // check the remaining folders which were not cached at all yet
        for (folder in foldersToScan) {
            if (mShouldStopFetching || isDestroyed || isFinishing) {
                return
            }

            val sorting = config.getFolderSorting(folder)
            val grouping = config.getFolderGrouping(folder)
            val getProperDateTaken = config.directorySorting and SORT_BY_DATE_TAKEN != 0
                    || sorting and SORT_BY_DATE_TAKEN != 0
                    || grouping and GROUP_BY_DATE_TAKEN_DAILY != 0
                    || grouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0

            val getProperLastModified = config.directorySorting and SORT_BY_DATE_MODIFIED != 0
                    || sorting and SORT_BY_DATE_MODIFIED != 0
                    || grouping and GROUP_BY_LAST_MODIFIED_DAILY != 0
                    || grouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

            val newMedia = mLastMediaFetcher!!.getFilesFrom(
                curPath = folder,
                isPickImage = getImagesOnly,
                isPickVideo = getVideosOnly,
                getProperDateTaken = getProperDateTaken,
                getProperLastModified = getProperLastModified,
                getProperFileSize = getProperFileSize,
                favoritePaths = favoritePaths,
                getVideoDurations = false,
                lastModifieds = lastModifieds,
                dateTakens = dateTakens,
                android11Files = android11Files
            )

            if (newMedia.isEmpty()) {
                continue
            }

            if (isPlaceholderVisible) {
                isPlaceholderVisible = false
                runOnUiThread {
                    binding.directoryPane.directoriesEmptyPlaceholder.beGone()
                    binding.directoryPane.directoriesEmptyPlaceholder2.beGone()
                    binding.directoryPane.directoriesFastscroller.beVisible()
                }
            }

            val newDir = createDirectoryFromMedia(
                path = folder,
                curMedia = newMedia,
                albumCovers = albumCovers,
                hiddenString = hiddenString,
                includedFolders = includedFolders,
                getProperFileSize = getProperFileSize,
                noMediaFolders = noMediaFolders
            )
            dirs.add(newDir)
            setupAdapterThrottled(dirs)

            // make sure to create a new thread for these operations, dont just use the common bg thread
            Thread {
                try {
                    directoryDB.insert(newDir)
                    if (folder != RECYCLE_BIN && folder != FAVORITES) {
                        mediaDB.insertAll(newMedia)
                    }
                } catch (ignored: Exception) {
                }
            }.start()
        }

        // the throttle above may be holding the last folders back, so always finish on a full one
        setupAdapter(dirs)

        mLoadedInitialPhotos = true
        if (config.appRunCount > 1) {
            checkLastMediaChanged()
        }

        runOnUiThread {
            binding.directoryPane.directoriesRefreshLayout.isRefreshing = false
            checkPlaceholderVisibility(dirs)
        }

        checkInvalidDirectories(dirs)
        if (mDirs.size > 50) {
            excludeSpamFolders()
        }

        val excludedFolders = config.excludedFolders
        val everShownFolders = config.everShownFolders.toMutableSet() as HashSet<String>

        // do not add excluded folders and their subfolders at everShownFolders
        dirs.filter { dir ->
            return@filter !excludedFolders.any { dir.path.startsWith(it) }
        }.mapTo(everShownFolders) { it.path }

        try {
            // scan the internal storage from time to time for new folders
            if (config.appRunCount == 1 || config.appRunCount % 30 == 0) {
                everShownFolders.addAll(getFoldersWithMedia(config.internalStoragePath))
            }

            // catch some extreme exceptions like too many everShownFolders for storing, shouldnt really happen
            config.everShownFolders = everShownFolders
        } catch (e: Exception) {
            config.everShownFolders = HashSet()
        }

        mDirs = dirs.clone() as ArrayList<Directory>

        // only now that a whole scan has been through every folder is a group's missing member
        // really gone rather than just not reached yet
        if (pruneFolderGroups()) {
            setupAdapter(dirs)
        }
    }

    /**
     * Opens whatever screen the user set the app to start on. A folder group is one of this grid's
     * own states, so it is stepped into rather than launched; everything else is a media screen.
     *
     * Run before the first scan, which is what lets a group be opened without the grid being drawn
     * at the root first: the id is already set by the time the folders arrive.
     *
     * Returns whether a media screen was launched, so the caller does not go on to open a second
     * one over it.
     */
    private fun openDefaultFolder(): Boolean {
        val target = config.defaultFolder
        val groupId = startupGroupId(target)
        when {
            target.isEmpty() -> Unit

            // the folder was deleted or the group dissolved since it was picked
            isStartupTargetGone(target) -> config.defaultFolder = ""

            groupId != 0L -> {
                mCurrentGroupId = groupId
                updateTopBarForGroup()
            }

            // showing every folder's content is a mode the app is in rather than a folder it opens,
            // so turning it on is all there is to do - the caller opens it from there
            target == SHOW_ALL -> config.showAll = true

            else -> {
                Intent(this, MediaActivity::class.java).apply {
                    putExtra(DIRECTORY, target)
                    handleMediaIntent(this)
                }

                return true
            }
        }

        return false
    }

    private fun checkPlaceholderVisibility(dirs: ArrayList<Directory>) {
        binding.directoryPane.directoriesEmptyPlaceholder.beVisibleIf(dirs.isEmpty() && mLoadedInitialPhotos)
        binding.directoryPane.directoriesEmptyPlaceholder2.beVisibleIf(dirs.isEmpty() && mLoadedInitialPhotos)

        if (binding.mainMenu.isSearchOpen) {
            binding.directoryPane.directoriesEmptyPlaceholder.text =
                getString(org.fossify.commons.R.string.no_items_found)
            binding.directoryPane.directoriesEmptyPlaceholder2.beGone()
        } else if (dirs.isEmpty() && config.filterMedia == getDefaultFileFilter()) {
            if (isRPlus() && !isExternalStorageManager()) {
                binding.directoryPane.directoriesEmptyPlaceholder.text =
                    getString(org.fossify.commons.R.string.no_items_found)
                binding.directoryPane.directoriesEmptyPlaceholder2.beGone()
            } else {
                binding.directoryPane.directoriesEmptyPlaceholder.text = getString(R.string.no_media_add_included)
                binding.directoryPane.directoriesEmptyPlaceholder2.text = getString(R.string.add_folder)
            }

            binding.directoryPane.directoriesEmptyPlaceholder2.setOnClickListener {
                showAddIncludedFolderDialog {
                    refreshItems()
                }
            }
        } else {
            binding.directoryPane.directoriesEmptyPlaceholder.text = getString(R.string.no_media_with_filters)
            binding.directoryPane.directoriesEmptyPlaceholder2.text =
                getString(R.string.change_filters_underlined)

            binding.directoryPane.directoriesEmptyPlaceholder2.setOnClickListener {
                showFilterMediaDialog()
            }
        }

        binding.directoryPane.directoriesEmptyPlaceholder2.underlineText()
        binding.directoryPane.directoriesFastscroller
            .beVisibleIf(binding.directoryPane.directoriesEmptyPlaceholder.isGone())
    }

    /**
     * Pushes the grid mid scan, at most [ADAPTER_REFRESH_INTERVAL] apart. The scan revisits folders
     * one at a time and used to hand the whole list over after every single one - each handover
     * being a notifyDataSetChanged() that rebinds every visible cover and restarts its image
     * request, so on a large library the covers flickered through dozens of reloads on the way in.
     * Callers must still finish with a plain [setupAdapter] so nothing held back gets lost.
     */
    private fun setupAdapterThrottled(dirs: ArrayList<Directory>) {
        val now = System.currentTimeMillis()
        if (now - mLastAdapterRefresh < ADAPTER_REFRESH_INTERVAL) {
            return
        }

        mLastAdapterRefresh = now
        setupAdapter(dirs)
    }

    private fun setupAdapter(
        dirs: ArrayList<Directory>,
        textToSearch: String = binding.mainMenu.getCurrentQuery(),
        forceRecreate: Boolean = false
    ) {
        val distinctDirs = dirs
            .distinctBy { it.path.getDistinctPath() }
            .toMutableList() as ArrayList<Directory>

        // every caller hands the whole folder list in, so this is what a search or an open group
        // narrows down from - and what they are put back to when they are done
        mDirsIgnoringSearch = distinctDirs

        val sortedDirs = getSortedDirectories(distinctDirs)
        val rootDirs = getDirsToShow(
            dirs = sortedDirs,
            allDirs = mDirs,
            currentPathPrefix = mCurrentPathPrefix
        ).clone() as ArrayList<Directory>

        // "Group direct subfolders" shows parent folders standing for their children, and a group
        // of exact paths has nothing to say about them - the two do not mix
        val foldersAreGrouped = !config.groupDirectSubfolders
        val groupedDirs = if (foldersAreGrouped) applyFolderGroups(rootDirs) else rootDirs

        runOnUiThread {
            if (!foldersAreGrouped && mCurrentGroupId != 0L) {
                // the setting was turned on while a group was open, and there is no longer a tile
                // for the grid to be standing in
                leaveOpenGroup()
            }

            // both narrowings are read and applied here, in the one main thread pass: worked out
            // on the scan thread they land a group or a keystroke late, and the grid ends up
            // showing one state while the screen believes another
            val dirsToShow = searchDirs(narrowToOpenGroup(groupedDirs), textToSearch)
            checkPlaceholderVisibility(dirsToShow)
            if (binding.directoryPane.directoriesGrid.adapter == null || forceRecreate) {
                createDirectoryAdapter(dirsToShow, textToSearch)
            } else {
                getRecyclerAdapter()?.apply {
                    openGroupId = mCurrentGroupId
                    isSearchActive = textToSearch.isNotEmpty()
                    updateDirs(dirsToShow)
                }
            }
        }

        // recyclerview sometimes becomes empty at init/update, triggering an invisible refresh like this seems to work fine
        binding.directoryPane.directoriesGrid.postDelayed({
            binding.directoryPane.directoriesGrid.scrollBy(0, 0)
        }, 500)
    }

    private fun createDirectoryAdapter(dirsToShow: ArrayList<Directory>, textToSearch: String) {
        val adapter = DirectoryAdapter(
            activity = this,
            dirs = dirsToShow,
            listener = this,
            recyclerView = binding.directoryPane.directoriesGrid,
            isPickIntent = isPickIntent(intent) || isGetAnyContentIntent(intent),
            swipeRefreshLayout = binding.directoryPane.directoriesRefreshLayout,
            openGroupId = mCurrentGroupId
        ) {
            val clickedDir = it as Directory
            val path = clickedDir.path
            if (clickedDir.isFolderGroup()) {
                openFolderGroup(clickedDir.folderGroupId())
            } else if (clickedDir.subfoldersCount == 1 || !config.groupDirectSubfolders) {
                if (path != config.tempFolderPath) {
                    itemClicked(path)
                }
            } else {
                mCurrentPathPrefix = path
                mOpenedSubfolders.add(path)
                setupAdapter(mDirs, "")
            }
        }

        adapter.isSearchActive = textToSearch.isNotEmpty()
        adapter.onSelectionModeChanged = { selecting ->
            mIsSelecting = selecting
            onPaneStateChanged()
        }
        // no entrance animation here: a layout animation on a RecyclerView binds every child at
        // alpha 0 and walks them in, and the view is recycled often enough that children kept
        // being left at that alpha - blank rows until something scrolled them off and back. See
        // the removed layoutAnimation in the layout
        binding.directoryPane.directoriesGrid.adapter = adapter
        setupScrollDirection()
    }

    /**
     * The open folder group's folders alone, in the order the group holds them, or [dirs] whole
     * while the grid is at the root. Main thread only - this is what keeps the grid and
     * [mCurrentGroupId] telling the same story.
     */
    private fun narrowToOpenGroup(dirs: ArrayList<Directory>): ArrayList<Directory> {
        if (mCurrentGroupId == 0L) {
            return dirs
        }

        val tile = dirs.firstOrNull { it.folderGroupId() == mCurrentGroupId }
        if (tile != null) {
            return sortGroupMembers(tile.groupMembers)
        }

        // no tile for it. either the scan has not reached the group's folders yet, or the group is
        // gone - its last folder taken out of it, or a scan finding them all missing - and then
        // there is nothing left to be standing in
        if (folderGroups().any { it.id == mCurrentGroupId }) {
            return ArrayList()
        }

        leaveOpenGroup()
        return dirs
    }

    /**
     * The open group's folders in the order the grid is to draw them: whatever sorting the user has
     * picked, or the group's own arrangement when that sorting is the custom one. The group's is a
     * hand made order like the root grid's, so the same setting decides which of the two is in
     * force - and it stays the order the tile's collage reads either way.
     */
    private fun sortGroupMembers(members: List<Directory>): ArrayList<Directory> {
        if (config.directorySorting and SORT_BY_CUSTOM != 0) {
            return ArrayList(members)
        }

        return getSortedDirectories(ArrayList(members))
    }

    /**
     * Narrows the grid to what matches [textToSearch]. Group tiles match on their own name, and
     * the folders inside them are offered alongside - a folder put in a group is still findable
     * by the name it has always had.
     */
    private fun searchDirs(dirs: ArrayList<Directory>, textToSearch: String): ArrayList<Directory> {
        if (textToSearch.isEmpty()) {
            return dirs
        }

        val candidates = dirs.flatMap {
            if (it.isFolderGroup()) listOf(it) + it.groupMembers else listOf(it)
        }

        return candidates
            .filter { it.name.contains(textToSearch, true) }
            .sortedBy { !it.name.startsWith(textToSearch, true) }
            .toMutableList() as ArrayList<Directory>
    }

    private fun openFolderGroup(id: Long) {
        // before the group opens, so the search closing does not rebuild the grid on top of it
        binding.mainMenu.closeSearch()
        mCurrentGroupId = id
        updateTopBarForGroup()
        setupAdapter(mDirsIgnoringSearch, "")
        binding.directoryPane.directoriesGrid.scrollToPosition(0)
    }

    private fun closeFolderGroup() {
        val leftGroupId = mCurrentGroupId
        leaveOpenGroup()
        setupAdapter(mDirsIgnoringSearch, "")

        // come back out onto the tile that was opened rather than wherever the group's own
        // scrolling left off
        val position = getRecyclerAdapter()
            ?.dirs
            ?.indexOfFirst { it.folderGroupId() == leftGroupId } ?: -1
        if (position >= 0) {
            binding.directoryPane.directoriesGrid.scrollToPosition(position)
        }
    }

    /** Puts the grid back at the root of the folder list. Redrawing it is the caller's to do. */
    private fun leaveOpenGroup() {
        mCurrentGroupId = 0L
        updateTopBarForGroup()
    }

    private fun setupScrollDirection() {
        val scrollHorizontally =
            config.scrollHorizontally && config.viewTypeFolders == VIEW_TYPE_GRID
        binding.directoryPane.directoriesFastscroller.setScrollVertically(!scrollHorizontally)
    }

    private fun checkInvalidDirectories(dirs: ArrayList<Directory>) {
        val invalidDirs = ArrayList<Directory>()
        val OTGPath = config.OTGPath
        dirs.filter { !it.areFavorites() && !it.isRecycleBin() }.forEach {
            if (!getDoesFilePathExist(it.path, OTGPath)) {
                invalidDirs.add(it)
            } else if (it.path != config.tempFolderPath && (!isRPlus() || isExternalStorageManager())) {
                // avoid calling file.list() or listfiles() on Android 11+, it became way too slow
                val children = if (isPathOnOTG(it.path)) {
                    getOTGFolderChildrenNames(it.path)
                } else {
                    File(it.path).list()?.asList()
                }

                val hasMediaFile = children?.any {
                    it != null && (
                            it.isMediaFile()
                                    || (it.startsWith("img_", true)
                                    && File(it).isDirectory)
                            )
                } == true

                if (!hasMediaFile) {
                    invalidDirs.add(it)
                }
            }
        }

        if (getFavoritePaths().isEmpty()) {
            val favoritesFolder = dirs.firstOrNull { it.areFavorites() }
            if (favoritesFolder != null) {
                invalidDirs.add(favoritesFolder)
            }
        }

        if (config.useRecycleBin) {
            try {
                val binFolder = dirs.firstOrNull { it.path == RECYCLE_BIN }
                if (binFolder != null && mediaDB.getDeletedMedia().isEmpty()) {
                    invalidDirs.add(binFolder)
                }
            } catch (ignored: Exception) {
            }
        }

        if (invalidDirs.isNotEmpty()) {
            dirs.removeAll(invalidDirs)
            setupAdapter(dirs)
            invalidDirs.forEach {
                try {
                    directoryDB.deleteDirPath(it.path)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    /**
     * The folders the scan and the database are to work on, which is not always what the grid is
     * showing. A group tile names nothing on disk, so it is taken apart into its members; and while
     * a group is open, or between an adapter being dropped and the next one being built, the grid
     * holds part of the library or none of it - handing that over reads the rest back as deleted.
     */
    private fun getCurrentlyDisplayedDirs(): ArrayList<Directory> {
        val shownDirs = getRecyclerAdapter()?.dirs
        return if (shownDirs == null || mCurrentGroupId != 0L) {
            ArrayList(mDirsIgnoringSearch)
        } else {
            expandFolderGroups(shownDirs)
        }
    }

    private fun setupLatestMediaId() {
        ensureBackgroundThread {
            if (hasPermission(PERMISSION_READ_STORAGE)) {
                mLatestMediaId = getLatestMediaId()
                mLatestMediaDateId = getLatestMediaByDateId()
            }
        }
    }

    private fun checkLastMediaChanged() {
        if (isDestroyed) {
            return
        }

        mLastMediaHandler.postDelayed({
            ensureBackgroundThread {
                val mediaId = getLatestMediaId()
                val mediaDateId = getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    runOnUiThread {
                        getDirectories()
                    }
                } else {
                    mLastMediaHandler.removeCallbacksAndMessages(null)
                    checkLastMediaChanged()
                }
            }
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun checkRecycleBinItems() {
        if (config.useRecycleBin && config.lastBinCheck < System.currentTimeMillis() - DAY_SECONDS * 1000) {
            config.lastBinCheck = System.currentTimeMillis()
            Handler().postDelayed({
                ensureBackgroundThread {
                    try {
                        val filesToDelete = mediaDB.getOldRecycleBinItems(
                            System.currentTimeMillis() - MONTH_MILLISECONDS
                        )
                        filesToDelete.forEach {
                            if (File(it.path.replaceFirst(RECYCLE_BIN, recycleBinPath)).delete()) {
                                mediaDB.deleteMediumPath(it.path)
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            }, 3000L)
        }
    }

    // exclude probably unwanted folders, for example facebook stickers are split between hundreds of separate folders like
    // /storage/emulated/0/Android/data/com.facebook.orca/files/stickers/175139712676531/209575122566323
    // /storage/emulated/0/Android/data/com.facebook.orca/files/stickers/497837993632037/499671223448714
    private fun excludeSpamFolders() {
        ensureBackgroundThread {
            try {
                val internalPath = internalStoragePath
                val checkedPaths = ArrayList<String>()
                val oftenRepeatedPaths = ArrayList<String>()
                val paths = mDirs
                    .map { it.path.removePrefix(internalPath) }
                    .toMutableList() as ArrayList<String>
                paths.forEach {
                    val parts = it.split("/")
                    var currentString = ""
                    for (i in 0 until parts.size) {
                        currentString += "${parts[i]}/"

                        if (!checkedPaths.contains(currentString)) {
                            val cnt = paths.count { it.startsWith(currentString) }
                            if (cnt > 50 && currentString.startsWith("/Android/data", true)) {
                                oftenRepeatedPaths.add(currentString)
                            }
                        }

                        checkedPaths.add(currentString)
                    }
                }

                val substringToRemove = oftenRepeatedPaths.filter {
                    val path = it
                    it == "/" || oftenRepeatedPaths.any { it != path && it.startsWith(path) }
                }

                oftenRepeatedPaths.removeAll(substringToRemove)
                val OTGPath = config.OTGPath
                oftenRepeatedPaths.forEach {
                    val file = File("$internalPath/$it")
                    if (getDoesFilePathExist(file.absolutePath, OTGPath)) {
                        config.addExcludedFolder(file.absolutePath)
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun getFoldersWithMedia(path: String): HashSet<String> {
        val folders = HashSet<String>()
        try {
            val files = File(path).listFiles()
            if (files != null) {
                files.sortBy { !it.isDirectory }
                for (file in files) {
                    if (file.isDirectory && !file.startsWith("${config.internalStoragePath}/Android")) {
                        folders.addAll(getFoldersWithMedia(file.absolutePath))
                    } else if (file.isFile && file.isMediaFile()) {
                        folders.add(file.parent ?: "")
                        break
                    }
                }
            }
        } catch (ignored: Exception) {
        }

        return folders
    }

    override fun refreshItems() {
        getDirectories()
    }

    override fun recheckPinnedFolders() {
        ensureBackgroundThread {
            gotDirectories(movePinnedDirectoriesToFront(getCurrentlyDisplayedDirs()))
        }
    }

    override fun updateDirectories(directories: ArrayList<Directory>) {
        ensureBackgroundThread {
            // a group tile has a synthetic path, so storing one would put a folder in the database
            // that nothing on disk answers to
            storeDirectoryItems(expandFolderGroups(directories))
            removeInvalidDBDirectories()
        }
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
