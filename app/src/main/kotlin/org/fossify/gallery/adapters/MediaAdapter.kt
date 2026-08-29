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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.allViews
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
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
import org.fossify.gallery.extensions.preloadImage
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
import org.fossify.gallery.helpers.clearPanelMotion
import org.fossify.gallery.helpers.GridZoom
import org.fossify.gallery.helpers.hidePanel
import org.fossify.gallery.helpers.PANEL_ENTER_MS
import org.fossify.gallery.helpers.PANEL_EXIT_MS
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ROUNDED_CORNERS_BIG
import org.fossify.gallery.helpers.ROUNDED_CORNERS_NONE
import org.fossify.gallery.helpers.ROUNDED_CORNERS_SMALL
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_FAVORITES
import org.fossify.gallery.helpers.SHOW_RECYCLE_BIN
import org.fossify.gallery.helpers.showPanel
import org.fossify.gallery.helpers.SimpleThumbnailLoader
import org.fossify.gallery.helpers.ThumbnailPrefetcher
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
     * Opens the peek viewer on one item, hooked up by the screen that owns the grid. The list and
     * the selection are the adapter's; where they get shown is not.
     */
    var onPeekRequested: ((media: List<Medium>, selectedPaths: Set<String>, path: String) -> Unit)? =
        null

    /**
     * Decodes what the grid is scrolling towards before it gets there. Null in the list view, whose
     * thumbnail is sized by the view it goes in ([thumbnailSize] returns null for it) - there is no
     * size to warm a cache entry at without inventing one the bind will not ask for.
     */
    private val prefetcher = if (isListViewType) {
        null
    } else {
        ThumbnailPrefetcher(
            recyclerView = recyclerView,
            preloadAt = ::prefetchThumbnail,
            cancel = { Glide.with(activity.applicationContext).clear(it) }
        )
    }

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
        // upstream invalidates the action mode whenever the count in its title changes, which is the
        // one place every way of selecting something passes through
        repaintSectionChecks()

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

    /** Told when a selection starts or ends, for chrome that has to get out of its way. */
    var onSelectionModeChanged: ((Boolean) -> Unit)? = null

    override fun onActionModeCreated() {
        updatePeekButtons(selecting = true)
        updateSectionChecks(selecting = true)
        onSelectionModeChanged?.invoke(true)
    }

    override fun onActionModeDestroyed() {
        updatePeekButtons(selecting = false)
        updateSectionChecks(selecting = false)
        onSelectionModeChanged?.invoke(false)
    }

    /**
     * Puts the peek buttons up or away on the items already on screen. Written straight onto the
     * views rather than gone about through notifyDataSetChanged(), which would restart every
     * thumbnail's decode for the sake of one icon appearing. Items bound after this get it from
     * [bindPeekButton].
     *
     * Told whether a selection is on rather than asking [isSelecting], which upstream sets around
     * these two calls without saying in which order.
     */
    private fun updatePeekButtons(selecting: Boolean) {
        val visible = selecting && tileFitsPeekButton()
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            val peek = child.findViewById<View>(R.id.medium_peek) ?: continue
            peek.beVisibleIf(visible)
            // the duration shares that corner and gives way to it; see setupThumbnail. Only the
            // video layouts carry one, so a photo on screen finds nothing here to change
            child.findViewById<TextView>(R.id.video_duration)
                ?.beVisibleIf(!visible && config.showThumbnailVideoDuration)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        prefetcher?.detach()
    }

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
        prefetcher?.reset()
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
            prefetcher?.reset()
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
        // the count is what a thumbnail is decoded to, so everything in flight is now the wrong size
        prefetcher?.reset()
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
        // the crop is a transformation, and a transformation is part of what was asked for
        prefetcher?.reset()
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
     * The button that opens the peek viewer on this item. Up only while the action mode is: it is a
     * tool for choosing between pictures too small to judge, and a grid nobody is selecting in has
     * the plain tap for opening one.
     */
    private fun MediaItemBinding.bindPeekButton(medium: Medium) {
        val peek = mediumPeek ?: return
        peek.beVisibleIf(isPeekButtonUp())
        peek.setOnClickListener {
            onPeekRequested?.invoke(media.filterIsInstance<Medium>(), getSelectedPaths().toSet(), medium.path)
        }
    }

    /** Whether the action mode owns the grid; reordering borrows the same gestures for itself. */
    private fun isSelecting() = !reorderMode.isActive && actModeCallback.isSelectable

    /** Whether the peek button belongs on a tile at all: a selection is on, and there is room. */
    private fun isPeekButtonUp() = isSelecting() && tileFitsPeekButton()

    /**
     * Whether a tile is wide enough for the button to be worth its place. Zoomed out far enough it
     * and the tick between them take most of the tile, and the button ends up covering the picture
     * it is offering to show - see [R.dimen.peek_button_min_tile]. The picture is still reachable
     * from the tile's own tap once the selection is over.
     */
    private fun tileFitsPeekButton(): Boolean {
        val tile = tileSize() ?: return false
        return tile >= resources.getDimensionPixelSize(R.dimen.peek_button_min_tile)
    }

    /**
     * Puts the selection where the peek viewer left it. Upstream's own toggle does the work - it is
     * what keeps the action mode's title right, and what ends the mode once the last item goes - so
     * all this does is work out the differences and feed them in.
     *
     * Additions go before removals, and only the last of them redraws the title: one item swapped
     * for another would otherwise pass through an empty selection, and an empty selection is what
     * ends the mode.
     */
    fun applySelection(paths: Set<String>) {
        val changes = media.withIndex().mapNotNull { (position, item) ->
            val medium = item as? Medium ?: return@mapNotNull null
            val select = paths.contains(medium.path)
            if (select == selectedKeys.contains(medium.path.hashCode())) {
                null
            } else {
                position to select
            }
        }.sortedByDescending { (_, select) -> select }

        changes.forEachIndexed { index, (position, select) ->
            toggleItemSelection(select, position, index == changes.lastIndex)
        }
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
    private fun thumbnailSize() = tileSize()?.let { ThumbnailSizes.snap(it.coerceAtLeast(1)) }

    /**
     * The width a tile is actually given, before [thumbnailSize] rounds it to a rung. Null in the
     * list view, which has no column to be divided out of, and before the grid has been measured.
     */
    private fun tileSize(): Int? {
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
        return across / columnCount - inset
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

            // the peek button takes the corner the duration shares, and squeezed in beside it on a
            // narrow tile the duration is cut to an ellipsis - better gone for the length of the
            // selection than there and unreadable
            val roomForDuration = mediumPeek == null || !isPeekButtonUp()
            val showVideoDuration =
                medium.isVideo() && config.showThumbnailVideoDuration && roomForDuration
            if (showVideoDuration) {
                videoDuration?.text = medium.videoDuration.getFormattedDuration()
            }
            videoDuration?.beVisibleIf(showVideoDuration)
            if (isListViewType) {
                videoDuration?.setTextColor(textColor)
            }

            markSelected(isSelected)
            bindPeekButton(medium)

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

    /**
     * Starts the same request [setupThumbnail] or [setupSimpleThumbnail] would, for an item the grid
     * has not reached yet - see [ThumbnailPrefetcher]. Null for anything there is nothing to warm:
     * a grouping header, an SVG (rendered by a pipeline of its own), and a file whose thumbnail is
     * deliberately kept out of the memory cache because it was just edited in place (see
     * `TransformedMedia`).
     */
    private fun prefetchThumbnail(position: Int): Target<*>? {
        val medium = media.getOrNull(position) as? Medium ?: return null
        if (medium.type == TYPE_SVGS || transformedImagePaths.contains(medium.path)) {
            return null
        }

        var path = medium.path
        if (hasOTGConnected && activity.isPathOnOTG(path)) {
            path = path.getOTGPublicPath(activity)
        }

        if (isSimplified) {
            return simpleThumbnails.preload(path, medium.getKey())
        }

        val size = thumbnailSize() ?: return null
        return activity.preloadImage(
            type = medium.type,
            path = path,
            cropThumbnails = cropThumbnails,
            roundCorners = getRoundedCorners(),
            signature = medium.getKey(),
            overrideSize = size,
            animateGifs = animateGifs
        )
    }

    private fun setupSection(view: View, section: ThumbnailSection) {
        ThumbnailSectionBinding.bind(view).apply {
            thumbnailSection.text = section.title
            thumbnailSection.setTextColor(textColor)
            // a header recycled out of a shifted one, or out of a tick caught mid fade
            thumbnailSection.translationX = 0f
            thumbnailSectionCheck.clearPanelMotion()

            val selecting = isSelecting()
            thumbnailSectionCheck.beVisibleIf(selecting)
            thumbnailSectionCheck.isClickable = selecting
            if (selecting) {
                paintSectionCheck(thumbnailSectionCheck, isWholeGroupSelected(view.groupRange()))
                // asked of the grid at the moment of the tap rather than bound in: a header outlives
                // several positions as the list is scrolled past it
                thumbnailSectionCheck.setOnClickListener { toggleGroup(view.groupRange()) }
            }
        }
    }

    /**
     * The items under this group's header, or an empty range for anything that is not one. Read off
     * the grid rather than off a position handed in at bind time, which a recycled header outlives.
     */
    private fun View.groupRange(): IntRange {
        val header = recyclerView.getChildAdapterPosition(this)
        if (header == RecyclerView.NO_POSITION || !isASectionTitle(header)) {
            return IntRange.EMPTY
        }

        var last = header
        while (last + 1 < media.size && media[last + 1] is Medium) {
            last++
        }

        return (header + 1)..last
    }

    private fun isWholeGroupSelected(group: IntRange) =
        !group.isEmpty() && group.all { selectedKeys.contains(getItemSelectionKey(it)) }

    /**
     * Takes the whole group in, or lets the whole group go when it was already in. Upstream's own
     * toggle does the work, exactly as [applySelection] leans on it - only the last of them redraws
     * the title, and letting go of a group that was the entire selection ends the mode with it.
     */
    private fun toggleGroup(group: IntRange) {
        if (group.isEmpty()) {
            return
        }

        val select = !isWholeGroupSelected(group)
        group.forEachIndexed { index, position ->
            toggleItemSelection(select, position, index == group.count() - 1)
        }
    }

    private fun paintSectionCheck(check: ImageView, selected: Boolean) {
        if (selected) {
            check.setBackgroundResource(R.drawable.circle_background)
            check.setImageResource(org.fossify.commons.R.drawable.ic_check_vector)
            check.background?.applyColorFilter(properPrimaryColor)
            check.applyColorFilter(contrastColor)
        } else {
            check.setBackgroundResource(R.drawable.circle_outline)
            check.setImageDrawable(null)
            check.background?.applyColorFilter(textColor)
        }
    }

    /** Brings every header on screen in line with what is currently selected under it. */
    private fun repaintSectionChecks() {
        if (!isSelecting()) {
            return
        }

        for (i in 0 until recyclerView.childCount) {
            val header = recyclerView.getChildAt(i) ?: continue
            val check = header.findViewById<ImageView>(R.id.thumbnail_section_check) ?: continue
            paintSectionCheck(check, isWholeGroupSelected(header.groupRange()))
        }
    }

    /**
     * Puts the group ticks up or away on the headers already on screen, alongside [updatePeekButtons]
     * and for the same reason. The title slides the tick's own width out of the way rather than
     * jumping: the layout has already made room by the time this runs, so the title is carried back
     * to where it was and let go of.
     */
    private fun updateSectionChecks(selecting: Boolean) {
        val shift = resources.getDimensionPixelSize(R.dimen.selection_check_size) +
            resources.getDimensionPixelSize(R.dimen.section_check_gap)

        for (i in 0 until recyclerView.childCount) {
            val header = recyclerView.getChildAt(i) ?: continue
            val check = header.findViewById<ImageView>(R.id.thumbnail_section_check) ?: continue
            val title = header.findViewById<View>(R.id.thumbnail_section) ?: continue
            check.isClickable = selecting
            title.animate().cancel()
            if (selecting) {
                paintSectionCheck(check, isWholeGroupSelected(header.groupRange()))
                check.setOnClickListener { toggleGroup(header.groupRange()) }
                check.showPanel()
                // the tick takes its place the moment it is VISIBLE, so the title is already where
                // it is going to end up and only has to be carried back and let go of
                title.translationX = -shift.toFloat()
                title.animate().translationX(0f).setDuration(PANEL_ENTER_MS).start()
            } else {
                // going the other way the tick holds its place until it is GONE, so the title walks
                // to where the layout is about to put it. The one end action settles both, the
                // title's own animator being cancelled rather than raced: two of them writing the
                // same translation leaves whichever finishes last with the final say
                title.animate().translationX(-shift.toFloat()).setDuration(PANEL_EXIT_MS).start()
                check.hidePanel(onGone = {
                    title.animate().cancel()
                    title.translationX = 0f
                })
            }
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
