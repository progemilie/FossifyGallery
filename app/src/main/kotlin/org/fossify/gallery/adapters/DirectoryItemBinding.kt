package org.fossify.gallery.adapters

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.views.MySquareImageView
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DirectoryItemListBinding
import org.fossify.gallery.helpers.FolderCoverStyle
import org.fossify.gallery.helpers.FolderLabelPlacement
import org.fossify.gallery.views.FolderGroupThumbnail

interface DirectoryItemBinding {
    val root: ViewGroup
    val dirThumbnail: MySquareImageView
    val dirGroupThumbnail: FolderGroupThumbnail
    val dirGroupBadge: ImageView
    val dirPath: TextView?
    val dirCheck: ImageView
    val dirHolder: ViewGroup
    val photoCnt: TextView
    val dirName: TextView
    val dirLock: ImageView
    val dirPin: ImageView
    val dirLocation: ImageView
    val dirDragHandle: ImageView
    val dirDragHandleWrapper: ViewGroup?
    val dirCoverBorder: View?
}

class ListDirectoryItemBinding(val binding: DirectoryItemListBinding) : DirectoryItemBinding {
    override val root: ViewGroup = binding.root
    override val dirThumbnail: MySquareImageView = binding.dirThumbnail
    override val dirGroupThumbnail: FolderGroupThumbnail = binding.dirGroupThumbnail
    override val dirGroupBadge: ImageView = binding.dirGroupBadge
    override val dirPath: TextView = binding.dirPath
    override val dirCheck: ImageView = binding.dirCheck
    override val dirHolder: ViewGroup = binding.dirHolder
    override val photoCnt: TextView = binding.photoCnt
    override val dirName: TextView = binding.dirName
    override val dirLock: ImageView = binding.dirLock
    override val dirPin: ImageView = binding.dirPin
    override val dirLocation: ImageView = binding.dirLocation
    override val dirDragHandle: ImageView = binding.dirDragHandle
    override val dirDragHandleWrapper: ViewGroup? = null
    override val dirCoverBorder: View? = null
}

fun DirectoryItemListBinding.toItemBinding() = ListDirectoryItemBinding(this)

/**
 * A grid tile in any of the [FolderCoverStyle] layouts. They all carry the same ids, so one lookup
 * serves every style rather than a generated binding apiece.
 */
class GridDirectoryItemBinding(override val root: ViewGroup) : DirectoryItemBinding {
    override val dirThumbnail: MySquareImageView = root.findViewById(R.id.dir_thumbnail)
    override val dirGroupThumbnail: FolderGroupThumbnail = root.findViewById(R.id.dir_group_thumbnail)
    override val dirGroupBadge: ImageView = root.findViewById(R.id.dir_group_badge)
    override val dirPath: TextView? = null
    override val dirCheck: ImageView = root.findViewById(R.id.dir_check)
    override val dirHolder: ViewGroup = root
    override val photoCnt: TextView = root.findViewById(R.id.photo_cnt)
    override val dirName: TextView = root.findViewById(R.id.dir_name)
    override val dirLock: ImageView = root.findViewById(R.id.dir_lock)
    override val dirPin: ImageView = root.findViewById(R.id.dir_pin)
    override val dirLocation: ImageView = root.findViewById(R.id.dir_location)
    override val dirDragHandle: ImageView = root.findViewById(R.id.dir_drag_handle)
    override val dirDragHandleWrapper: ViewGroup = root.findViewById(R.id.dir_drag_handle_wrapper)
    override val dirCoverBorder: View? = root.findViewById(R.id.dir_cover_border)
}

// how far the stack's cards are carried from the background towards the text colour
private const val STACK_MIDDLE_CARD_SHADE = 0.3f
private const val STACK_BACK_CARD_SHADE = 0.15f

// the text colour always stands out from the theme's background, so a faint wash of it edges a cover
private const val COVER_BORDER_ALPHA = 0x40

/** Colours what a tile's style leaves to the theme. Text on a cover keeps its own. */
fun DirectoryItemBinding.dressFor(style: FolderCoverStyle, textColor: Int) {
    if (style.label == FolderLabelPlacement.BELOW) {
        dirName.setTextColor(textColor)
        photoCnt.setTextColor(textColor)
        dirLocation.applyColorFilter(textColor)
    }

    dirCoverBorder?.backgroundTintList =
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(textColor, COVER_BORDER_ALPHA))

    if (style == FolderCoverStyle.STACK) {
        // opaque rather than see-through, or the back card would show through the middle one
        val background = root.context.getProperBackgroundColor()
        root.findViewById<View>(R.id.dir_stack_middle).backgroundTintList =
            ColorStateList.valueOf(ColorUtils.blendARGB(background, textColor, STACK_MIDDLE_CARD_SHADE))
        root.findViewById<View>(R.id.dir_stack_back).backgroundTintList =
            ColorStateList.valueOf(ColorUtils.blendARGB(background, textColor, STACK_BACK_CARD_SHADE))
    }
}
