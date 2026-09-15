package org.fossify.gallery.helpers

import android.content.res.Resources
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import org.fossify.commons.extensions.formatSize
import org.fossify.gallery.R
import kotlin.math.roundToInt

/** Where a style writes a folder's name and details: over the cover or under it. */
enum class FolderLabelPlacement { ON_COVER, BELOW }

private const val CARD_ASPECT_RATIO = 5f / 4f

/** The spacings a folder tile can keep around itself, in dp, closest first. */
@Suppress("MagicNumber")
val FOLDER_SPACING_STEPS = listOf(2, 4, 8, 12, 16)
const val DEFAULT_FOLDER_SPACING = 8

// the hairline of padding directory_item_grid_square.xml keeps around its cover
private const val SQUARE_COVER_INSET_PX = 2

private const val FOLDER_DETAILS_SEPARATOR = " · "

/**
 * The looks a folder tile can take, and everything about one the code has to know. The grid and the
 * style dialog's preview both read this, so a style is described in one place - its layout carries
 * the same ids as every other, see GridDirectoryItemBinding.
 */
enum class FolderCoverStyle(
    val id: Int,
    @StringRes val title: Int,
    @LayoutRes val layout: Int,
    /** How the cover's bitmap itself is cut, as one of the ROUNDED_CORNERS_ constants. */
    val bitmapCorners: Int,
    val label: FolderLabelPlacement
) {
    SQUARE(
        id = FOLDER_STYLE_SQUARE,
        title = R.string.square,
        layout = R.layout.directory_item_grid_square,
        bitmapCorners = ROUNDED_CORNERS_NONE,
        label = FolderLabelPlacement.ON_COVER
    ),

    ROUNDED(
        id = FOLDER_STYLE_ROUNDED_CORNERS,
        title = R.string.rounded_corners,
        layout = R.layout.directory_item_grid_rounded_corners,
        bitmapCorners = ROUNDED_CORNERS_BIG,
        label = FolderLabelPlacement.BELOW
    ),

    // rounded by the cover view's outline rather than in the bitmap: the frosted band is a blur of the
    // bitmap, and a transparent corner would bleed into it
    CARD(
        id = FOLDER_STYLE_CARD,
        title = R.string.folder_style_card,
        layout = R.layout.directory_item_grid_card,
        bitmapCorners = ROUNDED_CORNERS_NONE,
        label = FolderLabelPlacement.ON_COVER
    ),

    STACK(
        id = FOLDER_STYLE_STACK,
        title = R.string.folder_style_stack,
        layout = R.layout.directory_item_grid_stack,
        bitmapCorners = ROUNDED_CORNERS_BIG,
        label = FolderLabelPlacement.BELOW
    );

    /** The cover's height over its width. Has to agree with coverAspectRatio in the layout. */
    val aspectRatio get() = if (this == CARD) CARD_ASPECT_RATIO else 1f

    /** How round the cover's corners are drawn, for anything that has to trace one. */
    fun shapeRadius(resources: Resources): Float = when (this) {
        SQUARE -> 0f
        ROUNDED, CARD, STACK -> resources.getDimension(org.fossify.commons.R.dimen.rounded_corner_radius_big)
    }

    /** The margin a tile keeps around itself at [spacing] dp. Square tiles keep none, and meet edge to edge. */
    fun tileMargin(resources: Resources, spacing: Int): Int = when (this) {
        SQUARE -> 0
        ROUNDED, CARD, STACK -> (spacing * resources.displayMetrics.density).roundToInt()
    }

    /** How much narrower a cover is than the column it stands in, at [spacing] dp. */
    fun coverInset(resources: Resources, spacing: Int): Int = when (this) {
        SQUARE -> SQUARE_COVER_INSET_PX
        ROUNDED, CARD, STACK -> 2 * tileMargin(resources, spacing)
    }

    companion object {
        fun from(id: Int) = entries.firstOrNull { it.id == id } ?: SQUARE
    }
}

/**
 * The line under a folder's name: its file count and size, whichever are asked for. [count] is null
 * where the count is not to go on this line.
 */
fun folderDetailsLine(count: String?, size: Long, showSize: Boolean): String {
    // nothing on a folder weighs nothing: 0 is a size that has not been summed yet, see getProperFileSize
    val shownSize = if (showSize && size > 0) size.formatSize() else null
    return listOfNotNull(count, shownSize).joinToString(FOLDER_DETAILS_SEPARATOR)
}
