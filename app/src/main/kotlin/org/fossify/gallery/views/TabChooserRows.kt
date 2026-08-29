package org.fossify.gallery.views

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.text.NumberFormat
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.gallery.R
import org.fossify.gallery.helpers.Glass

/**
 * Building and painting [TabChooser]'s rows, split out of it to stay under detekt's function count
 * the way [GlassMenuParts] and [MetadataRows] are.
 */

/**
 * The number a tab wears, in the reader's own digits - the button and the rows draw the same one.
 */
internal fun tabLabel(index: Int): String = NumberFormat.getIntegerInstance().format(index + 1L)

/** The sizes every row is built to, which are the same for every row in one chooser. */
data class TabRowMetrics(
    val rowWidth: Int,
    val rowHeight: Int,
    val textSize: Float,
)

/** A row is the number and nothing else - closing is [TabCloseButton]'s, off to the side. */
internal fun LinearLayout.addTabRow(label: String, metrics: TabRowMetrics) {
    addView(TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(metrics.rowWidth, metrics.rowHeight)
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.textSize)
    })
}

/**
 * Repaints every row. The plate comes off the row the moment the finger leaves it for the cross, so
 * it is plain that letting go now closes rather than switches.
 */
internal fun LinearLayout.paintTabRows(selectedIndex: Int, isOnCross: Boolean, currentIndex: Int) {
    val highlight = context.getProperPrimaryColor()
    val content = Glass.contentColor(context)
    repeat(childCount) { index ->
        val row = getChildAt(index) as TextView
        val wearsPlate = index == selectedIndex && !isOnCross

        row.setBackgroundResource(if (wearsPlate) R.drawable.chooser_row_selected else 0)
        row.background?.setTint(highlight)
        row.setTextColor(if (wearsPlate) highlight.getContrastColor() else content)
        row.setTypeface(null, if (index == currentIndex) Typeface.BOLD else Typeface.NORMAL)
    }
}

internal fun View.screenLocation(): FloatArray {
    val location = IntArray(2)
    getLocationOnScreen(location)
    return floatArrayOf(location[0].toFloat(), location[1].toFloat())
}
