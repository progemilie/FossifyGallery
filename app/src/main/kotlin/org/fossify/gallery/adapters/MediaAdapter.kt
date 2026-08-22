package org.fossify.gallery.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.allViews
import androidx.core.view.isVisible
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.dialogs.RenameDialog
import org.fossify.commons.dialogs.RenameItemDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.commons.extensions.getOTGPublicPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.handleDeletePasswordProtection
import org.fossify.commons.extensions.hasOTGConnected
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isAStorageRootFolder
import org.fossify.commons.extensions.isAccessibleWithSAFSdk30
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.commons.extensions.needsStupidWritePermissions
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.helpers.sumByLong
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyRecyclerView
import org.fossify.commons.views.MySquareImageView
import org.fossify.gallery.R
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.databinding.PhotoItemGridBinding
import org.fossify.gallery.databinding.PhotoItemGridSimpleBinding
import org.fossify.gallery.databinding.PhotoItemListBinding
import org.fossify.gallery.databinding.ThumbnailSectionBinding
import org.fossify.gallery.databinding.VideoItemGridBinding
import org.fossify.gallery.databinding.VideoItemListBinding
import org.fossify.gallery.dialogs.DeleteWithRememberDialog
import org.fossify.gallery.dialogs.RateMediumDialog
import org.fossify.gallery.extensions.canBeRated
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.fixDateTaken
import org.fossify.gallery.extensions.getShortcutImage
import org.fossify.gallery.extensions.handleMediaManagementPrompt
import org.fossify.gallery.extensions.launchResizeImageDialog
import org.fossify.gallery.extensions.launchResizeMultipleImagesDialog
import org.fossify.gallery.extensions.loadImage
import org.fossify.gallery.extensions.loadSVG
import org.fossify.gallery.extensions.mediaGridZoom
import org.fossify.gallery.extensions.openEditor
import org.fossify.gallery.extensions.openPath
import org.fossify.gallery.extensions.rescanFolderMedia
import org.fossify.gallery.extensions.restoreRecycleBinPaths
import org.fossify.gallery.extensions.saveMirroredImageToFile
import org.fossify.gallery.extensions.saveRotatedImageToFile
import org.fossify.gallery.extensions.setAs
import org.fossify.gallery.extensions.shareMediaPaths
import org.fossify.gallery.extensions.shareMediumPath
import org.fossify.gallery.extensions.showRestoreConfirmationDialog
import org.fossify.gallery.extensions.toggleFileVisibility
import org.fossify.gallery.extensions.tryCopyMoveFilesTo
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.extensions.updateFavorite
import org.fossify.gallery.extensions.updateFavoritePaths
import org.fossify.gallery.extensions.updateFilesRating
import org.fossify.gallery.helpers.GridZoom
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ROUNDED_CORNERS_BIG
import org.fossify.gallery.helpers.ROUNDED_CORNERS_NONE
import org.fossify.gallery.helpers.ROUNDED_CORNERS_SMALL
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_FAVORITES
import org.fossify.gallery.helpers.SHOW_RECYCLE_BIN
import org.fossify.gallery.helpers.SimpleThumbnailLoader
import org.fossify.gallery.helpers.ThumbnailSizes
import org.fossify.gallery.helpers.TransformedMedia
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.interfaces.MediaOperationsListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection

class MediaAdapter(
    activity: BaseSimpleActivity,
    var media: ArrayList<ThumbnailItem>,
    val listener: MediaOperationsListener?,
    val isAGetIntent: Boolean,
    val allowMultiplePicks: Boolean,
    val path: String,
    recyclerView: MyRecyclerView,
    val swipeRefreshLayout: SwipeRefreshLayout? = null,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick),
    RecyclerViewFastScroller.OnPopupTextUpdate {

    private companion object {
        const val ITEM_SECTION = 0
        const val ITEM_MEDIUM_VIDEO_PORTRAIT = 1
        const val ITEM_MEDIUM_PHOTO = 2
        const val ITEM_MEDIUM_SIMPLE = 3

        /** Rows' worth of items the recycler keeps bound off screen. */
        const val CACHED_ROWS = 2

        /** Rows' worth of each item type the recycler keeps to recycle. */
        const val POOLED_ROWS = 3
    }

    private val config = activity.config
    private val viewType = config.getFolderViewType(if (config.showAll) SHOW_ALL else path)
    private val isListViewType = viewType == VIEW_TYPE_LIST
    private var transformedImagePaths = ArrayList<String>()
    private var currentMediaHash = media.hashCode()
    private var currentTransformGeneration = TransformedMedia.generation
    private val hasOTGConnected = activity.hasOTGConnected()

    /** Where the grid is looking - scrolling, revealing and remembering a place. */
    val gridNavigator = MediaGridNavigator(this)

    /** Drag-to-arrange, which takes over the grid's gestures while it is on. */
    val reorderMode = MediaReorderMode(this)

    /**
     * Whether every item is drawn as its picture and nothing else - see [GridZoom]. Only the media
     * grid turns this on; screens with no pinch of their own stay at interactive counts.
     */
    var isSimplified = false
        private set

    // built on first use, since most grids never zoom out far enough to want one, and dropped
    // whenever something it was prepared with changes
    private var preparedSimpleThumbnails: SimpleThumbnailLoader? = null
    private val simpleThumbnails: SimpleThumbnailLoader
        get() = preparedSimpleThumbnails ?: SimpleThumbnailLoader(
            context = activity,
            cropThumbnails = cropThumbnails,
            size = activity.mediaGridZoom().simpleThumbnailSize
        ).also { preparedSimpleThumbnails = it }

    private var columnCount = config.mediaColumnCnt
    private var scrollHorizontally = config.scrollHorizontally
    private var animateGifs = config.animateGifs
    private var cropThumbnails = config.cropThumbnails
    private var displayFilenames = config.displayFileNames
    private var showFileTypes = config.showThumbnailFileTypes
    private var showRatings = config.showThumbnailRating

    var sorting = config.getFolderSorting(if (config.showAll) SHOW_ALL else path)
    var dateFormat = config.dateFormat
    var timeFormat = activity.getTimeFormat()

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_media

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when {
            viewType == ITEM_SECTION -> ThumbnailSectionBinding.inflate(layoutInflater, parent, false)
            viewType == ITEM_MEDIUM_SIMPLE -> PhotoItemGridSimpleBinding.inflate(layoutInflater, parent, false)
            isListViewType -> {
                if (viewType == ITEM_MEDIUM_PHOTO) {
                    PhotoItemListBinding.inflate(layoutInflater, parent, false)
                } else {
                    VideoItemListBinding.inflate(layoutInflater, parent, false)
                }
            }

            else -> {
                if (viewType == ITEM_MEDIUM_PHOTO) {
                    PhotoItemGridBinding.inflate(layoutInflater, parent, false)
                } else {
                    VideoItemGridBinding.inflate(layoutInflater, parent, false)
                }
            }
        }
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: MyRecyclerViewAdapter.ViewHolder, position: Int) {
        val tmbItem = media.getOrNull(position) ?: return
        // nothing an item normally carries: no click, no long press, no selection, no badges
        if (isSimplified && tmbItem is Medium) {
            setupSimpleThumbnail(holder.itemView, tmbItem)
            bindViewHolder(holder)
            return
        }

        val isReordering = reorderMode.isActive
        val allowLongPress = !isReordering && (!isAGetIntent || allowMultiplePicks) && tmbItem is Medium
        holder.bindView(tmbItem, !isReordering && tmbItem is Medium, allowLongPress) { itemView, adapterPosition ->
            if (tmbItem is Medium) {
                setupThumbnail(itemView, tmbItem)
            } else {
                setupSection(itemView, tmbItem as ThumbnailSection)
            }
        }

        // while reordering the action mode is out of reach and bindView cleared both listeners
        // above, so the two gestures are free to mean something else
        if (isReordering && tmbItem is Medium) {
            reorderMode.bindItemGestures(holder, tmbItem)
        }

        bindViewHolder(holder)
    }

    override fun getItemCount() = media.size

    override fun getItemViewType(position: Int): Int {
        val tmbItem = media[position]
        return when {
            tmbItem is ThumbnailSection -> ITEM_SECTION
            isSimplified -> ITEM_MEDIUM_SIMPLE
            (tmbItem as Medium).isVideo() || tmbItem.isPortrait() -> ITEM_MEDIUM_VIDEO_PORTRAIT
            else -> ITEM_MEDIUM_PHOTO
        }
    }

    override fun prepareActionMode(menu: Menu) {
        val selectedItems = getSelectedItems()
        if (selectedItems.isEmpty()) {
            return
        }

        val isOneItemSelected = isOneItemSelected()
        val selectedPaths = selectedItems.map { it.path } as ArrayList<String>
        val isInRecycleBin = selectedItems.firstOrNull()?.getIsInRecycleBin() == true
        menu.apply {
            findItem(R.id.cab_rename).isVisible = !isInRecycleBin
            findItem(R.id.cab_add_to_favorites).isVisible = !isInRecycleBin
            findItem(R.id.cab_fix_date_taken).isVisible = !isInRecycleBin
            findItem(R.id.cab_move_to).isVisible = !isInRecycleBin
            findItem(R.id.cab_open_with).isVisible = isOneItemSelected
            findItem(R.id.cab_edit).isVisible = isOneItemSelected
            findItem(R.id.cab_set_as).isVisible = isOneItemSelected
            findItem(R.id.cab_resize).isVisible = canResize(selectedItems)
            findItem(R.id.cab_mirror).isVisible = selectedItems.any { it.isImage() }
            findItem(R.id.cab_rate).isVisible = !isInRecycleBin && selectedItems.any { it.path.canBeRated() }
            findItem(R.id.cab_confirm_selection).isVisible = isAGetIntent && allowMultiplePicks && selectedKeys.isNotEmpty()
            findItem(R.id.cab_restore_recycle_bin_files).isVisible = selectedPaths.all { it.startsWith(activity.recycleBinPath) }
            findItem(R.id.cab_create_shortcut).isVisible = isOneItemSelected

            checkHideBtnVisibility(this, selectedItems)
            checkFavoriteBtnVisibility(this, selectedItems)
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_confirm_selection -> confirmSelection()
            R.id.cab_properties -> showProperties()
            R.id.cab_rename -> checkMediaManagementAndRename()
            R.id.cab_edit -> editFile()
            R.id.cab_hide -> toggleFileVisibility(true)
            R.id.cab_unhide -> toggleFileVisibility(false)
            R.id.cab_rate -> rateSelection()
            R.id.cab_add_to_favorites -> toggleFavorites(true)
            R.id.cab_remove_from_favorites -> toggleFavorites(false)
            R.id.cab_restore_recycle_bin_files -> restoreFiles()
            R.id.cab_share -> shareMedia()
            R.id.cab_rotate_right -> rotateSelection(90)
            R.id.cab_rotate_left -> rotateSelection(270)
            R.id.cab_rotate_one_eighty -> rotateSelection(180)
            R.id.cab_mirror -> mirrorSelection()
            R.id.cab_copy_to -> checkMediaManagementAndCopy(true)
            R.id.cab_move_to -> moveFilesTo()
            R.id.cab_create_shortcut -> createShortcut()
            R.id.cab_select_all -> selectAll()
            R.id.cab_open_with -> openPath()
            R.id.cab_fix_date_taken -> fixDateTaken()
            R.id.cab_set_as -> setAs()
            R.id.cab_resize -> resize()
            R.id.cab_delete -> checkDeleteConfirmation()
        }
    }

    override fun getSelectableItemCount() = media.filter { it is Medium }.size

    override fun getIsItemSelectable(position: Int) = !isASectionTitle(position)

    override fun getItemSelectionKey(position: Int) = (media.getOrNull(position) as? Medium)?.path?.hashCode()

    override fun getItemKeyPosition(key: Int) = media.indexOfFirst { (it as? Medium)?.path?.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (activity.isDestroyed) {
            return
        }

        val itemView = holder.itemView
        // a simplified item is its own thumbnail and carries none of the state below
        if (itemView.id == R.id.medium_thumbnail) {
            simpleThumbnails.clear(itemView)
            return
        }

        resetTransientItemState(itemView)
        val tmb = itemView.allViews.firstOrNull { it.id == R.id.medium_thumbnail }
        if (tmb != null) {
            Glide.with(activity).clear(tmb)
        }
    }

    fun isASectionTitle(position: Int) = media.getOrNull(position) is ThumbnailSection

    private fun checkHideBtnVisibility(menu: Menu, selectedItems: ArrayList<Medium>) {
        val isInRecycleBin = selectedItems.firstOrNull()?.getIsInRecycleBin() == true
        menu.findItem(R.id.cab_hide).isVisible = (!isRPlus() || isExternalStorageManager()) && !isInRecycleBin && selectedItems.any { !it.isHidden() }
        menu.findItem(R.id.cab_unhide).isVisible = (!isRPlus() || isExternalStorageManager()) && !isInRecycleBin && selectedItems.any { it.isHidden() }
    }

    private fun checkFavoriteBtnVisibility(menu: Menu, selectedItems: ArrayList<Medium>) {
        menu.findItem(R.id.cab_add_to_favorites).isVisible = selectedItems.none { it.getIsInRecycleBin() } && selectedItems.any { !it.isFavorite }
        menu.findItem(R.id.cab_remove_from_favorites).isVisible = selectedItems.none { it.getIsInRecycleBin() } && selectedItems.any { it.isFavorite }
    }

    private fun confirmSelection() {
        listener?.selectedPaths(getSelectedPaths())
    }

    private fun showProperties() {
        if (selectedKeys.size <= 1) {
            val path = getFirstSelectedItemPath() ?: return
            PropertiesDialog(activity, path, config.shouldShowHidden)
        } else {
            val paths = getSelectedPaths()
            PropertiesDialog(activity, paths, config.shouldShowHidden)
        }
    }

    private fun checkMediaManagementAndRename() {
        activity.handleMediaManagementPrompt {
            renameFile()
        }
    }

    private fun renameFile() {
        val firstPath = getFirstSelectedItemPath() ?: return

        val isSDOrOtgRootFolder = activity.isAStorageRootFolder(firstPath.getParentPath()) && !firstPath.startsWith(activity.internalStoragePath)
        if (isRPlus() && isSDOrOtgRootFolder && !isExternalStorageManager()) {
            activity.toast(org.fossify.commons.R.string.rename_in_sd_card_system_restriction, Toast.LENGTH_LONG)
            finishActMode()
            return
        }

        if (selectedKeys.size == 1) {
            RenameItemDialog(activity, firstPath) {
                ensureBackgroundThread {
                    activity.updateDBMediaPath(firstPath, it)

                    activity.runOnUiThread {
                        listener?.refreshItems()
                        finishActMode()
                    }
                }
            }
        } else {
            RenameDialog(activity, getSelectedPaths(), true) {
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun editFile() {
        val path = getFirstSelectedItemPath() ?: return
        activity.openEditor(path)
    }

    private fun openPath() {
        val path = getFirstSelectedItemPath() ?: return
        activity.openPath(path, true)
    }

    private fun setAs() {
        val path = getFirstSelectedItemPath() ?: return
        activity.setAs(path)
    }

    private fun resize() {
        val paths = getSelectedItems().filter { it.isImage() }.map { it.path }
        if (isOneItemSelected()) {
            val path = paths.first()
            activity.launchResizeImageDialog(path) {
                finishActMode()
                listener?.refreshItems()
            }
        } else {
            activity.launchResizeMultipleImagesDialog(paths) {
                finishActMode()
                listener?.refreshItems()
            }
        }
    }

    private fun canResize(selectedItems: ArrayList<Medium>): Boolean {
        val selectionContainsImages = selectedItems.any { it.isImage() }
        if (!selectionContainsImages) {
            return false
        }

        val parentPath = selectedItems.first { it.isImage() }.parentPath
        val isCommonParent = selectedItems.all { parentPath == it.parentPath }
        val isRestrictedDir = activity.isRestrictedWithSAFSdk30(parentPath)
        return isExternalStorageManager() || (isCommonParent && !isRestrictedDir)
    }

    private fun toggleFileVisibility(hide: Boolean) {
        ensureBackgroundThread {
            getSelectedItems().forEach {
                activity.toggleFileVisibility(it.path, hide)
            }
            activity.runOnUiThread {
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    /** Gives the whole selection one rating. */
    private fun rateSelection() {
        val rateable = getSelectedItems().filter { it.path.canBeRated() }
        if (rateable.isEmpty()) {
            activity.toast(R.string.rating_unsupported_format)
            return
        }

        val sharedRating = rateable.map { it.rating }.distinct().singleOrNull() ?: 0
        RateMediumDialog(activity, sharedRating) { rating ->
            activity.updateFilesRating(rateable.map { it.path }, rating) { ratedPaths ->
                val rated = ratedPaths.toHashSet()
                media.forEachIndexed { index, item ->
                    if (item is Medium && rated.contains(item.path)) {
                        item.rating = rating
                        notifyItemChanged(index)
                    }
                }

                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun toggleFavorites(add: Boolean) {
        ensureBackgroundThread {
            getSelectedItems().forEach {
                it.isFavorite = add
                activity.updateFavorite(it.path, add)
            }
            activity.runOnUiThread {
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun restoreFiles() {
        val paths = getSelectedPaths()
        if (paths.size > 1) {
            activity.showRestoreConfirmationDialog(paths.size) {
                doRestoreFiles(paths)
            }
        } else {
            doRestoreFiles(paths)
        }
    }

    private fun doRestoreFiles(paths: ArrayList<String>) {
        activity.restoreRecycleBinPaths(paths) {
            listener?.refreshItems()
            finishActMode()
        }
    }

    private fun shareMedia() {
        if (selectedKeys.size == 1 && selectedKeys.first() != -1) {
            activity.shareMediumPath(getSelectedItems().first().path)
        } else if (selectedKeys.size > 1) {
            activity.shareMediaPaths(getSelectedPaths())
        }
    }

    private fun handleRotate(paths: List<String>, degrees: Int) {
        var fileCnt = paths.size
        transformedImagePaths.clear()
        activity.toast(org.fossify.commons.R.string.saving)
        ensureBackgroundThread {
            paths.forEach {
                transformedImagePaths.add(it)
                activity.saveRotatedImageToFile(it, it, degrees, true) {
                    fileCnt--
                    if (fileCnt == 0) {
                        activity.runOnUiThread {
                            listener?.refreshItems()
                            finishActMode()
                        }
                    }
                }
            }
        }
    }

    private fun rotateSelection(degrees: Int) {
        val paths = getSelectedPaths().filter { it.isImageFast() }

        if (paths.any { activity.needsStupidWritePermissions(it) }) {
            activity.handleSAFDialog(paths.first { activity.needsStupidWritePermissions(it) }) {
                if (it) {
                    handleRotate(paths, degrees)
                }
            }
        } else {
            handleRotate(paths, degrees)
        }
    }

    private fun handleMirror(paths: List<String>) {
        var fileCnt = paths.size
        activity.toast(org.fossify.commons.R.string.saving)
        ensureBackgroundThread {
            paths.forEach {
                activity.saveMirroredImageToFile(it, it, true) {
                    fileCnt--
                    if (fileCnt == 0) {
                        activity.runOnUiThread {
                            // rebind right away rather than waiting for refreshItems()' requery -
                            // the mirrored items already carry a fresh cache key by now, so this
                            // decodes them anew, unlike rotate no memory cache skipping is needed
                            paths.forEach { path ->
                                val position = media.indexOfFirst { item -> (item as? Medium)?.path == path }
                                if (position != -1) {
                                    notifyItemChanged(position)
                                }
                            }
                            listener?.refreshItems()
                            finishActMode()
                        }
                    }
                }
            }
        }
    }

    private fun mirrorSelection() {
        val paths = getSelectedPaths().filter { it.isImageFast() }

        if (paths.any { activity.needsStupidWritePermissions(it) }) {
            activity.handleSAFDialog(paths.first { activity.needsStupidWritePermissions(it) }) {
                if (it) {
                    handleMirror(paths)
                }
            }
        } else {
            handleMirror(paths)
        }
    }

    private fun moveFilesTo() {
        activity.handleDeletePasswordProtection {
            checkMediaManagementAndCopy(false)
        }
    }

    private fun checkMediaManagementAndCopy(isCopyOperation: Boolean) {
        activity.handleMediaManagementPrompt {
            copyMoveTo(isCopyOperation)
        }
    }

    private fun copyMoveTo(isCopyOperation: Boolean) {
        val paths = getSelectedPaths()

        val recycleBinPath = activity.recycleBinPath
        val fileDirItems = paths.asSequence().filter { isCopyOperation || !it.startsWith(recycleBinPath) }.map {
            FileDirItem(it, it.getFilenameFromPath())
        }.toMutableList() as ArrayList

        if (!isCopyOperation && paths.any { it.startsWith(recycleBinPath) }) {
            activity.toast(org.fossify.commons.R.string.moving_recycle_bin_items_disabled, Toast.LENGTH_LONG)
        }

        if (fileDirItems.isEmpty()) {
            return
        }

        activity.tryCopyMoveFilesTo(fileDirItems, isCopyOperation) {
            val destinationPath = it
            config.tempFolderPath = ""
            activity.applicationContext.rescanFolderMedia(destinationPath)
            activity.applicationContext.rescanFolderMedia(fileDirItems.first().getParentPath())

            val newPaths = fileDirItems.map { "$destinationPath/${it.name}" }.toMutableList() as ArrayList<String>
            activity.rescanPaths(newPaths) {
                activity.fixDateTaken(newPaths, false)
            }

            if (!isCopyOperation) {
                listener?.refreshItems()
                activity.updateFavoritePaths(fileDirItems, destinationPath)
            }
        }
    }

    private fun createShortcut() {
        val manager = activity.getSystemService(ShortcutManager::class.java)
        if (manager.isRequestPinShortcutSupported) {
            val path = getSelectedPaths().first()
            val drawable = resources.getDrawable(R.drawable.shortcut_image).mutate()
            activity.getShortcutImage(path, drawable) {
                val intent = Intent(activity, ViewPagerActivity::class.java).apply {
                    putExtra(PATH, path)
                    putExtra(SHOW_ALL, config.showAll)
                    putExtra(SHOW_FAVORITES, path == FAVORITES)
                    putExtra(SHOW_RECYCLE_BIN, path == RECYCLE_BIN)
                    action = Intent.ACTION_VIEW
                    flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }

                val shortcut = ShortcutInfo.Builder(activity, path)
                    .setShortLabel(path.getFilenameFromPath())
                    .setIcon(Icon.createWithBitmap(drawable.convertToBitmap()))
                    .setIntent(intent)
                    .build()

                manager.requestPinShortcut(shortcut, null)
            }
        }
    }

    private fun fixDateTaken() {
        ensureBackgroundThread {
            activity.fixDateTaken(getSelectedPaths(), true) {
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun checkDeleteConfirmation() {
        activity.handleMediaManagementPrompt {
            if (config.isDeletePasswordProtectionOn) {
                activity.handleDeletePasswordProtection {
                    deleteFiles(config.tempSkipRecycleBin)
                }
            } else if (config.tempSkipDeleteConfirmation || config.skipDeleteConfirmation) {
                deleteFiles(config.tempSkipRecycleBin)
            } else {
                askConfirmDelete()
            }
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val selectedMedia = getSelectedItems()
        val firstPath = selectedMedia.first().path
        val fileDirItem = selectedMedia.first().toFileDirItem()
        val size = fileDirItem.getProperSize(activity, countHidden = true).formatSize()
        val itemsAndSize = if (itemsCnt == 1) {
            fileDirItem.mediaStoreId = selectedMedia.first().mediaStoreId
            "\"${firstPath.getFilenameFromPath()}\" ($size)"
        } else {
            val fileDirItems = ArrayList<FileDirItem>(selectedMedia.size)
            selectedMedia.forEach { medium ->
                val curFileDirItem = medium.toFileDirItem()
                fileDirItems.add(curFileDirItem)
            }
            val fileSize = fileDirItems.sumByLong { it.getProperSize(activity, countHidden = true) }.formatSize()
            val deleteItemsString = resources.getQuantityString(org.fossify.commons.R.plurals.delete_items, itemsCnt, itemsCnt)
            "$deleteItemsString ($fileSize)"
        }

        val isRecycleBin = firstPath.startsWith(activity.recycleBinPath)
        val baseString =
            if (config.useRecycleBin && !config.tempSkipRecycleBin && !isRecycleBin) org.fossify.commons.R.string.move_to_recycle_bin_confirmation else org.fossify.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), itemsAndSize)
        val showSkipRecycleBinOption = config.useRecycleBin && !isRecycleBin

        DeleteWithRememberDialog(activity, question, showSkipRecycleBinOption) { remember, skipRecycleBin ->
            config.tempSkipDeleteConfirmation = remember

            if (remember) {
                config.tempSkipRecycleBin = skipRecycleBin
            }

            deleteFiles(skipRecycleBin)
        }
    }

    private fun deleteFiles(skipRecycleBin: Boolean) {
        if (selectedKeys.isEmpty()) {
            return
        }

        val selectedItems = getSelectedItems()
        val selectedPaths = selectedItems.map { it.path } as ArrayList<String>
        val SAFPath = selectedPaths.firstOrNull { activity.needsStupidWritePermissions(it) } ?: getFirstSelectedItemPath() ?: return
        activity.handleSAFDialog(SAFPath) {
            if (!it) {
                return@handleSAFDialog
            }

            val sdk30SAFPath = selectedPaths.firstOrNull { activity.isAccessibleWithSAFSdk30(it) } ?: getFirstSelectedItemPath() ?: return@handleSAFDialog
            activity.checkManageMediaOrHandleSAFDialogSdk30(sdk30SAFPath) {
                if (!it) {
                    return@checkManageMediaOrHandleSAFDialogSdk30
                }

                val fileDirItems = ArrayList<FileDirItem>(selectedKeys.size)
                val removeMedia = ArrayList<Medium>(selectedKeys.size)
                val positions = getSelectedItemPositions()

                selectedItems.forEach { medium ->
                    fileDirItems.add(medium.toFileDirItem())
                    removeMedia.add(medium)
                }

                media.removeAll(removeMedia)
                listener?.tryDeleteFiles(fileDirItems, skipRecycleBin)
                listener?.updateMediaGridDecoration(media)
                removeSelectedItems(positions)
                currentMediaHash = media.hashCode()
            }
        }
    }

    private fun getSelectedItems() = selectedKeys.mapNotNull { getItemWithKey(it) } as ArrayList<Medium>

    private fun getSelectedPaths() = getSelectedItems().map { it.path } as ArrayList<String>

    private fun getFirstSelectedItemPath() = getItemWithKey(selectedKeys.first())?.path

    private fun getItemWithKey(key: Int): Medium? = media.firstOrNull { (it as? Medium)?.path?.hashCode() == key } as? Medium

    /** Hands the grid a different list, or simply accepts the one [reorderMode] rearranged. */
    internal fun replaceMedia(newMedia: ArrayList<ThumbnailItem>?) {
        if (newMedia != null) {
            media = newMedia.clone() as ArrayList<ThumbnailItem>
        }

        currentMediaHash = media.hashCode()
    }

    /** Repaints an item's check without rebinding it, which would restart its image request. */
    internal fun repaintSelection(itemView: View, medium: Medium) {
        bindItem(itemView, medium).markSelected(isItemSelected(medium))
    }

    // a view let go of mid drag would come back to another item still lifted, one recycled mid
    // reveal still growing
    private fun resetTransientItemState(itemView: View) {
        itemView.animate().cancel()
        gridNavigator.cancelReveal()
        reorderMode.resetItemState(itemView)
    }

    fun updateMedia(newMedia: ArrayList<ThumbnailItem>) {
        if (reorderMode.isActive) {
            return
        }

        val thumbnailItems = newMedia.clone() as ArrayList<ThumbnailItem>
        // an in-place transform (see TransformedMedia) leaves every field this hashcode is built
        // from untouched, so also rebind whenever one happened while this screen was off top
        val transformGeneration = TransformedMedia.generation
        if (thumbnailItems.hashCode() != currentMediaHash || transformGeneration != currentTransformGeneration) {
            currentMediaHash = thumbnailItems.hashCode()
            currentTransformGeneration = transformGeneration
            media = thumbnailItems
            notifyDataSetChanged()
            finishActMode()
        }
    }

    /** Sets the initial state, before the adapter has ever been laid out. See [setSimplified]. */
    fun setSimplifiedInitially(simplified: Boolean) {
        isSimplified = simplified
    }

    /**
     * Crosses between full thumbnails and the stripped ones, taking the list with it - grouping
     * headers are dropped from a simplified grid. See [GridZoom].
     */
    @SuppressLint("NotifyDataSetChanged") // every position changes its view type, and its item
    fun setSimplified(simplified: Boolean, newMedia: ArrayList<ThumbnailItem>) {
        if (isSimplified == simplified) {
            return
        }

        isSimplified = simplified
        finishActMode()
        replaceMedia(newMedia)
        notifyDataSetChanged()
    }

    /**
     * Takes the count the grid is about to draw: what a thumbnail is decoded to, and the size of
     * the recycler's caches. The cache defaults - two views off screen, five per type pooled - are a
     * fraction of a row at twenty columns, where a fling would otherwise inflate items the whole
     * way down.
     */
    fun applyColumnCount(columnCount: Int) {
        this.columnCount = columnCount
        recyclerView.setItemViewCacheSize(columnCount * CACHED_ROWS)
        val poolSize = columnCount * POOLED_ROWS
        listOf(ITEM_MEDIUM_SIMPLE, ITEM_MEDIUM_PHOTO, ITEM_MEDIUM_VIDEO_PORTRAIT).forEach {
            recyclerView.recycledViewPool.setMaxRecycledViews(it, poolSize)
        }
    }

    fun updateDisplayFilenames(displayFilenames: Boolean) {
        this.displayFilenames = displayFilenames
        notifyDataSetChanged()
    }

    fun updateAnimateGifs(animateGifs: Boolean) {
        this.animateGifs = animateGifs
        notifyDataSetChanged()
    }

    fun updateCropThumbnails(cropThumbnails: Boolean) {
        this.cropThumbnails = cropThumbnails
        preparedSimpleThumbnails = null
        notifyDataSetChanged()
    }

    fun updateShowFileTypes(showFileTypes: Boolean) {
        this.showFileTypes = showFileTypes
        notifyDataSetChanged()
    }

    fun updateShowRatings(showRatings: Boolean) {
        this.showRatings = showRatings
        notifyDataSetChanged()
    }

    private fun getRoundedCorners() = when {
        isListViewType -> ROUNDED_CORNERS_SMALL
        config.fileRoundedCorners -> ROUNDED_CORNERS_BIG
        else -> ROUNDED_CORNERS_NONE
    }

    /** How round a thumbnail is drawn, for anything that has to trace one - see [MediaReorderMode]. */
    internal val thumbnailCornerRadius: Float
        get() {
            val radiusId = when (getRoundedCorners()) {
                ROUNDED_CORNERS_SMALL -> org.fossify.commons.R.dimen.rounded_corner_radius_small
                ROUNDED_CORNERS_BIG -> org.fossify.commons.R.dimen.rounded_corner_radius_big
                else -> return 0f
            }

            return activity.resources.getDimension(radiusId)
        }

    /**
     * A ticked item wears the same check while reordering as it does in the action mode - it means
     * the same thing, the item is one of several the next command applies to.
     */
    private fun MediaItemBinding.markSelected(isSelected: Boolean) {
        mediumCheck.beVisibleIf(isSelected)
        if (isSelected) {
            mediumCheck.background?.applyColorFilter(properPrimaryColor)
            mediumCheck.applyColorFilter(contrastColor)
        }

        if (isListViewType) {
            mediaItemHolder.isSelected = isSelected
        }
    }

    private fun isItemSelected(medium: Medium) = if (reorderMode.isActive) {
        reorderMode.isMarked(medium)
    } else {
        selectedKeys.contains(medium.path.hashCode())
    }

    /**
     * The size a full thumbnail is decoded to: the column count's nominal share of the grid, rather
     * than the width the tile it goes in is actually given. Null in the list view, whose thumbnail
     * is a fixed size of its own with no column to be divided out of.
     *
     * An uneven division leaves the layout manager a few pixels to spread across the row, so tiles a
     * column apart differ by one - and the size asked for is part of Glide's cache key, so a tile
     * sized by its own view stores the same picture twice for a single column count, once for each
     * of the two widths a row holds. The odd tile is a pixel wider than its bitmap, which the
     * ImageView takes up. The simplified rungs do the same with `GridZoom.simpleThumbnailSize`.
     *
     * That share is then rounded to a rung of [ThumbnailSizes], which does the same thing one step
     * further out: column counts whose tiles come within a step of each other - five and six, or a
     * count read down the screen and one read across it - stop keeping a copy of the picture each.
     */
    private fun thumbnailSize(): Int? {
        if (isListViewType) {
            return null
        }

        // the span count divides the axis the grid does *not* scroll along
        val across = if (scrollHorizontally) {
            recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
        } else {
            recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight
        }

        // nothing to divide before the grid has been measured; the view's own size will do
        if (across <= 0) {
            return null
        }

        // a tile does not fill its span: a thin spacing is padding on the item (below), anything
        // wider is the item decoration's insets, which come to about one spacing per item
        val spacing = config.thumbnailSpacing
        val inset = if (spacing <= 1) spacing * 2 else spacing
        return ThumbnailSizes.snap((across / columnCount - inset).coerceAtLeast(1))
    }

    private fun setupThumbnail(view: View, medium: Medium) {
        val isSelected = isItemSelected(medium)
        bindItem(view, medium).apply {
            val padding = if (config.thumbnailSpacing <= 1) {
                config.thumbnailSpacing
            } else {
                0
            }

            mediaItemHolder.setPadding(padding, padding, padding, padding)

            favorite.beVisibleIf(medium.isFavorite && config.markFavoriteItems)

            val showRating = medium.rating > 0 && showRatings
            if (showRating) {
                ratingBadge?.text = medium.rating.toString()
            }
            ratingBadge?.beVisibleIf(showRating)

            playPortraitOutline?.beVisibleIf(medium.isVideo() || medium.isPortrait())
            if (medium.isVideo()) {
                playPortraitOutline?.setImageResource(
                    if (isListViewType) {
                        org.fossify.commons.R.drawable.ic_play_outline_vector
                    } else {
                        org.fossify.commons.R.drawable.ic_play_vector
                    }
                )
                playPortraitOutline?.beVisible()
            } else if (medium.isPortrait()) {
                playPortraitOutline?.setImageResource(R.drawable.ic_portrait_photo_vector)
                playPortraitOutline?.beVisibleIf(showFileTypes)
            }

            if (showFileTypes && (medium.isGIF() || medium.isRaw() || medium.isSVG())) {
                fileType?.setText(
                    when (medium.type) {
                        TYPE_GIFS -> R.string.gif
                        TYPE_RAWS -> R.string.raw
                        else -> R.string.svg
                    }
                )
                fileType?.beVisible()
            } else {
                fileType?.beGone()
            }

            mediumName.beVisibleIf(displayFilenames || isListViewType)
            mediumName.text = medium.name
            mediumName.tag = medium.path

            val showVideoDuration = medium.isVideo() && config.showThumbnailVideoDuration
            if (showVideoDuration) {
                videoDuration?.text = medium.videoDuration.getFormattedDuration()
            }
            videoDuration?.beVisibleIf(showVideoDuration)
            if (isListViewType) {
                videoDuration?.setTextColor(textColor)
            }

            markSelected(isSelected)

            var path = medium.path
            if (hasOTGConnected && root.context.isPathOnOTG(path)) {
                path = path.getOTGPublicPath(root.context)
            }

            val roundedCorners = getRoundedCorners()
            mediumThumbnail.setBackgroundResource(
                when (roundedCorners) {
                    ROUNDED_CORNERS_SMALL -> R.drawable.placeholder_rounded_small
                    ROUNDED_CORNERS_BIG -> R.drawable.placeholder_rounded_big
                    else -> R.drawable.placeholder_square
                }
            )

            // a file that failed to load left its warning icon centred here, and the view outlives
            // the item it failed for - without this every later picture bound to it is drawn at its
            // own size in the middle of the tile instead of filling it
            mediumThumbnail.scaleType = ImageView.ScaleType.FIT_CENTER

            activity.loadImage(
                type = medium.type,
                path = path,
                target = mediumThumbnail,
                horizontalScroll = scrollHorizontally,
                animateGifs = animateGifs,
                cropThumbnails = cropThumbnails,
                roundCorners = roundedCorners,
                signature = medium.getKey(),
                overrideSize = thumbnailSize(),
                skipMemoryCacheAtPaths = transformedImagePaths,
                onError = {
                    mediumThumbnail.scaleType = ImageView.ScaleType.CENTER
                    mediumThumbnail.setImageDrawable(AppCompatResources.getDrawable(activity, R.drawable.ic_vector_warning_colored))
                }
            )

            if (isListViewType) {
                mediumName.setTextColor(textColor)
                playPortraitOutline?.applyColorFilter(textColor)
            }
        }
    }

    /** The whole of a simplified item: its picture, and nothing else at all. See [GridZoom]. */
    private fun setupSimpleThumbnail(view: View, medium: Medium) {
        val thumbnail = view as MySquareImageView
        thumbnail.isHorizontalScrolling = scrollHorizontally

        var path = medium.path
        if (hasOTGConnected && thumbnail.context.isPathOnOTG(path)) {
            path = path.getOTGPublicPath(thumbnail.context)
        }

        if (medium.type == TYPE_SVGS) {
            activity.loadSVG(
                path = path,
                target = thumbnail,
                cropThumbnails = cropThumbnails,
                roundCorners = ROUNDED_CORNERS_NONE,
                signature = medium.getKey()
            )
        } else {
            simpleThumbnails.load(path, thumbnail, medium.getKey())
        }
    }

    private fun setupSection(view: View, section: ThumbnailSection) {
        ThumbnailSectionBinding.bind(view).apply {
            thumbnailSection.text = section.title
            thumbnailSection.setTextColor(textColor)
        }
    }

    override fun onChange(position: Int): String {
        var realIndex = position
        if (isASectionTitle(position)) {
            realIndex++
        }

        return (media[realIndex] as? Medium)?.getBubbleText(sorting, activity, dateFormat, timeFormat) ?: ""
    }

    private fun bindItem(view: View, medium: Medium): MediaItemBinding {
        return if (isListViewType) {
            if (!medium.isVideo() && !medium.isPortrait()) {
                PhotoItemListBinding.bind(view).toMediaItemBinding()
            } else {
                VideoItemListBinding.bind(view).toMediaItemBinding()
            }
        } else {
            if (!medium.isVideo() && !medium.isPortrait()) {
                PhotoItemGridBinding.bind(view).toMediaItemBinding()
            } else {
                VideoItemGridBinding.bind(view).toMediaItemBinding()
            }
        }
    }
}
