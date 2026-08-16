package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.activities.BaseViewerActivity
import org.fossify.gallery.databinding.MetadataSheetBinding
import org.fossify.gallery.extensions.showFileOnMap
import org.fossify.gallery.helpers.MetadataReader
import org.fossify.gallery.models.FileMetadata
import org.fossify.gallery.models.MetadataGroup

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

        /** How far up the sheet has to travel for the "there is more below" hint to be gone. */
        private const val HINT_FADE_FRACTION = 0.2f

        /** How long the reveal waits for a resting height before coming up without one anyway. */
        private const val MAX_REVEAL_DELAY = 250L

        /** How long a swipe has to settle before the file it landed on is opened. */
        private const val READ_DELAY = 200L

        /** How long the file swiped away from stays on screen before a slow read is admitted to. */
        private const val STALE_CONTENT_TIMEOUT = 500L

        private const val LABEL_ALPHA = 0.7f
        private const val HANDLE_ALPHA = 0.4f
        private const val DIVIDER_ALPHA = 0.15f
    }

    private val binding = MetadataSheetBinding.inflate(LayoutInflater.from(context), this)
    private val behavior: BottomSheetBehavior<MetadataSheet> by lazy { BottomSheetBehavior.from(this) }

    private val textColor = context.getProperTextColor()
    private val labelColor = textColor.adjustAlpha(LABEL_ALPHA)
    private val primaryColor = context.getProperPrimaryColor()

    private val rows = MetadataRows(
        context = context,
        textColor = textColor,
        labelColor = labelColor,
        primaryColor = primaryColor,
        onLocationClicked = { path -> viewer?.showFileOnMap(path) },
        onDescriptionClicked = { path -> writes?.editDescription(path) },
    )

    /** The file currently described, so a load that lands after a swipe can be recognised as stale. */
    private var currentPath = ""

    /** What of the current file's metadata this app can take off it, as the last read found it. */
    private var removable = emptyList<MetadataGroup>()

    private var topInset = 0
    private var callbackRegistered = false

    /** Set between being asked for and coming up, so a layout pass in between can bring it up early. */
    private var revealPending = false

    /**
     * Opens the file named by [currentPath] and swaps its rows in. Posted by [load] a moment after
     * the swipe rather than run on the spot.
     */
    private val readCurrent = Runnable {
        val path = currentPath
        ensureBackgroundThread {
            val metadata = MetadataReader.read(context, path)
            post {
                // a fast swipe can land several reads out of order; only the one describing the
                // file currently on screen gets to draw itself
                if (currentPath == path) {
                    removeCallbacks(showReading)
                    bind(metadata, path)
                }
            }
        }
    }

    /**
     * Empties the sheet down to a note that the file is being read. Only ever seen when there is
     * nothing to keep - the sheet is coming up fresh, or a read has outrun [STALE_CONTENT_TIMEOUT] -
     * because clearing the rows is a visible step in itself: the summary is what the resting sheet
     * is measured from, so an empty one drops the hint that sits below it up to the top edge.
     */
    private val showReading = Runnable {
        binding.metadataSheetSummary.removeAllViews()
        binding.metadataSheetSections.removeAllViews()
        // what it offers to remove describes the file that is on its way out, so it goes with it
        binding.metadataSheetStrip.beGone()
        binding.metadataSheetPlaceholder.apply {
            setText(R.string.metadata_reading)
            beVisible()
        }
        binding.metadataSheetScroll.scrollTo(0, 0)
    }

    /**
     * Brings the sheet up, once. Posted with a delay by [show] and run ahead of that by
     * [updatePeekHeight] as soon as there is a resting height to come up to, so the sheet rises
     * straight to it instead of overshooting and settling back down over the photo.
     */
    private val reveal = Runnable {
        if (revealPending && behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            revealPending = false
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    /** The viewer the sheet is opening over, once [attachTo] has said which. */
    private var viewer: BaseViewerActivity? = null

    /** The two rows of the sheet that write to the file, wired up along with the viewer. */
    private var writes: MetadataWrites? = null

    /** What the viewer wants to know when the sheet has finished sliding out of view. */
    private var onHidden: (() -> Unit)? = null

    val isSheetVisible: Boolean
        get() = isAttachedToWindow && behavior.state != BottomSheetBehavior.STATE_HIDDEN

    /**
     * Wires the sheet to the viewer it lives in: the coordinates row opens the map, the back gesture
     * puts the sheet away rather than leaving the screen, and the navigation bar is handed its own
     * icons back for as long as the sheet covers it. [onHidden] is whatever else that viewer has to
     * put right once the sheet is gone.
     */
    fun attachTo(viewer: BaseViewerActivity, onHidden: () -> Unit = {}) {
        this.viewer = viewer
        this.writes = MetadataWrites(viewer) {
            load(currentPath, keepCurrentUntilRead = true)
            viewer.onCurrentFileChanged()
        }

        this.onHidden = {
            viewer.updateNavigationBarIconsForPanel(false)
            onHidden()
        }

        viewer.onBackPressedDispatcher.addCallback(viewer) {
            if (isSheetVisible) {
                hide()
            } else {
                isEnabled = false
                viewer.onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    init {
        orientation = VERTICAL
        elevation = resources.getDimension(R.dimen.metadata_sheet_elevation)
        background = context.tinted(R.drawable.metadata_sheet_background, context.getProperBackgroundColor())
        binding.metadataSheetHandleBar.background =
            context.tinted(R.drawable.metadata_sheet_handle, textColor.adjustAlpha(HANDLE_ALPHA))
        binding.metadataSheetPlaceholder.setTextColor(labelColor)
        binding.metadataSheetDivider.setBackgroundColor(textColor.adjustAlpha(DIVIDER_ALPHA))
        binding.metadataSheetMoreHintText.setTextColor(labelColor)
        binding.metadataSheetMoreHintIcon.setColorFilter(labelColor)
        binding.metadataSheetStripLabel.setTextColor(primaryColor)
        binding.metadataSheetStripIcon.setColorFilter(primaryColor)
        binding.metadataSheetStripDivider.setBackgroundColor(textColor.adjustAlpha(DIVIDER_ALPHA))
        binding.metadataSheetStripButton.setOnClickListener {
            writes?.removeMetadata(currentPath, removable)
        }

        // read ignoring visibility because the viewer hides the system bars in fullscreen, and the
        // sheet should not shuffle about by a status bar's worth when it does
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val system = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            topInset = system.top
            binding.metadataSheetScroll.updatePadding(bottom = system.bottom)

            // the resting sheet shows its own top peekHeight pixels, so the blank strip that keeps
            // the summary clear of the navigation bar has to be laid out directly under it rather
            // than added to the peek - anything else and the first section heading is what ends up
            // behind the buttons
            binding.metadataSheetPeek.updatePadding(bottom = system.bottom)

            updateTopMargin()
            updatePeekHeight()
            insets
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updateTopMargin()
        // the first pass after the holder is shown is the earliest the sheet knows its own width,
        // and so the earliest a resting height can be worked out at all
        updatePeekHeight()
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
            // never straight out of a layout pass. The resting height is worked out from the margin
            // (see updatePeekHeight), so it is asked for again once the new one is actually on
            post {
                updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = wanted }
                updatePeekHeight(animate = false)
            }
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
                    revealPending = false
                    holder?.beGone()
                    onHidden?.invoke()
                }

                // the settle that brings the sheet up puts it its peek height from the bottom of
                // the screen, while a layout pass puts it there and then moves it down by the top
                // margin as well. The peek is worked out for the second of those (see
                // updatePeekHeight), so the sheet is laid out again once it has come to rest -
                // otherwise a freshly opened one rests a status bar higher than one swiped through
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    requestLayout()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // the hint is about what is below the fold, so it goes as soon as the fold does.
                // nothing else follows the drag: the photo behind stays put, and the chrome the
                // sheet covers is left as it was so dismissing puts it straight back
                binding.metadataSheetMoreHint.alpha =
                    1f - (slideOffset / HINT_FADE_FRACTION).coerceIn(0f, 1f)
            }
        })
    }

    /**
     * Brings the sheet up describing [path], reading the file as it goes, or opens an already
     * resting sheet the rest of the way - a second ask is a request for the part not on screen yet.
     */
    fun show(path: String) {
        if (isSheetVisible) {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            return
        }

        viewer?.updateNavigationBarIconsForPanel(true)

        holder?.beVisible()
        binding.metadataSheetMoreHint.alpha = 1f
        // nothing on screen to hold on to, and the reveal is waiting on a resting height that can
        // only be worked out from the file being come up for, so this one is read straight away
        load(path, keepCurrentUntilRead = false)

        revealPending = true
        postDelayed(reveal, MAX_REVEAL_DELAY)
    }

    fun hide() {
        removeCallbacks(reveal)
        removeCallbacks(readCurrent)
        removeCallbacks(showReading)
        if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            // asked for and dropped again before it ever came up, so the state callback that tidies
            // up after the sheet has nothing to fire on
            revealPending = false
            holder?.beGone()
            onHidden?.invoke()
        } else {
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    /**
     * Describes [path] instead of whatever was on screen, without disturbing how far open the sheet
     * is - this is what a swipe onto the next photo goes through.
     *
     * Everything the sheet says about the file swiped away from stays up until the next file's rows
     * are ready to take their place. [keepCurrentUntilRead] is what a caller with nothing
     * worth keeping turns that off with.
     */
    fun load(path: String, keepCurrentUntilRead: Boolean = true) {
        if (path.isEmpty()) return

        currentPath = path
        removeCallbacks(readCurrent)
        removeCallbacks(showReading)

        if (keepCurrentUntilRead && binding.metadataSheetSummary.isNotEmpty()) {
            postDelayed(showReading, STALE_CONTENT_TIMEOUT)
        } else {
            showReading.run()
        }

        postDelayed(readCurrent, if (keepCurrentUntilRead) READ_DELAY else 0)
    }

    private val holder: View?
        get() = parent as? View

    private val topMargin: Int
        get() = (layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0

    /** Swaps in everything [path] says about itself at once - rows, hint and resting height. */
    private fun bind(metadata: FileMetadata, path: String) {
        binding.metadataSheetSummary.removeAllViews()
        binding.metadataSheetSections.removeAllViews()
        binding.metadataSheetScroll.scrollTo(0, 0)

        binding.metadataSheetPlaceholder.apply {
            setText(R.string.metadata_unavailable)
            beVisibleIf(metadata.summary.isEmpty() && metadata.sections.isEmpty())
        }

        metadata.summary.forEach { tag ->
            binding.metadataSheetSummary.addView(rows.buildRow(binding.metadataSheetSummary, tag, path))
        }

        metadata.sections.forEach { section ->
            binding.metadataSheetSections.addView(rows.buildSection(binding.metadataSheetSections, section, path))
        }

        removable = metadata.removable
        binding.metadataSheetStrip.beVisibleIf(removable.isNotEmpty())

        // nothing to promise below the fold if there are no sections to open
        binding.metadataSheetMoreHint.beVisibleIf(metadata.sections.isNotEmpty())
        // not animated: the rows it is the height of are going up in this same frame, so a resting
        // sheet that settles into its new height afterwards only reads as the sheet twitching
        updatePeekHeight(animate = false)
    }

    /**
     * Works out how tall the sheet comes to rest and brings it up if it was waiting on that.
     *
     * The peek is measured rather than read off the laid out views: it has to be right before the
     * sheet is revealed, which is a layout pass earlier than the rows it is worked out from.
     */
    private fun updatePeekHeight(animate: Boolean = isSheetVisible) {
        if (width == 0 || height == 0 || binding.metadataSheetSummary.isEmpty()) return

        val peekContent = binding.metadataSheetPeek.heightMeasuredAt(width)
        if (peekContent == 0) return

        // the peek content already carries the navigation bar inset as bottom padding
        val wanted = resources.getDimensionPixelSize(R.dimen.metadata_sheet_handle_height) + peekContent
        val ceiling = (height * MAX_PEEK_RATIO).toInt()
        val floor = resources.getDimensionPixelSize(R.dimen.metadata_sheet_min_peek_height)
        // the behaviour rests the sheet peekHeight pixels from the bottom of its parent and then
        // shifts it down again by the top margin updateTopMargin() gave it, so the margin has to be
        // asked for on top of the height actually wanted. Without it the last thing in the peek -
        // which is the one row of the sheet that does something - sits behind the navigation bar
        val peek = wanted.coerceIn(minOf(floor, ceiling), ceiling) + topMargin
        if (peek != behavior.peekHeight) {
            behavior.setPeekHeight(peek, animate)
        }

        if (revealPending) {
            removeCallbacks(reveal)
            reveal.run()
        }
    }
}

/** A copy of [drawableRes] tinted [color], mutated so the shared drawable is left alone. */
private fun Context.tinted(drawableRes: Int, color: Int) =
    ContextCompat.getDrawable(this, drawableRes)?.mutate()?.apply { setTint(color) }

/** How tall this view comes out in a parent [width] pixels across, asked before any layout pass. */
private fun View.heightMeasuredAt(width: Int): Int {
    measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    return measuredHeight
}
