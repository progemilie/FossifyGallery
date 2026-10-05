package org.fossify.gallery.dialogs

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.annotation.StringRes
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import org.fossify.commons.R as commonsR
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.CreateNewFolderDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isAStorageRootFolder
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.helpers.isPiePlus
import org.fossify.commons.helpers.isQPlus
import org.fossify.gallery.R
import org.fossify.gallery.adapters.NewFolderTileAdapter
import org.fossify.gallery.databinding.DialogDirectoryPickerBinding
import org.fossify.gallery.extensions.config

private const val LIGHT_LUMINANCE = 0.5

private val PADDED_INSETS = WindowInsetsCompat.Type.systemBars() or
    WindowInsetsCompat.Type.displayCutout() or
    WindowInsetsCompat.Type.ime()

// how far the eye's fill is carried from the page towards the text colour once it is switched off
private const val HIDDEN_TOGGLE_OFF_FILL = 0.12f

/**
 * The folder picker as a screen of its own: a dialog filling the window edge to edge, its toolbar
 * naming what the folder is for, and around the grid every way out of it that is not a folder tile -
 * Other folder, making a new folder, and the eye that shows hidden folders and hides them again.
 * [PickDirectoryDialog] fills the grid and answers back.
 *
 * [newFolderBeside] is the folder the files come from, which a new folder is made next to; null where
 * the picker is not choosing somewhere to put files, and has no use for one.
 */
class FolderPickerScreen(
    private val activity: BaseSimpleActivity,
    private val binding: DialogDirectoryPickerBinding,
    @StringRes titleId: Int,
    showOtherFolder: Boolean,
    private val newFolderBeside: String?,
) {
    var onOtherFolder: () -> Unit = {}
    var onShowHiddenChanged: (showHidden: Boolean) -> Unit = {}
    var onFolderCreated: (path: String) -> Unit = {}

    private val textColor = activity.getProperTextColor()
    private val primaryColor = activity.getProperPrimaryColor()
    private val backgroundColor = activity.getProperBackgroundColor()
    private val bottomBarPadding = binding.directoriesBottomBar.paddingBottom
    private var isShowingHidden = false

    val dialog = ComponentDialog(activity, R.style.FullscreenDialog)

    /** The tile after the folders that makes a new one, where the picker has a use for one. */
    val newFolderTile = newFolderBeside?.let { NewFolderTileAdapter(activity, textColor, ::createNewFolder) }

    init {
        setupToolbar(titleId)
        setupBottomBar(showOtherFolder)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyInsets(insets.getInsets(PADDED_INSETS))
            WindowInsetsCompat.CONSUMED
        }

        dialog.setContentView(binding.root)
        dialog.window?.fillScreen(backgroundColor)
        if (!activity.isDestroyed && !activity.isFinishing) {
            dialog.show()
        }
    }

    fun dismiss() = dialog.dismiss()

    private fun setupToolbar(@StringRes titleId: Int) = binding.directoriesToolbar.apply {
        title = activity.getString(titleId)
        setTitleTextColor(textColor)
        navigationIcon?.applyColorFilter(textColor)
        setNavigationOnClickListener { dialog.onBackPressedDispatcher.onBackPressed() }
        if (newFolderTile != null) {
            inflateMenu(R.menu.menu_pick_directory)
            menu.findItem(R.id.create_new_folder).icon?.applyColorFilter(textColor)
            setOnMenuItemClickListener {
                createNewFolder()
                true
            }
        }
    }

    private fun setupBottomBar(showOtherFolder: Boolean) = with(binding) {
        directoriesOtherFolder.beVisibleIf(showOtherFolder)
        directoriesOtherFolder.setTextColor(primaryColor)
        directoriesOtherFolder.setOnClickListener {
            activity.hideKeyboard(folderSearchView.binding.topToolbarSearch)
            onOtherFolder()
        }

        // shown hidden folders everywhere already leave nothing for it to do
        directoriesShowHidden.beVisibleIf(!activity.config.shouldShowHidden)
        directoriesShowHidden.setOnClickListener {
            if (isShowingHidden) {
                showHidden(false)
            } else {
                activity.handleHiddenFolderPasswordProtection { showHidden(true) }
            }
        }

        paintHiddenToggle()
        directoriesBottomBar.beVisibleIf(showOtherFolder || directoriesShowHidden.isVisible())
    }

    private fun showHidden(show: Boolean) {
        isShowingHidden = show
        paintHiddenToggle()
        onShowHiddenChanged(show)
    }

    // lit while hidden folders are kept out, as the way to bring them in; plain, with the eye shut, once they are
    private fun paintHiddenToggle() = binding.directoriesShowHidden.apply {
        val fill = if (isShowingHidden) {
            ColorUtils.compositeColors(textColor.adjustAlpha(HIDDEN_TOGGLE_OFF_FILL), backgroundColor)
        } else {
            primaryColor
        }

        backgroundTintList = ColorStateList.valueOf(fill)
        setImageResource(if (isShowingHidden) commonsR.drawable.ic_hide_vector else commonsR.drawable.ic_unhide_vector)
        applyColorFilter(if (isShowingHidden) textColor else primaryColor.getContrastColor())

        val description = activity.getString(
            if (isShowingHidden) commonsR.string.stop_showing_hidden else commonsR.string.show_hidden_items
        )

        contentDescription = description
        TooltipCompat.setTooltipText(this, description)
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

    // the grid scrolls on under the navigation bar wherever the bottom row is not there to take it
    private fun applyInsets(insets: Insets) = with(binding) {
        root.setPadding(insets.left, insets.top, insets.right, 0)
        if (directoriesBottomBar.isVisible()) {
            directoriesBottomBar.updatePadding(bottom = bottomBarPadding + insets.bottom)
        } else {
            directoriesGrid.updatePadding(bottom = insets.bottom)
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
