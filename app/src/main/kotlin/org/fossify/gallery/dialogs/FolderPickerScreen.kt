package org.fossify.gallery.dialogs

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.ComponentDialog
import androidx.annotation.StringRes
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isEmpty
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.R as commonsR
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.CreateNewFolderDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isAStorageRootFolder
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.fossify.commons.helpers.isPiePlus
import org.fossify.commons.helpers.isQPlus
import org.fossify.gallery.R
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.adapters.NewFolderTileAdapter
import org.fossify.gallery.adapters.OtherFolderAdapter
import org.fossify.gallery.databinding.DialogDirectoryPickerBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.FloatingTopBar
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.views.GlassPanel
import org.fossify.gallery.views.NavPillSegment

private const val LIGHT_LUMINANCE = 0.5

// the file count in brackets after the title, a step down from it
private const val TITLE_COUNT_SCALE = 0.8f

/**
 * The folder picker as a screen of its own: a dialog filling the window edge to edge, the grid running
 * under glass pills the way the browsing grids do - back and a new folder along the top, the search,
 * with the eye that shows hidden folders and hides them again, along the foot - and the title naming
 * what the folder is for. Past the folder tiles come the new folder tile and Other folder.
 * [PickDirectoryDialog] fills the grid and answers back; [FolderPickerLayout] lays it all out.
 *
 * [newFolderBeside] is the folder the files come from, which a new folder is made next to; null where
 * the picker is not choosing somewhere to put files, and has no use for one. [fileCount] is how many
 * files are being put there, 0 where that is not what the picker is for.
 */
class FolderPickerScreen(
    private val activity: BaseSimpleActivity,
    private val binding: DialogDirectoryPickerBinding,
    @StringRes titleId: Int,
    fileCount: Int,
    showOtherFolder: Boolean,
    private val newFolderBeside: String?,
) {
    var onOtherFolder: () -> Unit = {}
    var onShowHiddenChanged: (showHidden: Boolean) -> Unit = {}
    var onFolderCreated: (path: String) -> Unit = {}

    private val textColor = activity.getProperTextColor()
    private val backgroundColor = activity.getProperBackgroundColor()
    private val searchBar = FloatingTopBar(binding.folderSearchView, binding.directoriesContent)
    private var isShowingHidden = false

    // shown hidden folders everywhere already leave nothing for the eye to do
    private val offersHiddenToggle = !activity.config.shouldShowHidden

    val dialog = ComponentDialog(activity, R.style.FullscreenDialog)

    /** The tile after the folders that makes a new one, where the picker has a use for one. */
    val newFolderTile = newFolderBeside?.let { NewFolderTileAdapter(activity, textColor, ::createNewFolder) }

    private val otherFolderButton = if (showOtherFolder) {
        OtherFolderAdapter(activity, activity.getProperPrimaryColor()) {
            activity.hideKeyboard(binding.folderSearchView.binding.topToolbarSearch)
            onOtherFolder()
        }
    } else {
        null
    }

    /** Whether the grid has something to show past the folders, which it shows even without any folders. */
    val hasExtraTiles get() = newFolderTile != null || otherFolderButton != null

    init {
        FolderPickerLayout(binding)
        setupTopBar(titleId, fileCount)
        spanOtherFolderAcrossGrid()

        dialog.setContentView(binding.root)
        dialog.window?.fillScreen(backgroundColor)
        if (!activity.isDestroyed && !activity.isFinishing) {
            dialog.show()
        }
    }

    fun dismiss() = dialog.dismiss()

    /** What the grid shows: [folders], followed by whichever of the new folder tile and Other folder the picker has. */
    fun gridAdapter(folders: DirectoryAdapter?): RecyclerView.Adapter<*> {
        val parts = listOfNotNull<RecyclerView.Adapter<out RecyclerView.ViewHolder>>(
            folders,
            newFolderTile,
            otherFolderButton
        )

        return parts.singleOrNull() ?: ConcatAdapter(parts)
    }

    /**
     * Frosts the search pill the way the browsing screens' is, and puts the eye at its end. Commons
     * paints the pill and tints its menu in [org.fossify.commons.views.MySearchMenu.updateColors], so
     * all of this follows that.
     */
    fun dressSearchBar() = with(binding.folderSearchView) {
        val toolbar = requireToolbar()
        if (offersHiddenToggle && toolbar.menu.isEmpty()) {
            toolbar.inflateMenu(R.menu.menu_pick_directory)
            toolbar.setOnMenuItemClickListener {
                toggleHidden()
                true
            }
        }

        toolbar.beVisibleIf(offersHiddenToggle)
        updateColors()
        searchBar.makeFloating()
        paintHiddenToggle()
    }

    private fun setupTopBar(@StringRes titleId: Int, fileCount: Int) = with(binding) {
        val title = activity.getString(titleId)
        directoriesTitle.text = if (fileCount > 0) {
            val text = activity.getString(R.string.folder_picker_title_with_count, title, fileCount)
            SpannableString(text).apply {
                val count = RelativeSizeSpan(TITLE_COUNT_SCALE)
                setSpan(count, title.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            title
        }

        directoriesTitle.setTextColor(textColor)
        directoriesEmptyPlaceholder.setTextColor(textColor)

        dressPill(directoriesBackPanel, directoriesBack, directoriesBackIcon)
        directoriesBack.setOnClickListener { dialog.onBackPressedDispatcher.onBackPressed() }

        directoriesNewFolderPanel.beVisibleIf(newFolderTile != null)
        dressPill(directoriesNewFolderPanel, directoriesNewFolder, directoriesNewFolderIcon)
        directoriesNewFolder.setOnClickListener { createNewFolder() }
    }

    // round, the selection pill's glass and its back button's look
    private fun dressPill(panel: GlassPanel, segment: NavPillSegment, icon: ImageView) {
        val content = Glass.contentColor(activity)
        panel.dressAsFloatingPill(activity.resources.getDimension(R.dimen.peek_pill_radius))
        panel.frost(binding.directoriesContent)
        segment.paintWash(content, isCurrent = false)
        icon.applyColorFilter(content)
        TooltipCompat.setTooltipText(segment, segment.contentDescription)
    }

    // Other folder takes a row of its own under the tiles, and a column of its own after them sideways
    private fun spanOtherFolderAcrossGrid() {
        val layoutManager = binding.directoriesGrid.layoutManager as GridLayoutManager
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val isOtherFolder = otherFolderButton != null && position == layoutManager.itemCount - 1
                return if (isOtherFolder) layoutManager.spanCount else 1
            }
        }
    }

    private fun toggleHidden() {
        if (isShowingHidden) {
            showHidden(false)
        } else {
            activity.handleHiddenFolderPasswordProtection { showHidden(true) }
        }
    }

    private fun showHidden(show: Boolean) {
        isShowingHidden = show
        paintHiddenToggle()
        onShowHiddenChanged(show)
    }

    // the eye open while hidden folders are kept out, as the way to bring them in; shut, and dimmed to
    // the search hint's grey, once they are
    private fun paintHiddenToggle() {
        val item = binding.folderSearchView.requireToolbar().menu.findItem(R.id.toggle_hidden_folders) ?: return
        item.setIcon(if (isShowingHidden) commonsR.drawable.ic_hide_vector else commonsR.drawable.ic_unhide_vector)
        item.icon?.applyColorFilter(if (isShowingHidden) textColor.adjustAlpha(MEDIUM_ALPHA) else textColor)
        item.title = activity.getString(
            if (isShowingHidden) commonsR.string.stop_showing_hidden else commonsR.string.show_hidden_items
        )
    }

    private fun createNewFolder() {
        val source = newFolderBeside ?: return
        // a storage's root has nothing beside it, so the folder goes inside it instead
        val parent = if (activity.isAStorageRootFolder(source)) source else source.getParentPath()
        activity.hideKeyboard(binding.folderSearchView.binding.topToolbarSearch)
        CreateNewFolderDialog(activity, parent) { path ->
            dismiss()
            onFolderCreated(path)
        }
    }
}

private fun Window.fillScreen(background: Int) {
    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    setBackgroundDrawable(background.toDrawable())
    setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    WindowCompat.setDecorFitsSystemWindows(this, false)
    statusBarColor = Color.TRANSPARENT
    navigationBarColor = Color.TRANSPARENT
    if (isQPlus()) {
        isNavigationBarContrastEnforced = false
    }

    if (isPiePlus()) {
        attributes = attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    val isLight = ColorUtils.calculateLuminance(background) > LIGHT_LUMINANCE
    WindowInsetsControllerCompat(this, decorView).apply {
        isAppearanceLightStatusBars = isLight
        isAppearanceLightNavigationBars = isLight
    }
}
