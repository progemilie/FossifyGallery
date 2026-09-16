package org.fossify.gallery.dialogs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.onSeekBarChangeListener
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.models.RadioItem
import org.fossify.gallery.R
import org.fossify.gallery.adapters.GridDirectoryItemBinding
import org.fossify.gallery.adapters.dressFor
import org.fossify.gallery.databinding.DialogOutlineLabBinding
import org.fossify.gallery.databinding.DialogOutlineLabSliderBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.coverCornersTransformation
import org.fossify.gallery.helpers.DEFAULT_FOLDER_SPACING
import org.fossify.gallery.helpers.FolderCoverStyle
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.helpers.OutlineParams
import org.fossify.gallery.helpers.OutlineProfile
import org.fossify.gallery.helpers.OutlineSettings
import org.fossify.gallery.helpers.OutlineStyle
import java.text.DecimalFormat

private const val PERCENT = 100
private const val QUARTERS_PER_DP = 4
private const val MAX_WIDTH_QUARTERS = 24
private const val MAX_SIZE_DP = 32
private const val DISABLED_ALPHA = 0.35f
private const val SAMPLE_COVER_WIDTH = 360
private const val SAMPLE_COVER_HEIGHT = 480

/**
 * TEMPORARY - the outline lab, see OutlineStyle. Every style tried on the reorder mode's Save over a
 * photo and on a cover in each outlined folder style at once, redrawn as the sliders move. The pill and
 * the covers are set apart, or the covers told to follow the pill. Nothing is kept until OK.
 */
class OutlineLabDialog(private val activity: BaseSimpleActivity, private val callback: () -> Unit) {
    private val binding = DialogOutlineLabBinding.inflate(activity.layoutInflater)
    private val pill = OutlineSettings.pill(activity)
    private val cover = OutlineSettings.cover(activity)
    private var coversMatchPill = activity.config.outlineCoversMatchPill
    private var editingCovers = false

    // set while the controls are being filled in, when a slider moving is not the user moving it
    private var isRefreshing = false

    private val sliders = listOf(
        slider(R.string.outline_tint, PERCENT, OutlineParams::tint, activity::percent, { it != OutlineStyle.NONE }) {
            copy(tint = it)
        },
        slider(
            R.string.outline_lightness, PERCENT, OutlineParams::lightness, activity::signedPercent,
            isUsedBy = { it != OutlineStyle.NONE }
        ) { copy(lightness = it) },
        slider(R.string.outline_opacity, PERCENT, OutlineParams::opacity, activity::percent, { it.usesLine }) {
            copy(opacity = it)
        },
        slider(
            R.string.outline_width, MAX_WIDTH_QUARTERS, OutlineParams::width, activity::quartersOfDp,
            isUsedBy = { it.usesLine }
        ) { copy(width = it) },
        slider(R.string.outline_size, MAX_SIZE_DP, OutlineParams::size, activity::dp, { it.sizeLabel != null }) {
            copy(size = it)
        },
        slider(
            R.string.outline_strength, PERCENT, OutlineParams::strength, activity::percent,
            isUsedBy = { it.strengthLabel != null }
        ) { copy(strength = it) }
    )

    private val tiles = listOf(
        FolderCoverStyle.ROUNDED to R.drawable.sample_cover_mountains,
        FolderCoverStyle.CARD to R.drawable.sample_cover_coast,
        FolderCoverStyle.STACK to R.drawable.sample_cover_mountains
    ).map { (style, cover) -> style to sampleTile(style, cover) }

    // the profile the controls show: the pill's own while the covers follow it
    private val shown: OutlineProfile
        get() = if (editingCovers && !coversMatchPill) cover else pill

    private val isLocked get() = editingCovers && coversMatchPill

    init {
        setupPill()
        setupSurface()
        setupStyle()

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                OutlineSettings.save(activity, pill, cover, coversMatchPill)
                callback()
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.outline_lab_title) {
                    refresh()
                }
            }
    }

    private fun setupPill() {
        val content = Glass.contentColor(activity)
        binding.apply {
            outlineLabPill.dressAsFloatingPill(activity.resources.getDimension(R.dimen.nav_pill_radius))
            outlineLabPill.frost(outlineLabPillBackdrop)
            outlineLabPillIcon.applyColorFilter(content)
            outlineLabPillLabel.setTextColor(content)
        }
    }

    private fun setupSurface() {
        binding.apply {
            outlineLabSurface.setOnCheckedChangeListener { _, checkedId ->
                editingCovers = checkedId == R.id.outline_lab_surface_covers
                refresh()
            }

            outlineLabMatch.isChecked = coversMatchPill
            outlineLabMatch.setOnCheckedChangeListener { _, isChecked ->
                coversMatchPill = isChecked
                refresh()
            }

            outlineLabPhotoColor.isChecked = cover.photoColor
            outlineLabPhotoColor.setOnCheckedChangeListener { _, isChecked ->
                cover.photoColor = isChecked
                updatePreview()
            }
        }
    }

    private fun setupStyle() {
        val styles = OutlineStyle.entries
        binding.apply {
            outlineLabPrevious.setOnClickListener { stepStyle(-1) }
            outlineLabNext.setOnClickListener { stepStyle(1) }
            outlineLabStyleHolder.setOnClickListener {
                val items = styles.map { RadioItem(it.ordinal, activity.getString(it.title)) }
                RadioGroupDialog(activity, ArrayList(items), shown.style.ordinal) {
                    pickStyle(styles[it as Int])
                }
            }

            outlineLabReset.setOnClickListener {
                if (!isLocked) {
                    shown.params.remove(shown.style)
                    refresh()
                }
            }
        }
    }

    private fun stepStyle(by: Int) {
        val styles = OutlineStyle.entries
        pickStyle(styles[(shown.style.ordinal + by + styles.size) % styles.size])
    }

    private fun pickStyle(style: OutlineStyle) {
        if (!isLocked) {
            shown.style = style
            refresh()
        }
    }

    /** Fills every control in from the profile being edited, and redraws the preview. */
    private fun refresh() {
        val profile = shown
        val style = profile.style
        val locked = isLocked
        isRefreshing = true
        binding.apply {
            outlineLabMatch.beVisibleIf(editingCovers)
            outlineLabPhotoColor.beVisibleIf(editingCovers)
            outlineLabStyleName.text = activity.getString(style.title)
            outlineLabStylePosition.text = activity.getString(
                R.string.outline_lab_style_position,
                style.ordinal + 1,
                OutlineStyle.entries.size
            )

            listOf(outlineLabStepper, outlineLabReset).forEach {
                it.alpha = if (locked) DISABLED_ALPHA else 1f
            }
        }

        sliders.forEach { it.show(profile, locked) }
        isRefreshing = false
        updatePreview()
    }

    private fun updatePreview() {
        binding.outlineLabPill.outline = pill.look(activity)
        val coverLook = (if (coversMatchPill) pill else cover).look(activity, photoColor = cover.photoColor)
        val textColor = activity.getProperTextColor()
        tiles.forEach { (style, tile) -> tile.dressFor(style, textColor, coverLook) }
    }

    private fun slider(
        @StringRes label: Int,
        max: Int,
        read: (OutlineParams) -> Int,
        format: (Int) -> String,
        isUsedBy: (OutlineStyle) -> Boolean,
        write: OutlineParams.(Int) -> OutlineParams
    ): Slider {
        val row = DialogOutlineLabSliderBinding.inflate(activity.layoutInflater, binding.outlineLabSliders, true)
        return Slider(row, label, read, format, isUsedBy).also { slider ->
            row.outlineSlider.max = max
            row.outlineSlider.min = when (label) {
                // no line is thinner than a quarter of a dp
                R.string.outline_width -> 1
                R.string.outline_lightness -> -max
                else -> 0
            }
            row.outlineSlider.onSeekBarChangeListener { value ->
                row.outlineSliderValue.text = format(value)
                if (!isRefreshing && !isLocked) {
                    shown.current = shown.current.write(value)
                    updatePreview()
                }
            }
        }
    }

    private inner class Slider(
        val row: DialogOutlineLabSliderBinding,
        @StringRes val label: Int,
        val read: (OutlineParams) -> Int,
        val format: (Int) -> String,
        val isUsedBy: (OutlineStyle) -> Boolean
    ) {
        fun show(profile: OutlineProfile, locked: Boolean) {
            val style = profile.style
            val used = isUsedBy(style)
            // the size and strength sliders are named for what they do to this style
            val name = when (label) {
                R.string.outline_size -> style.sizeLabel
                R.string.outline_strength -> style.strengthLabel
                else -> null
            } ?: label

            row.apply {
                outlineSliderLabel.setText(name)
                outlineSlider.progress = read(profile.current)
                outlineSliderValue.text = format(outlineSlider.progress)
                outlineSlider.isEnabled = used && !locked
                root.alpha = if (used && !locked) 1f else DISABLED_ALPHA
            }
        }
    }

    /** A tile of [style] with a made up folder written on it, laid out a third of the dialog wide. */
    private fun sampleTile(style: FolderCoverStyle, @DrawableRes cover: Int): GridDirectoryItemBinding {
        val holder = binding.outlineLabCovers
        val tile = activity.layoutInflater.inflate(style.layout, holder, false) as ViewGroup
        val margin = style.tileMargin(activity.resources, DEFAULT_FOLDER_SPACING)
        tile.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(margin, margin, margin, margin)
        }

        holder.addView(tile)
        val transformation: Transformation<Bitmap> = activity.coverCornersTransformation(style.bitmapCorners)
            ?.let { MultiTransformation(CenterCrop(), it) }
            ?: CenterCrop()

        return GridDirectoryItemBinding(tile).apply {
            dirName.text = activity.getString(style.title)
            photoCnt.setText(R.string.outline_lab_sample_count)
            Glide.with(activity)
                .load(activity.sampleCover(cover))
                .apply(RequestOptions.bitmapTransform(transformation))
                .into(dirThumbnail)
        }
    }
}

private fun Context.percent(value: Int) = getString(R.string.outline_percent, value)

private fun Context.signedPercent(value: Int) = getString(R.string.outline_signed_percent, value)

private fun Context.dp(value: Int) = getString(R.string.outline_dp, value.toString())

private fun Context.quartersOfDp(quarters: Int) =
    getString(R.string.outline_dp, DecimalFormat("0.##").format(quarters.toFloat() / QUARTERS_PER_DP))

private fun Context.sampleCover(@DrawableRes id: Int): Bitmap {
    val drawable = checkNotNull(AppCompatResources.getDrawable(this, id))
    return createBitmap(SAMPLE_COVER_WIDTH, SAMPLE_COVER_HEIGHT).also {
        drawable.setBounds(0, 0, SAMPLE_COVER_WIDTH, SAMPLE_COVER_HEIGHT)
        drawable.draw(Canvas(it))
    }
}
