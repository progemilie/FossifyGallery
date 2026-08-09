package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ItemMetadataRowBinding
import org.fossify.gallery.databinding.ItemMetadataSectionBinding
import org.fossify.gallery.databinding.MetadataSheetBinding
import org.fossify.gallery.helpers.MetadataReader
import org.fossify.gallery.models.FileMetadata
import org.fossify.gallery.models.MetadataSection
import org.fossify.gallery.models.MetadataTag
import java.text.NumberFormat

/**
 * The panel the viewer pulls up from the bottom, listing everything the file on screen says about
 * itself.
 *
 * It rests at a height that shows the whole pinned summary and no more, so the photo stays visible
 * behind it; dragging further opens the collapsible sections, which hold every metadata group the
 * file carries. Sections build their rows the first time they are opened - a file with a fat XMP
 * packet has hundreds of tags, and inflating all of them up front would be paid for on every swipe
 * whether or not anyone looked.
 *
 * Metadata is re-read off the file on every [load], never taken from the media database, so what is
 * on screen is what is on disk right now.
 *
 * The sheet expects to be the only child of a full-screen [androidx.coordinatorlayout.widget.CoordinatorLayout]
 * of its own (see layout/metadata_sheet_holder.xml) and hides that holder along with itself, so a
 * dismissed sheet leaves no invisible layer between a finger and the photo.
 */
class MetadataSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        /** The resting sheet never takes more of the screen than this, however long the summary is. */
        private const val MAX_PEEK_RATIO = 0.7f

        private const val LABEL_ALPHA = 0.7f
        private const val HANDLE_ALPHA = 0.4f
        private const val DIVIDER_ALPHA = 0.15f
    }

    private val binding = MetadataSheetBinding.inflate(LayoutInflater.from(context), this)
    private val behavior: BottomSheetBehavior<MetadataSheet> by lazy { BottomSheetBehavior.from(this) }

    private val textColor = context.getProperTextColor()
    private val labelColor = textColor.adjustAlpha(LABEL_ALPHA)
    private val primaryColor = context.getProperPrimaryColor()

    /** The file currently described, so a load that lands after a swipe can be recognised as stale. */
    private var currentPath = ""

    private var topInset = 0
    private var callbackRegistered = false

    /** Called once the sheet has finished sliding out of view, by drag or otherwise. */
    var onHidden: (() -> Unit)? = null

    /** Called when the coordinates row is tapped, with the path of the file it describes. */
    var onLocationClicked: ((String) -> Unit)? = null

    val isSheetVisible: Boolean
        get() = isAttachedToWindow && behavior.state != BottomSheetBehavior.STATE_HIDDEN

    init {
        orientation = VERTICAL
        elevation = resources.getDimension(R.dimen.metadata_sheet_elevation)
        background = context.tinted(R.drawable.metadata_sheet_background, context.getProperBackgroundColor())
        binding.metadataSheetHandleBar.background =
            context.tinted(R.drawable.metadata_sheet_handle, textColor.adjustAlpha(HANDLE_ALPHA))
        binding.metadataSheetPlaceholder.setTextColor(labelColor)
        binding.metadataSheetDivider.setBackgroundColor(textColor.adjustAlpha(DIVIDER_ALPHA))

        // read ignoring visibility because the viewer hides the system bars in fullscreen, and the
        // sheet should not shuffle about by a status bar's worth when it does
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val system = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            topInset = system.top
            binding.metadataSheetScroll.updatePadding(bottom = system.bottom)

            // the resting sheet shows its own top peekHeight pixels, so the blank strip that keeps
            // the summary clear of the navigation bar has to be laid out directly under the summary
            // rather than added to the peek - anything else and the first section heading is what
            // ends up behind the buttons
            binding.metadataSheetSummary.updatePadding(
                bottom = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin) + system.bottom
            )

            updateTopMargin()
            insets
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updateTopMargin()
    }

    /**
     * Stops the fully expanded sheet at the status bar. The behaviour takes the child's top margin
     * as its expanded offset, but part of that clearance may already have been paid for by an
     * ancestor - the viewer pads its content holder by the display cutout - so the margin is worked
     * out from where the holder actually sits on screen rather than from the inset alone. Adding
     * the inset blind would push the sheet down twice on a device with a notch.
     */
    private fun updateTopMargin() {
        val holder = holder ?: return
        val onScreen = IntArray(2)
        holder.getLocationOnScreen(onScreen)

        val wanted = (topInset - onScreen[1]).coerceAtLeast(0)
        val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.topMargin != wanted) {
            // never straight out of a layout pass
            post { updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = wanted } }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (callbackRegistered) return

        callbackRegistered = true
        behavior.isHideable = true
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    holder?.beGone()
                    onHidden?.invoke()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // nothing follows the drag: the photo behind stays put, and the chrome the sheet
                // covers is left as it was so dismissing puts it straight back
            }
        })
    }

    /** Brings the sheet up describing [path], reading the file as it goes. */
    fun show(path: String) {
        holder?.beVisible()
        load(path)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    fun hide() {
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    /**
     * Describes [path] instead of whatever was on screen, without disturbing how far open the sheet
     * is - this is what a swipe onto the next photo goes through.
     */
    fun load(path: String) {
        if (path.isEmpty()) return

        currentPath = path
        binding.metadataSheetSummary.removeAllViews()
        binding.metadataSheetSections.removeAllViews()
        binding.metadataSheetPlaceholder.apply {
            setText(R.string.metadata_reading)
            beVisible()
        }
        binding.metadataSheetScroll.scrollTo(0, 0)

        ensureBackgroundThread {
            val metadata = MetadataReader.read(context, path)
            post {
                // a fast swipe can land several reads out of order; only the one describing the
                // file currently on screen gets to draw itself
                if (currentPath == path) {
                    bind(metadata, path)
                }
            }
        }
    }

    private val holder: View?
        get() = parent as? View

    private fun bind(metadata: FileMetadata, path: String) {
        binding.metadataSheetPlaceholder.apply {
            setText(R.string.metadata_unavailable)
            beVisibleIf(metadata.summary.isEmpty() && metadata.sections.isEmpty())
        }

        metadata.summary.forEach { tag ->
            binding.metadataSheetSummary.addView(buildRow(binding.metadataSheetSummary, tag, path))
        }

        metadata.sections.forEach { section ->
            binding.metadataSheetSections.addView(buildSection(binding.metadataSheetSections, section, path))
        }

        // the summary is what the resting height is measured against, so it has to have been laid
        // out before the peek can be worked out
        binding.metadataSheetSummary.post { updatePeekHeight() }
    }

    private fun updatePeekHeight() {
        val summaryHeight = binding.metadataSheetSummary.height
        if (summaryHeight == 0 || height == 0) return

        // the summary already carries the navigation bar inset as bottom padding
        val wanted = binding.metadataSheetHandle.height + summaryHeight
        val ceiling = (height * MAX_PEEK_RATIO).toInt()
        val floor = resources.getDimensionPixelSize(R.dimen.metadata_sheet_min_peek_height)
        val peek = wanted.coerceIn(minOf(floor, ceiling), ceiling)
        if (peek != behavior.peekHeight) {
            behavior.setPeekHeight(peek, isSheetVisible)
        }
    }

    private fun buildRow(parent: ViewGroup, tag: MetadataTag, path: String): View {
        val row = ItemMetadataRowBinding.inflate(LayoutInflater.from(context), parent, false)
        row.metadataRowLabel.setTextColor(labelColor)
        row.metadataRowLabel.text = tag.name
        row.metadataRowValue.text = tag.value

        if (tag.opensMap) {
            row.metadataRowValue.setTextColor(primaryColor)
            row.root.setOnClickListener { onLocationClicked?.invoke(path) }
        } else {
            row.metadataRowValue.setTextColor(textColor)
        }

        row.root.setOnLongClickListener {
            context.copyToClipboard("${tag.name}: ${tag.value}")
            true
        }

        return row.root
    }

    private fun buildSection(parent: ViewGroup, section: MetadataSection, path: String): View {
        val view = ItemMetadataSectionBinding.inflate(LayoutInflater.from(context), parent, false)
        view.metadataSectionTitle.setTextColor(primaryColor)
        view.metadataSectionTitle.text = section.name
        view.metadataSectionCount.setTextColor(labelColor)
        view.metadataSectionCount.text = NumberFormat.getIntegerInstance().format(section.tags.size)
        view.metadataSectionChevron.setColorFilter(labelColor)
        view.metadataSectionBody.beGone()

        view.metadataSectionHeader.setOnClickListener {
            val expanding = !view.metadataSectionBody.isVisible
            if (expanding && view.metadataSectionBody.isEmpty()) {
                section.tags.forEach { tag ->
                    view.metadataSectionBody.addView(buildRow(view.metadataSectionBody, tag, path))
                }
            }

            view.metadataSectionBody.beVisibleIf(expanding)
            view.metadataSectionChevron.setImageResource(
                if (expanding) {
                    org.fossify.commons.R.drawable.ic_chevron_up_vector
                } else {
                    org.fossify.commons.R.drawable.ic_chevron_down_vector
                }
            )
            view.metadataSectionChevron.setColorFilter(labelColor)
        }

        return view.root
    }
}

/** A copy of [drawableRes] tinted [color], mutated so the shared drawable is left alone. */
private fun Context.tinted(drawableRes: Int, color: Int) =
    ContextCompat.getDrawable(this, drawableRes)?.mutate()?.apply { setTint(color) }
