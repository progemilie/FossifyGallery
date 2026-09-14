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

    /** Two made up folders in the chosen style and options, built from the layouts the grid uses. */
    private fun updateSample() {
        val holder = binding.dialogFolderSampleHolder
        val style = selectedStyle()
        holder.removeAllViews()
        SAMPLE_FOLDERS.forEach { holder.addView(sampleTile(style, it, holder)) }
    }

    private fun sampleTile(style: FolderCoverStyle, sample: SampleFolder, holder: ViewGroup): View {
        val tile = activity.layoutInflater.inflate(style.layout, holder, false) as ViewGroup
        tile.layoutParams.width = tile.resources.getDimensionPixelSize(R.dimen.folder_style_sample_width)

        // a tile named over its cover is as tall as the cover, and is told so: the square layout pins
        // its name to the tile's bottom, which a holder measuring at most would stretch to its full height
        if (style.label == FolderLabelPlacement.ON_COVER) {
            tile.layoutParams.height = (tile.layoutParams.width * style.aspectRatio).roundToInt()
        }

        val countMode = selectedCount()
        val showSize = binding.dialogFolderShowSize.isChecked
        val showDate = binding.dialogFolderShowDate.isChecked
        GridDirectoryItemBinding(tile).apply {
            dirName.text = when (countMode) {
                FOLDER_MEDIA_CNT_BRACKETS -> "${sample.name} (${sample.count})"
                else -> sample.name
            }
            if (binding.dialogFolderLimitTitle.isChecked) {
                dirName.setSingleLine()
                dirName.ellipsize = TextUtils.TruncateAt.MIDDLE
            }

            photoCnt.text = activity.folderDetailsLine(
                count = sample.count.toString().takeIf { countMode == FOLDER_MEDIA_CNT_LINE },
                size = sample.size,
                date = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(sample.daysAgo),
                showSize = showSize,
                showDate = showDate
            )
            photoCnt.beVisibleIf(countMode == FOLDER_MEDIA_CNT_LINE || showSize || showDate)
            dressFor(style, activity.getProperTextColor())

            val transformation: Transformation<Bitmap> = activity.coverCornersTransformation(style.bitmapCorners)
                ?.let { MultiTransformation(CenterCrop(), it) }
                ?: CenterCrop()
            Glide.with(activity)
                .load(sampleCover(sample.cover))
                .apply(RequestOptions.bitmapTransform(transformation))
                .into(dirThumbnail)
        }

        return tile
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
