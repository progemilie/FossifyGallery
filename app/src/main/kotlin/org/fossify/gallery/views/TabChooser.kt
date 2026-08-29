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
 * The list of tabs, held open while the tab button is. Rows are the tabs by number and nothing else,
 * with a last row offering another one while there is room for it.
 *
 * Closing is behind a dwell: resting on a row long enough grows a [TabCloseButton] beside it, which
 * has to be slid onto before letting go. Nothing is closed by hesitating, and a row released on is
 * still the tab switched to. Building and painting the rows is in TabChooserRows.kt.
 *
 * The button this is held open from sits *beside* the list rather than under it - on the very end of
 * a search bar, or along the bottom bar - so the list is kept clear of the finger holding it down
 * and answers to a reach past its own sides rather than only to what is directly over it.
 */
class TabChooser @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HoldChooser(context, attrs, defStyleAttr) {

    override val endMarginId = R.dimen.chooser_edge_margin

    private val rowHeight = resources.getDimensionPixelSize(R.dimen.tab_chooser_row_height)
    private val dropGap = resources.getDimensionPixelSize(R.dimen.tab_chooser_drop_gap)
    private val closeGap = resources.getDimensionPixelSize(R.dimen.tab_chooser_close_gap)
    private val sideReach = resources.getDimensionPixelSize(R.dimen.tab_chooser_side_reach)
    private val fingerClearance = resources.getDimensionPixelSize(R.dimen.tab_chooser_finger_clearance)
    private val edgeMargin = resources.getDimensionPixelSize(R.dimen.chooser_edge_margin)
    private val rows = LinearLayout(context)
    private val closeButton = TabCloseButton(context)

    private val metrics = TabRowMetrics(
        rowWidth = resources.getDimensionPixelSize(R.dimen.tab_chooser_row_width),
        rowHeight = rowHeight,
        textSize = resources.getDimension(R.dimen.tab_chooser_text_size),
    )

    /** Whether this one hangs under its button rather than opening above it. */
    var dropsBelow = false

    private var tabCount = 0
    private var canAddTab = false
    private var canCloseTabs = false
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

    // the cross grows in off the left hand edge of the list, level with the row that armed it
    private val armRunnable = Runnable {
        armedIndex = selectedIndex
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        closeButton.growInAt(
            rightEdge = screenLocation()[0] - closeGap,
            centreY = rows.screenLocation()[1] + (armedIndex + 0.5f) * rowHeight,
        )

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
        // the cross is put beside this panel rather than inside it, a panel being clipped to its own
        // rounded rect. Left until the list is first asked for, by when the frosting is settled
        closeButton.attachBeside(this, contentBehind)

        val tabList = context.tabs()
        tabCount = tabList.size
        currentIndex = context.currentTabIndexIn(tabList)
        // no last row offering another one once the app is holding as many tabs as it will
        canAddTab = tabCount < MAX_TABS
        // the last tab left cannot be closed - the app is always looking at something
        canCloseTabs = tabCount > 1
        selectedIndex = NO_SELECTION
        disarmClose()

        rows.removeAllViews()
        repeat(tabCount) { rows.addTabRow(label = tabLabel(it), metrics = metrics) }
        if (canAddTab) {
            rows.addTabRow(label = "+", metrics = metrics)
        }

        repaint()
    }

    override fun updateSelectionFor(rawX: Float, rawY: Float) {
        if (closeButton.isReachedBy(rawX, rawY, verticalSlop = rowHeight / 2f)) {
            // the cross stands in for the row it grew beside, so a finger that has reached it holds
            // on to that row rather than picking whatever it has ended up level with
            setOnCross(true)
            return
        }

        setOnCross(false)
        val screenLeft = screenLocation()[0]
        if (rawX < screenLeft - sideReach || rawX > screenLeft + width + sideReach) {
            // out past the reach to one side, which is how the gesture is abandoned
            selectedIndex = NO_SELECTION
            return
        }

        val offsetInList = rawY - rows.screenLocation()[1]
        selectedIndex = if (offsetInList < 0) {
            NO_SELECTION
        } else {
            (offsetInList / rowHeight).toInt().takeIf { it < rows.childCount } ?: NO_SELECTION
        }
    }

    /**
     * Sits to the side of its button rather than centred under it - one narrow column of numbers
     * would be entirely under the finger holding that button down - and never so far over that the
     * cross has nowhere to grow. Hangs under the button where it was told to, and opens above it
     * as usual otherwise.
     */
    override fun position(button: View) {
        val roomForCross = (edgeMargin + closeButton.size + closeGap).toFloat()
        placeLeftEdgeAt((button.centerX() - fingerClearance - width).coerceAtLeast(roomForCross))
        if (!dropsBelow || height == 0) {
            return
        }

        val untranslatedTop = screenLocation()[1] - translationY
        translationY = button.screenLocation()[1] + button.height + dropGap - untranslatedTop
    }

    override fun onChooserClosed() {
        removeCallbacks(armRunnable)
        closeButton.hide()
    }

    private fun isNewTabRow(index: Int) = canAddTab && index == tabCount

    private fun setOnCross(value: Boolean) {
        if (isOnCross == value) {
            return
        }

        isOnCross = value
        closeButton.paint(isUnderFinger = value)
        repaint()
    }

    private fun armCloseIfPossible() {
        if (canCloseTabs && selectedIndex != NO_SELECTION && !isNewTabRow(selectedIndex)) {
            postDelayed(armRunnable, CLOSE_DWELL_MS)
        }
    }

    private fun disarmClose() {
        removeCallbacks(armRunnable)
        armedIndex = NO_SELECTION
        isOnCross = false
        closeButton.hide()
    }

    private fun repaint() = rows.paintTabRows(selectedIndex, isOnCross, currentIndex)

    private companion object {
        const val NO_SELECTION = -1

        /**
         * How long a row has to be rested on before it offers to be closed. Twice a long press:
         * closing a tab is not something to be arrived at by pausing over the list.
         */
        const val CLOSE_DWELL_MS = 1000L
    }
}
