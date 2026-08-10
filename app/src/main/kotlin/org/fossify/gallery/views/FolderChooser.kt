package org.fossify.gallery.views

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.gallery.R
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.helpers.MAX_VISIBLE_QUICK_CHOOSER_FOLDERS
import org.fossify.commons.R as commonsR

/** A folder the quick chooser can copy or move to. */
data class QuickFolder(val path: String, val name: String)

// The list of destination folders that pops up above the copy and move buttons while one is held.
class FolderChooser @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HoldChooser(context, attrs, defStyleAttr) {

    override val endMarginId = R.dimen.folder_chooser_end_margin

    private val rowHeight = resources.getDimensionPixelSize(R.dimen.folder_chooser_row_height)
    private val rowPadding = resources.getDimensionPixelSize(R.dimen.folder_chooser_row_padding)
    private val textSize = resources.getDimension(R.dimen.folder_chooser_text_size)
    private val column = LinearLayout(context)
    private val rows = LinearLayout(context)
    private val scroller = ScrollView(context)
    private val scrollUp: ImageView
    private val scrollDown: ImageView
    private val autoScroller: EdgeAutoScroller

    private var folders = emptyList<QuickFolder>()
    private var lastTouchY = 0f

    // the end of the list is only reachable once the rows have been measured, so opening there waits
    // for the layout pass that a fresh list triggers
    private var pendingScrollToEnd = false

    /** The folder the finger sits over, or null when it sits somewhere that means "never mind". */
    val selected: QuickFolder?
        get() = folders.getOrNull(selectedIndex)

    // tracked by position rather than by value, so two rows that name the same folder cannot both
    // answer to it and light up together
    private var selectedIndex = NO_SELECTION
        set(value) {
            if (field != value) {
                field = value
                updateRowHighlights()
            }
        }

    init {
        cornerRadius = resources.getDimension(R.dimen.chooser_corner_radius)
        blurRadius = Glass.CHOOSER_RADIUS
        elevation = resources.getDimension(R.dimen.chooser_elevation)
        resources.getDimensionPixelSize(R.dimen.chooser_padding).let {
            setPadding(it, it, it, it)
        }

        column.orientation = LinearLayout.VERTICAL
        addView(column, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        scrollUp = buildIndicator(commonsR.drawable.ic_chevron_up_vector)
        column.addView(scrollUp)

        rows.orientation = LinearLayout.VERTICAL
        scroller.apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            // the gesture driving this lives on the button it pops up from, so the list itself must
            // not react to anything that reaches it
            isEnabled = false
            addView(rows, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        column.addView(scroller, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        scrollDown = buildIndicator(commonsR.drawable.ic_chevron_down_vector)
        column.addView(scrollDown)

        autoScroller = EdgeAutoScroller(
            scroller = scroller,
            edgeZone = resources.getDimensionPixelSize(R.dimen.folder_chooser_edge_zone),
            maxSpeed = resources.getDimension(R.dimen.folder_chooser_scroll_speed),
        ) {
            // the rows moved under a finger that did not, so what it is over has changed
            updateSelectionForY(lastTouchY)
            updateScrollIndicators()
        }
    }

    private fun buildIndicator(iconId: Int) = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight / 2)
        setImageResource(iconId)
        contentDescription = null
    }

    /** Filling the list is also what repaints it, so a theme changed since is picked up on opening. */
    fun setFolders(folders: List<QuickFolder>) {
        this.folders = folders
        selectedIndex = NO_SELECTION
        rows.removeAllViews()
        folders.forEach { rows.addView(buildRow(it)) }

        scrollUp.applyColorFilter(Glass.contentColor(context))
        scrollDown.applyColorFilter(Glass.contentColor(context))

        // an explicit height is what makes the list scroll at all, and every row is exactly
        // rowHeight tall so the cap lands on a row boundary rather than halfway through one
        val isScrollable = folders.size > MAX_VISIBLE_QUICK_CHOOSER_FOLDERS
        scroller.layoutParams = (scroller.layoutParams as LinearLayout.LayoutParams).apply {
            height = if (isScrollable) rowHeight * MAX_VISIBLE_QUICK_CHOOSER_FOLDERS else LayoutParams.WRAP_CONTENT
        }

        scrollUp.isVisible = isScrollable
        scrollDown.isVisible = isScrollable
        pendingScrollToEnd = true
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (pendingScrollToEnd) {
            pendingScrollToEnd = false
            scroller.scrollTo(0, autoScroller.maxScroll())
            updateScrollIndicators()
        }
    }

    private fun buildRow(folder: QuickFolder) = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight)
        text = folder.name
        gravity = Gravity.CENTER_VERTICAL
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.MIDDLE
        minWidth = resources.getDimensionPixelSize(R.dimen.folder_chooser_min_row_width)
        maxWidth = resources.getDimensionPixelSize(R.dimen.folder_chooser_max_row_width)
        setTextColor(Glass.contentColor(context))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        setPadding(rowPadding, 0, rowPadding, 0)
    }

    /** Re-reads what the finger at [rawX], [rawY] is over, starting or stopping the edge scroll to match. */
    override fun updateSelectionFor(rawX: Float, rawY: Float) {
        lastTouchY = rawY
        val screenLeft = locationOnScreen(this)[0]
        if (rawX < screenLeft || rawX > screenLeft + width) {
            // off to one side, which is how the gesture is abandoned
            selectedIndex = NO_SELECTION
            autoScroller.stop()
            return
        }

        updateSelectionForY(rawY)
        // driven by where the finger is rather than by whether it landed on a row: below the list is
        // the button it came off, which picks nothing but still has to pull the list down
        autoScroller.update(rawY)
    }

    // the list keeps scrolling itself while the finger rests near an edge, which has to end with it
    override fun onChooserClosed() = autoScroller.stop()

    private fun updateSelectionForY(rawY: Float) {
        val viewportTop = locationOnScreen(scroller)[1]
        selectedIndex = if (rawY > viewportTop + scroller.height) {
            // below the list is the button the finger came off, so nothing is picked yet
            NO_SELECTION
        } else {
            // clamped rather than cleared, so dragging up past the top holds on to the topmost row and
            // keeps the list scrolling instead of stranding the gesture at the very edge it runs out on
            val offsetInList = (rawY - viewportTop).coerceAtLeast(0f) + scroller.scrollY
            (offsetInList / rowHeight).toInt().takeIf { it in folders.indices } ?: NO_SELECTION
        }
    }

    private fun updateScrollIndicators() {
        if (!scrollUp.isVisible) {
            return
        }

        scrollUp.alpha = if (autoScroller.canScrollUp()) 1f else DIMMED_ALPHA
        scrollDown.alpha = if (autoScroller.canScrollDown()) 1f else DIMMED_ALPHA
    }

    private fun updateRowHighlights() {
        val highlight = context.getProperPrimaryColor()
        repeat(rows.childCount) { index ->
            val row = rows.getChildAt(index) as TextView
            val isSelected = index == selectedIndex
            row.setBackgroundResource(if (isSelected) R.drawable.chooser_row_selected else 0)
            row.background?.setTint(highlight)
            row.setTextColor(if (isSelected) highlight.getContrastColor() else Glass.contentColor(context))
        }
    }

    private fun locationOnScreen(view: View): FloatArray {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return floatArrayOf(location[0].toFloat(), location[1].toFloat())
    }

    private companion object {
        const val DIMMED_ALPHA = 0.3f
        const val NO_SELECTION = -1
    }
}
