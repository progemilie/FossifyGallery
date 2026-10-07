package org.fossify.gallery.dialogs

import android.graphics.Color
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.ComponentDialog
import androidx.annotation.StringRes
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.isEmpty
import org.fossify.commons.R as commonsR
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.CreateNewFolderDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isAStorageRootFolder
import org.fossify.commons.extensions.setSystemBarsAppearance
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.fossify.commons.helpers.isPiePlus
import org.fossify.commons.helpers.isQPlus
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogDirectoryPickerBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.FloatingTopBar
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.helpers.LandscapeStatusBar
import org.fossify.gallery.views.GlassPanel
import org.fossify.gallery.views.NavPillSegment

/**
 * The folder picker as a screen of its own: a dialog filling the window edge to edge, the grid running
 * under glass pills the way the browsing grids do - back and a new folder along the top, the search
 * with the eye for hidden folders along the foot - and Other folder after the tiles.
 * [PickDirectoryDialog] fills the grid and answers back; [FolderPickerLayout] lays it all out.
 *
 * [newFolderBeside] is the folder the files come from, which a new folder is made next to; null where
 * the picker is not choosing somewhere to put files. [fileCount] is how many files are being put there,
 * 0 where that is not what the picker is for.
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
    private val layout = FolderPickerLayout(binding)
    private val searchBar = FloatingTopBar(binding.folderSearchView, binding.directoriesContent)
    private var isShowingHidden = false

    // shown hidden folders everywhere already leave nothing for the eye to do
    private val offersHiddenToggle = !activity.config.shouldShowHidden

    val dialog = ComponentDialog(activity, R.style.FullscreenDialog)

    init {
        searchBar.onHeightChanged = layout::keepGridClear
        setupTopBar(titleId, fileCount)
        if (showOtherFolder) {
            setupOtherFolder()
        }

        dialog.setContentView(binding.root)
        dialog.window?.fillScreen(activity.getProperBackgroundColor())
        LandscapeStatusBar.follow(dialog, activity)
        if (!activity.isDestroyed && !activity.isFinishing) {
            dialog.show()
        }
    }

    fun dismiss() = dialog.dismiss()

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
            activity.getString(R.string.folder_picker_title_with_count, title, fileCount)
        } else {
            title
        }

        directoriesTitle.setTextColor(textColor)
        directoriesEmptyPlaceholder.setTextColor(textColor)

        dressPill(directoriesBackPanel, directoriesBack, directoriesBackIcon)
        directoriesBack.setOnClickListener { dialog.onBackPressedDispatcher.onBackPressed() }

        if (newFolderBeside != null) {
            directoriesNewFolderPanel.beVisible()
            dressPill(directoriesNewFolderPanel, directoriesNewFolder, directoriesNewFolderIcon)
            directoriesNewFolder.setOnClickListener { createNewFolder(newFolderBeside) }
        }
    }

    private fun dressPill(panel: GlassPanel, segment: NavPillSegment, icon: ImageView) {
        val content = Glass.contentColor(activity)
        panel.dressAsFloatingPill(activity.resources.getDimension(R.dimen.peek_pill_radius))
        panel.frost(binding.directoriesContent)
        segment.paintWash(content, isCurrent = false)
        icon.applyColorFilter(content)
        TooltipCompat.setTooltipText(segment, segment.contentDescription)
    }

    private fun setupOtherFolder() = with(binding.directoriesOtherFolder) {
        beVisible()
        setTextColor(activity.getProperPrimaryColor())
        setOnClickListener {
            activity.hideKeyboard(binding.folderSearchView.binding.topToolbarSearch)
            onOtherFolder()
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

    // the icon shows what a tap does next, so it is the open eye while hidden folders are kept out
    private fun paintHiddenToggle() {
        val item = binding.folderSearchView.requireToolbar().menu.findItem(R.id.toggle_hidden_folders) ?: return
        item.setIcon(if (isShowingHidden) commonsR.drawable.ic_hide_vector else commonsR.drawable.ic_unhide_vector)
        item.icon?.applyColorFilter(if (isShowingHidden) textColor.adjustAlpha(MEDIUM_ALPHA) else textColor)
        item.title = activity.getString(
            if (isShowingHidden) commonsR.string.stop_showing_hidden else commonsR.string.show_hidden_items
        )
    }

    private fun createNewFolder(source: String) {
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

    setSystemBarsAppearance(background)
}
