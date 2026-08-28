package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import org.fossify.gallery.R
import org.fossify.gallery.extensions.currentTabIndexIn
import org.fossify.gallery.extensions.tabs
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.helpers.MAX_TABS

/** What a finger letting go of the tab chooser meant. */
sealed interface TabChoice {
    data class Switch(val index: Int) : TabChoice
    data class Close(val index: Int) : TabChoice
    data object New : TabChoice
}

/**
 * The list of tabs, held open while the tab button is. Rows are the tabs by number, with a last row
 * offering another one while there is room for it.
 *
 * Closing is behind a dwell: resting on a row long enough grows a cross beside it, which has to be
 * slid onto before letting go. Nothing is closed by hesitating, and a row released on is still the
 * tab switched to. Building and painting the rows is in TabChooserRows.kt.
 */
class TabChooser @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HoldChooser(context, attrs, defStyleAttr) {

    override val endMarginId = R.dimen.chooser_edge_margin

    private val rowHeight = resources.getDimensionPixelSize(R.dimen.tab_chooser_row_height)
    private val dropGap = resources.getDimensionPixelSize(R.dimen.tab_chooser_drop_gap)
    private val rows = LinearLayout(context)

    private val metrics = TabRowMetrics(
        rowWidth = resources.getDimensionPixelSize(R.dimen.tab_chooser_row_width),
        rowHeight = rowHeight,
        closeWidth = resources.getDimensionPixelSize(R.dimen.tab_chooser_close_width),
        textSize = resources.getDimension(R.dimen.tab_chooser_text_size),
    )

    /** Whether this one hangs under its button rather than opening above it. */
    var dropsBelow = false

    private var tabCount = 0
    private var canAddTab = false
    private var currentIndex = 0

    /** Which row the finger is over, by position so two identical labels cannot both answer to it. */
    private var selectedIndex = NO_SELECTION
        set(value) {
            if (field != value) {
                field = value
                // the dwell is a rest on one row, so moving to another starts the wait over
                disarmClose()
                armCloseIfPossible()
                repaint()
            }
        }

    /** The row whose cross is showing, once it has been rested on long enough. */
    private var armedIndex = NO_SELECTION

    /** Whether the finger has since moved sideways onto that cross. */
    private var isOnCross = false

    private val armRunnable = Runnable {
        armedIndex = selectedIndex
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        repaint()
    }

    /** What letting go here would mean. Read once the chooser has been taken away. */
    val choice: TabChoice?
        get() = when {
            selectedIndex == NO_SELECTION -> null
            isNewTabRow(selectedIndex) -> TabChoice.New
            isOnCross && armedIndex == selectedIndex -> TabChoice.Close(selectedIndex)
            selectedIndex == currentIndex -> null
            else -> TabChoice.Switch(selectedIndex)
        }

    init {
        cornerRadius = resources.getDimension(R.dimen.chooser_corner_radius)
        blurRadius = Glass.CHOOSER_RADIUS
        elevation = resources.getDimension(R.dimen.chooser_elevation)
        resources.getDimensionPixelSize(R.dimen.chooser_padding).let {
            setPadding(it, it, it, it)
        }

        rows.orientation = LinearLayout.VERTICAL
        addView(rows, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    /**
     * Fills the list from the tabs on file and repaints it, so a theme changed since it was last up
     * is picked up on opening. The same list wherever the chooser is offered, off one read of it.
     */
    fun fillFromTabs() {
        val tabList = context.tabs()
        tabCount = tabList.size
        currentIndex = context.currentTabIndexIn(tabList)
        // no last row offering another one once the app is holding as many tabs as it will
        canAddTab = tabCount < MAX_TABS
        selectedIndex = NO_SELECTION
        armedIndex = NO_SELECTION
        isOnCross = false

        rows.removeAllViews()
        // the last tab left cannot be closed - the app is always looking at something
        repeat(tabCount) { rows.addTabRow(label = tabLabel(it), closable = tabCount > 1, metrics = metrics) }
        if (canAddTab) {
            rows.addTabRow(label = "+", closable = false, metrics = metrics)
        }

        repaint()
    }

    override fun updateSelectionFor(rawX: Float, rawY: Float) {
        val screenLeft = screenLocation()[0]
        if (rawX < screenLeft || rawX > screenLeft + width) {
            // off to one side, which is how the gesture is abandoned
            selectedIndex = NO_SELECTION
            return
        }

        val offsetInList = rawY - rows.screenLocation()[1]
        selectedIndex = if (offsetInList < 0) {
            NO_SELECTION
        } else {
            (offsetInList / rowHeight).toInt().takeIf { it < rows.childCount } ?: NO_SELECTION
        }

        updateCrossReach(rawX)
    }

    /** Hangs under the button where it was told to, and opens over it as usual otherwise. */
    override fun position(button: View) {
        centerOver(button)
        if (!dropsBelow || height == 0) {
            return
        }

        val untranslatedTop = screenLocation()[1] - translationY
        translationY = button.screenLocation()[1] + button.height + dropGap - untranslatedTop
    }

    override fun onChooserClosed() {
        removeCallbacks(armRunnable)
    }

    private fun isNewTabRow(index: Int) = canAddTab && index == tabCount

    /** Whether the finger has crossed onto the cross of the row it armed. */
    private fun updateCrossReach(rawX: Float) {
        val wasOnCross = isOnCross
        val cross = crossOf(armedIndex)
        isOnCross = cross != null
                && selectedIndex == armedIndex
                && rawX >= cross.screenLocation()[0]

        if (wasOnCross != isOnCross) {
            repaint()
        }
    }

    private fun armCloseIfPossible() {
        val cross = crossOf(selectedIndex) ?: return
        if (cross.isEnabled) {
            postDelayed(armRunnable, CLOSE_DWELL_MS)
        }
    }

    private fun disarmClose() {
        removeCallbacks(armRunnable)
        armedIndex = NO_SELECTION
        isOnCross = false
    }

    private fun crossOf(index: Int) = if (index == NO_SELECTION || isNewTabRow(index)) {
        null
    } else {
        rows.tabRowCross(index)
    }

    private fun repaint() = rows.paintTabRows(selectedIndex, armedIndex, isOnCross, currentIndex)

    private companion object {
        const val NO_SELECTION = -1

        /**
         * How long a row has to be rested on before it offers to be closed. Twice a long press:
         * closing a tab is not something to be arrived at by pausing over the list.
         */
        const val CLOSE_DWELL_MS = 1000L
    }
}
