package org.fossify.gallery.dialogs

import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.adapters.GridDirectoryItemBinding
import org.fossify.gallery.adapters.dressFor
import org.fossify.gallery.databinding.DialogChangeFolderThumbnailStyleBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.coverCornersTransformation
import org.fossify.gallery.helpers.FOLDER_MEDIA_CNT_BRACKETS
import org.fossify.gallery.helpers.FOLDER_MEDIA_CNT_LINE
import org.fossify.gallery.helpers.FOLDER_MEDIA_CNT_NONE
import org.fossify.gallery.helpers.FolderCoverStyle
import org.fossify.gallery.helpers.FolderLabelPlacement
import org.fossify.gallery.helpers.folderDetailsLine
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class ChangeFolderThumbnailStyleDialog(val activity: BaseSimpleActivity, val callback: () -> Unit) : DialogInterface.OnClickListener {
    private val config = activity.config
    private val binding = DialogChangeFolderThumbnailStyleBinding.inflate(activity.layoutInflater)

    private val styleButtons: Map<FolderCoverStyle, RadioButton> = binding.run {
        mapOf(
            FolderCoverStyle.SQUARE to dialogRadioFolderSquare,
            FolderCoverStyle.ROUNDED to dialogRadioFolderRoundedCorners,
            FolderCoverStyle.CARD to dialogRadioFolderCard,
            FolderCoverStyle.STACK to dialogRadioFolderStack
        )
    }

    // the illustrations are drawn once, rather than every time an option changes
    private val sampleCovers = HashMap<Int, Bitmap>()

    // the preview is held at the height of the tallest style showing every detail, so switching styles
    // or options never resizes the dialog. Declared ahead of init, which builds the first preview
    private val sampleHeight by lazy {
        val holder = binding.dialogFolderSampleHolder
        val fullest = SampleOptions(FOLDER_MEDIA_CNT_LINE, showSize = true, showDate = true, limitTitle = false)
        val tallest = FolderCoverStyle.entries.maxOf { style ->
            SAMPLE_FOLDERS.maxOf { measuredHeight(sampleTile(style, it, fullest, holder).root) }
        }

        tallest + holder.paddingTop + holder.paddingBottom
    }

    init {
        binding.apply {
            dialogFolderShowSize.isChecked = config.showFolderSize
            dialogFolderShowDate.isChecked = config.showFolderDate
            dialogFolderLimitTitle.isChecked = config.limitFolderTitle
            listOf(dialogFolderShowSize, dialogFolderShowDate, dialogFolderLimitTitle).forEach {
                it.setOnCheckedChangeListener { _, _ -> updateSample() }
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, this)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) {
                    setupStyle()
                    setupMediaCount()
                    updateSample()
                }
            }
    }

    private fun setupStyle() {
        styleButtons.getValue(FolderCoverStyle.from(config.folderStyle)).isChecked = true
        binding.dialogRadioFolderStyle.setOnCheckedChangeListener { _, _ -> updateSample() }
    }

    private fun setupMediaCount() {
        val countBtn = when (config.showFolderMediaCount) {
            FOLDER_MEDIA_CNT_LINE -> binding.dialogRadioFolderCountLine
            FOLDER_MEDIA_CNT_BRACKETS -> binding.dialogRadioFolderCountBrackets
            else -> binding.dialogRadioFolderCountNone
        }

        countBtn.isChecked = true
        binding.dialogRadioFolderCountHolder.setOnCheckedChangeListener { _, _ -> updateSample() }
    }

    private fun selectedStyle() =
        styleButtons.entries.firstOrNull { it.value.isChecked }?.key ?: FolderCoverStyle.SQUARE

    private fun selectedCount() = when (binding.dialogRadioFolderCountHolder.checkedRadioButtonId) {
        R.id.dialog_radio_folder_count_line -> FOLDER_MEDIA_CNT_LINE
        R.id.dialog_radio_folder_count_brackets -> FOLDER_MEDIA_CNT_BRACKETS
        else -> FOLDER_MEDIA_CNT_NONE
    }

    private fun selectedOptions() = SampleOptions(
        countMode = selectedCount(),
        showSize = binding.dialogFolderShowSize.isChecked,
        showDate = binding.dialogFolderShowDate.isChecked,
        limitTitle = binding.dialogFolderLimitTitle.isChecked
    )

    /** Two made up folders in the chosen style and options, built from the layouts the grid uses. */
    private fun updateSample() {
        val holder = binding.dialogFolderSampleHolder
        val style = selectedStyle()
        val options = selectedOptions()
        val transformation: Transformation<Bitmap> = activity.coverCornersTransformation(style.bitmapCorners)
            ?.let { MultiTransformation(CenterCrop(), it) }
            ?: CenterCrop()

        holder.minimumHeight = sampleHeight
        holder.removeAllViews()
        SAMPLE_FOLDERS.forEach { sample ->
            sampleTile(style, sample, options, holder).apply {
                dressFor(style, activity.getProperTextColor())
                Glide.with(activity)
                    .load(sampleCover(sample.cover))
                    .apply(RequestOptions.bitmapTransform(transformation))
                    .into(dirThumbnail)

                holder.addView(root)
            }
        }
    }

    /** A sample tile with its name and details written, not yet coloured or given a cover. */
    private fun sampleTile(
        style: FolderCoverStyle,
        sample: SampleFolder,
        options: SampleOptions,
        holder: ViewGroup
    ): GridDirectoryItemBinding {
        val tile = activity.layoutInflater.inflate(style.layout, holder, false) as ViewGroup
        tile.layoutParams.width = tile.resources.getDimensionPixelSize(R.dimen.folder_style_sample_width)

        // a tile named over its cover is as tall as the cover, and is told so: the square layout pins
        // its name to the tile's bottom, which a holder measuring at most would stretch to its full height
        if (style.label == FolderLabelPlacement.ON_COVER) {
            tile.layoutParams.height = (tile.layoutParams.width * style.aspectRatio).roundToInt()
        }

        return GridDirectoryItemBinding(tile).apply {
            dirName.text = when (options.countMode) {
                FOLDER_MEDIA_CNT_BRACKETS -> "${sample.name} (${sample.count})"
                else -> sample.name
            }
            if (options.limitTitle) {
                dirName.setSingleLine()
                dirName.ellipsize = TextUtils.TruncateAt.MIDDLE
            }

            photoCnt.text = activity.folderDetailsLine(
                count = sample.count.toString().takeIf { options.countMode == FOLDER_MEDIA_CNT_LINE },
                size = sample.size,
                date = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(sample.daysAgo),
                showSize = options.showSize,
                showDate = options.showDate
            )
            photoCnt.beVisibleIf(options.countMode == FOLDER_MEDIA_CNT_LINE || options.showSize || options.showDate)
        }
    }

    /** How much of the holder's height [tile] takes, margins included. */
    private fun measuredHeight(tile: View): Int {
        val params = tile.layoutParams as ViewGroup.MarginLayoutParams
        val heightSpec = if (params.height > 0) {
            View.MeasureSpec.makeMeasureSpec(params.height, View.MeasureSpec.EXACTLY)
        } else {
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        }

        tile.measure(View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY), heightSpec)
        return tile.measuredHeight + params.topMargin + params.bottomMargin
    }

    private fun sampleCover(@DrawableRes id: Int) = sampleCovers.getOrPut(id) {
        val drawable = checkNotNull(AppCompatResources.getDrawable(activity, id))
        Bitmap.createBitmap(SAMPLE_COVER_WIDTH, SAMPLE_COVER_HEIGHT, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, SAMPLE_COVER_WIDTH, SAMPLE_COVER_HEIGHT)
            drawable.draw(Canvas(it))
        }
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        config.folderStyle = selectedStyle().id
        config.showFolderMediaCount = selectedCount()
        config.showFolderSize = binding.dialogFolderShowSize.isChecked
        config.showFolderDate = binding.dialogFolderShowDate.isChecked
        config.limitFolderTitle = binding.dialogFolderLimitTitle.isChecked
        callback()
    }

    private class SampleFolder(
        val name: String,
        val count: Int,
        val size: Long,
        val daysAgo: Long,
        @DrawableRes val cover: Int
    )

    private data class SampleOptions(
        val countMode: Int,
        val showSize: Boolean,
        val showDate: Boolean,
        val limitTitle: Boolean
    )

    private companion object {
        const val SAMPLE_COVER_WIDTH = 360
        const val SAMPLE_COVER_HEIGHT = 480

        // one dated this year and one before it, so the date option shows both of its forms
        val SAMPLE_FOLDERS = listOf(
            SampleFolder("Camera", 36, size = 214_000_000L, daysAgo = 3, cover = R.drawable.sample_cover_mountains),
            SampleFolder("Holidays", 128, size = 1_340_000_000L, daysAgo = 400, cover = R.drawable.sample_cover_coast)
        )
    }
}
