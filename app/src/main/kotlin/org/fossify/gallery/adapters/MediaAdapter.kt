package org.fossify.gallery.adapters

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.animation.doOnEnd
import androidx.core.view.allViews
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
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
import org.fossify.commons.helpers.MAX_ALPHA_INT
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.helpers.sumByLong
import org.fossify.commons.interfaces.ItemMoveCallback
import org.fossify.commons.interfaces.ItemTouchHelperContract
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.databinding.PhotoItemGridBinding
import org.fossify.gallery.databinding.PhotoItemListBinding
import org.fossify.gallery.databinding.ThumbnailSectionBinding
import org.fossify.gallery.databinding.VideoItemGridBinding
import org.fossify.gallery.databinding.VideoItemListBinding
import org.fossify.gallery.dialogs.DeleteWithRememberDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.fixDateTaken
import org.fossify.gallery.extensions.getShortcutImage
import org.fossify.gallery.extensions.handleMediaManagementPrompt
import org.fossify.gallery.extensions.launchResizeImageDialog
import org.fossify.gallery.extensions.launchResizeMultipleImagesDialog
import org.fossify.gallery.extensions.loadImage
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
import org.fossify.gallery.helpers.DRAG_BORDER_WIDTH_FRACTION
import org.fossify.gallery.helpers.DRAG_LIFT_DURATION_MS
import org.fossify.gallery.helpers.DRAG_LIFT_SCALE
import org.fossify.gallery.helpers.HIGHLIGHT_BORDER_OPAQUE_ALPHA
import org.fossify.gallery.helpers.HIGHLIGHT_BORDER_WIDTH_FRACTION
import org.fossify.gallery.helpers.HIGHLIGHT_FADE_IN_DURATION_MS
import org.fossify.gallery.helpers.HIGHLIGHT_FADE_OUT_DURATION_MS
import org.fossify.gallery.helpers.HIGHLIGHT_HOLD_DURATION_MS
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ROUNDED_CORNERS_BIG
import org.fossify.gallery.helpers.ROUNDED_CORNERS_NONE
import org.fossify.gallery.helpers.ROUNDED_CORNERS_SMALL
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_FAVORITES
import org.fossify.gallery.helpers.SHOW_RECYCLE_BIN
import org.fossify.gallery.helpers.TransformedMedia
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.interfaces.MediaOperationsListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import java.util.Collections

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
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), ItemTouchHelperContract,
    RecyclerViewFastScroller.OnPopupTextUpdate {

    private val ITEM_SECTION = 0
    private val ITEM_MEDIUM_VIDEO_PORTRAIT = 1
    private val ITEM_MEDIUM_PHOTO = 2

    private val config = activity.config
    private val viewType = config.getFolderViewType(if (config.showAll) SHOW_ALL else path)
    private val isListViewType = viewType == VIEW_TYPE_LIST
    private var transformedImagePaths = ArrayList<String>()
    private var currentMediaHash = media.hashCode()
    private var currentTransformGeneration = TransformedMedia.generation
    private val hasOTGConnected = activity.hasOTGConnected()

    private var highlightAnimator: Animator? = null
    private var dragLiftAnimator: Animator? = null

    private var isReordering = false
    private var itemTouchHelper: ItemTouchHelper? = null

    // paths ticked off while reordering, kept in the order they were ticked - a separate set from
    // selectedKeys, which belongs to the action mode and has no business being up mid arrangement
    private val reorderSelection = LinkedHashSet<String>()

    // what the drag under way is carrying: the marked items in the order they were picked up, the
    // dragged one among them. empty while a lone item is being dragged
    private var carriedItems = emptyList<Medium>()
    private var draggedPath: String? = null

    // told what is marked and what a drag is carrying, so the reorder bar can say so
    var onReorderStateChanged: ((marked: Int, carried: Int) -> Unit)? = null

    private var scrollHorizontally = config.scrollHorizontally
    private var animateGifs = config.animateGifs
    private var cropThumbnails = config.cropThumbnails
    private var displayFilenames = config.displayFileNames
    private var showFileTypes = config.showThumbnailFileTypes

    var sorting = config.getFolderSorting(if (config.showAll) SHOW_ALL else path)
    var dateFormat = config.dateFormat
    var timeFormat = activity.getTimeFormat()

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_media

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = if (viewType == ITEM_SECTION) {
            ThumbnailSectionBinding.inflate(layoutInflater, parent, false)
        } else {
            if (isListViewType) {
                if (viewType == ITEM_MEDIUM_PHOTO) {
                    PhotoItemListBinding.inflate(layoutInflater, parent, false)
                } else {
                    VideoItemListBinding.inflate(layoutInflater, parent, false)
                }
            } else {
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
        val allowLongPress = !isReordering && (!isAGetIntent || allowMultiplePicks) && tmbItem is Medium
        holder.bindView(tmbItem, !isReordering && tmbItem is Medium, allowLongPress) { itemView, adapterPosition ->
            if (tmbItem is Medium) {
                setupThumbnail(itemView, tmbItem)
            } else {
                setupSection(itemView, tmbItem as ThumbnailSection)
            }
        }

        // while reordering the action mode is out of reach, bindView cleared both listeners above
        // so the two gestures are free to mean something else: a long press picks the thumbnail up,
        // a tap marks it to travel along with whatever is picked up next
        if (isReordering && tmbItem is Medium) {
            holder.itemView.setOnClickListener { toggleReorderSelection(tmbItem, holder) }
            holder.itemView.setOnLongClickListener {
                startDragging(tmbItem, holder)
                true
            }
        }

        bindViewHolder(holder)
    }

    override fun getItemCount() = media.size

    override fun getItemViewType(position: Int): Int {
        val tmbItem = media[position]
        return when {
            tmbItem is ThumbnailSection -> ITEM_SECTION
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
        if (!activity.isDestroyed) {
            val itemView = holder.itemView
            // a view let go of mid drag would come back to another item still lifted
            itemView.animate().cancel()
            itemView.scaleX = 1f
            itemView.scaleY = 1f
            itemView.translationZ = 0f
            // the tick is put back on every bind, the count of a carried group is not
            itemView.findCountBadge()?.beGone()

            val tmb = itemView.allViews.firstOrNull { it.id == R.id.medium_thumbnail }
            if (tmb != null) {
                Glide.with(activity).clear(tmb)
                // drop a leftover highlight or drag border, it would otherwise show up on another item
                tmb.foreground = null
            }
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

    /**
     * Swaps the grid over to [newMedia] - which the caller flattens, sections cannot take part in a
     * hand made order - and lets a long press start a drag rather than a selection. Leaving the
     * mode restores whatever list the caller passes back in, or keeps the dragged one when the
     * caller passes none.
     */
    fun setReordering(reordering: Boolean, newMedia: ArrayList<ThumbnailItem>? = null) {
        isReordering = reordering
        carriedItems = emptyList()
        draggedPath = null
        reorderSelection.clear()
        notifyReorderState()
        if (reordering) {
            finishActMode()
            if (itemTouchHelper == null) {
                itemTouchHelper = ItemTouchHelper(NearestCellMoveCallback(this))
            }
            itemTouchHelper?.attachToRecyclerView(recyclerView)
        } else {
            itemTouchHelper?.attachToRecyclerView(null)
        }

        if (newMedia != null) {
            media = newMedia.clone() as ArrayList<ThumbnailItem>
        }

        currentMediaHash = media.hashCode()
        notifyDataSetChanged()
    }

    fun getReorderedPaths(): List<String> {
        // a save that lands while a drag is still carrying items must not go out without them
        dropCarriedItems()
        return media.mapNotNull { (it as? Medium)?.path }
    }

    fun updateMedia(newMedia: ArrayList<ThumbnailItem>) {
        if (isReordering) {
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
        notifyDataSetChanged()
    }

    fun updateShowFileTypes(showFileTypes: Boolean) {
        this.showFileTypes = showFileTypes
        notifyDataSetChanged()
    }

    /**
     * Scrolls the item at [path] into view if needed and briefly pulses a border over it, so it is
     * obvious which thumbnail the fullscreen viewer was just left from. Returns false if the item
     * is not in the grid (deleted, filtered out), letting the caller retry after a refresh.
     */
    fun revealItem(path: String): Boolean {
        val position = getItemKeyPosition(path.hashCode())
        if (position == -1) {
            return false
        }

        // scroll on the next pass so the grid is laid out, then highlight once that scroll settled
        recyclerView.post {
            scrollToItemIfNeeded(position)
            recyclerView.post { highlightItem(position) }
        }

        return true
    }

    private fun scrollToItemIfNeeded(position: Int) {
        val layoutManager = recyclerView.layoutManager as? MyGridLayoutManager ?: return
        val isHorizontal = layoutManager.orientation == RecyclerView.HORIZONTAL
        val itemView = layoutManager.findViewByPosition(position)
        if (itemView != null && isFullyVisible(itemView, isHorizontal)) {
            return
        }

        val available = if (isHorizontal) {
            recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight
        } else {
            recyclerView.height - recyclerView.paddingTop - recyclerView.paddingBottom
        }

        val itemSize = getItemSize(isHorizontal) ?: (available / layoutManager.spanCount)
        layoutManager.scrollToPositionWithOffset(position, ((available - itemSize) / 2).coerceAtLeast(0))
    }

    private fun isFullyVisible(itemView: View, isHorizontal: Boolean) = if (isHorizontal) {
        itemView.left >= recyclerView.paddingLeft && itemView.right <= recyclerView.width - recyclerView.paddingRight
    } else {
        itemView.top >= recyclerView.paddingTop && itemView.bottom <= recyclerView.height - recyclerView.paddingBottom
    }

    // all media items share a size, section titles do not, so measure any visible medium
    private fun getItemSize(isHorizontal: Boolean): Int? {
        return recyclerView.children
            .firstOrNull {
                val position = recyclerView.getChildAdapterPosition(it)
                position != RecyclerView.NO_POSITION && !isASectionTitle(position)
            }?.let {
                if (isHorizontal) it.width else it.height
            }
    }

    /**
     * An accent ring following the thumbnail's own corners, [widthFraction] of its width so it
     * stays in proportion whatever column count the grid is on.
     */
    private fun buildAccentBorder(thumbnailWidth: Int, widthFraction: Float, alpha: Int): GradientDrawable {
        val strokeWidth = (thumbnailWidth * widthFraction).coerceIn(
            activity.resources.getDimension(R.dimen.highlight_border_min_width),
            activity.resources.getDimension(R.dimen.highlight_border_max_width)
        )

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = getThumbnailCornerRadius()
            setStroke(strokeWidth.toInt(), properPrimaryColor)
            this.alpha = alpha
        }
    }

    private fun highlightItem(position: Int) {
        val thumbnail = recyclerView.findViewHolderForAdapterPosition(position)
            ?.itemView
            ?.findViewById<ImageView>(R.id.medium_thumbnail) ?: return

        highlightAnimator?.cancel()

        val border = buildAccentBorder(thumbnail.width, HIGHLIGHT_BORDER_WIDTH_FRACTION, alpha = 0)

        // setForeground makes the view the drawables callback, so changing the alpha repaints it
        thumbnail.foreground = border
        highlightAnimator = AnimatorSet().apply {
            val fadeIn = ObjectAnimator.ofInt(border, "alpha", 0, HIGHLIGHT_BORDER_OPAQUE_ALPHA)
                .setDuration(HIGHLIGHT_FADE_IN_DURATION_MS)

            val fadeOut = ObjectAnimator.ofInt(border, "alpha", HIGHLIGHT_BORDER_OPAQUE_ALPHA, 0)
                .setDuration(HIGHLIGHT_FADE_OUT_DURATION_MS)
                .apply { startDelay = HIGHLIGHT_HOLD_DURATION_MS }

            playSequentially(fadeIn, fadeOut)
            doOnEnd {
                thumbnail.foreground = null
                highlightAnimator = null
            }

            start()
        }
    }

    private fun getRoundedCorners() = when {
        isListViewType -> ROUNDED_CORNERS_SMALL
        config.fileRoundedCorners -> ROUNDED_CORNERS_BIG
        else -> ROUNDED_CORNERS_NONE
    }

    private fun getThumbnailCornerRadius(): Float {
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

    private fun isItemSelected(medium: Medium) = if (isReordering) {
        reorderSelection.contains(medium.path)
    } else {
        selectedKeys.contains(medium.path.hashCode())
    }

    /**
     * Repaints only the check rather than rebinding the item - a rebind would put the thumbnail
     * through another image request, and this runs on every tap.
     */
    private fun toggleReorderSelection(medium: Medium, holder: ViewHolder) {
        if (!reorderSelection.remove(medium.path)) {
            reorderSelection.add(medium.path)
        }

        bindItem(holder.itemView, medium).markSelected(isItemSelected(medium))
        notifyReorderState()
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

            activity.loadImage(
                type = medium.type,
                path = path,
                target = mediumThumbnail,
                horizontalScroll = scrollHorizontally,
                animateGifs = animateGifs,
                cropThumbnails = cropThumbnails,
                roundCorners = roundedCorners,
                signature = medium.getKey(),
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

    private fun setupSection(view: View, section: ThumbnailSection) {
        ThumbnailSectionBinding.bind(view).apply {
            thumbnailSection.text = section.title
            thumbnailSection.setTextColor(textColor)
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(media, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(media, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {
        swipeRefreshLayout?.isEnabled = false
        myViewHolder?.itemView?.liftForDrag()
    }

    override fun onRowClear(myViewHolder: ViewHolder?) {
        swipeRefreshLayout?.isEnabled = !isReordering && config.enablePullToRefresh
        myViewHolder?.itemView?.dropAfterDrag()
        dropCarriedItems()
        notifyReorderState()
    }

    /**
     * Picks up the item held down and, if it is one of several marked, every other marked item with
     * it. The others leave the grid for the length of the drag: the grid closes over the gaps they
     * leave and opens one where the group is headed, so what is on screen while dragging is what
     * the arrangement will look like rather than one item wandering through the old one.
     *
     * The group is taken out before the drag starts rather than after. ItemTouchHelper anchors the
     * dragged view to where it sat when it was picked up, so the item stays under the finger even
     * though the grid closes up underneath it.
     */
    private fun startDragging(medium: Medium, holder: ViewHolder) {
        draggedPath = medium.path
        carriedItems = if (reorderSelection.size > 1 && reorderSelection.contains(medium.path)) {
            media.filterIsInstance<Medium>().filter { reorderSelection.contains(it.path) }
        } else {
            emptyList()
        }

        carriedItems.filter { it.path != medium.path }.forEach { removeItem(it.path) }
        notifyReorderState()
        itemTouchHelper?.startDrag(holder)
    }

    /**
     * Lands the carried items around the one just dropped, keeping the order they were picked up in
     * - the drag says where the group goes, not how it is shuffled. Whatever was ahead of the
     * dragged item in the group goes directly in front of it, whatever was behind goes directly
     * after, so the dropped item keeps the place the finger left it in among the items that stayed.
     */
    private fun dropCarriedItems() {
        val carried = carriedItems
        val droppedPath = draggedPath
        carriedItems = emptyList()
        draggedPath = null
        if (carried.size < 2 || droppedPath == null) {
            return
        }

        val droppedIndex = indexOfPath(droppedPath)
        if (droppedIndex == -1) {
            return
        }

        val offset = carried.indexOfFirst { it.path == droppedPath }
        carried.take(offset).forEachIndexed { index, item ->
            insertItem(item, droppedIndex + index)
        }

        carried.drop(offset + 1).forEachIndexed { index, item ->
            insertItem(item, droppedIndex + offset + 1 + index)
        }

        // items landing in front of the dropped one push the grid down while it holds its scroll on
        // what it was showing, which can leave the group that just landed above the fold
        if (offset > 0) {
            recyclerView.post {
                val landedAt = indexOfPath(carried.first().path)
                if (landedAt != -1) {
                    scrollToItemIfNeeded(landedAt)
                }
            }
        }
    }

    private fun removeItem(path: String) {
        val position = indexOfPath(path)
        if (position != -1) {
            media.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    private fun insertItem(item: Medium, position: Int) {
        media.add(position, item)
        notifyItemInserted(position)
    }

    private fun indexOfPath(path: String) = media.indexOfFirst { (it as? Medium)?.path == path }

    private fun notifyReorderState() = onReorderStateChanged?.invoke(reorderSelection.size, carriedItems.size)

    /**
     * Pulls the picked up thumbnail out of the grid - smaller, ringed in the accent color and
     * casting a shadow into the gap that opens around it - so the moment the long press takes hold
     * and the item is free to be moved is unmistakable. A tap of feedback goes with it, the finger
     * is on the item and cannot see it.
     */
    private fun View.liftForDrag() {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        // ItemTouchHelper owns the elevation of whatever it drags, translationZ is ours to lift with
        outlineProvider = thumbnailOutlineProvider
        animateLift(DRAG_LIFT_SCALE, activity.resources.getDimension(R.dimen.drag_lift_elevation))
        showCarriedCount(carriedItems.size)

        findThumbnail()?.apply {
            foreground = buildAccentBorder(width, DRAG_BORDER_WIDTH_FRACTION, MAX_ALPHA_INT)
        }
    }

    private fun View.dropAfterDrag() {
        // the ring and the shadow go at once rather than when the item has settled - carrying a
        // group re-lays the grid out on the drop, and a reset waiting on an animation that a
        // re-layout can cut short would leave the ring painted on the thumbnail for good
        outlineProvider = ViewOutlineProvider.BACKGROUND
        findThumbnail()?.foreground = null
        hideCarriedCount()
        animateLift(1f, 0f)
    }

    /**
     * The rest of a group being carried is off the grid, so the one thumbnail on its way counts the
     * whole group in the badge the tick came from - one item on the move has nothing to count and
     * keeps its tick.
     */
    private fun View.showCarriedCount(count: Int) {
        if (count < 2) {
            return
        }

        findCountBadge()?.apply {
            text = count.toString()
            background?.applyColorFilter(properPrimaryColor)
            setTextColor(contrastColor)
            beVisible()
        }

        findCheck()?.beGone()
    }

    private fun View.hideCarriedCount() {
        val badge = findCountBadge() ?: return
        if (badge.isVisible) {
            badge.beGone()
            // only a marked item is ever carried in a group, so its tick is due back
            findCheck()?.beVisible()
        }
    }

    /**
     * Animators of our own rather than the view's animate() builder: the grid's item animator uses
     * that builder for the items it slides around and cancels whatever it finds on it, which would
     * strand a picked up thumbnail half lifted.
     */
    private fun View.animateLift(scale: Float, elevation: Float) {
        dragLiftAnimator?.cancel()
        dragLiftAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(this@animateLift, View.SCALE_X, scale),
                ObjectAnimator.ofFloat(this@animateLift, View.SCALE_Y, scale),
                ObjectAnimator.ofFloat(this@animateLift, View.TRANSLATION_Z, elevation)
            )
            duration = DRAG_LIFT_DURATION_MS
            start()
        }
    }

    private fun View.findThumbnail() = findViewById<ImageView>(R.id.medium_thumbnail)

    private fun View.findCountBadge() = findViewById<TextView>(R.id.medium_count)

    private fun View.findCheck() = findViewById<ImageView>(R.id.medium_check)

    /**
     * The item view carries the padding that spaces the grid out, so a shadow around it would sit
     * off the picture. This traces the thumbnail inside it instead, corners and all.
     */
    private val thumbnailOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(
                view.paddingLeft,
                view.paddingTop,
                view.width - view.paddingRight,
                view.height - view.paddingBottom,
                getThumbnailCornerRadius()
            )
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

/**
 * The dragged item takes the cell it covers most, which is the head of the list handed over here -
 * ItemTouchHelper has already narrowed it to the cells the item overlaps and sorted them by how far
 * their centre is from its own.
 *
 * The default rule instead asks which cells lie between where the item is being drawn and the cell
 * it currently occupies. Those are the same place while a lone item is dragged, but picking up a
 * group takes the rest of it out of the grid, which slides the held item's cell out from under the
 * finger - and then that rule can find nothing between the two and the item stops responding. Going
 * by what is under the item needs no such agreement, and it drops where it looks like it will.
 */
private class NearestCellMoveCallback(adapter: ItemTouchHelperContract) : ItemMoveCallback(adapter, true) {
    override fun chooseDropTarget(
        selected: RecyclerView.ViewHolder,
        dropTargets: MutableList<RecyclerView.ViewHolder>,
        curX: Int,
        curY: Int
    ) = dropTargets.firstOrNull() ?: super.chooseDropTarget(selected, dropTargets, curX, curY)
}
