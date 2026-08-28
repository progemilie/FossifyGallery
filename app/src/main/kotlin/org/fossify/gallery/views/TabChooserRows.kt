package org.fossify.gallery.views

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import java.text.NumberFormat
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.gallery.R
import org.fossify.gallery.helpers.Glass
import org.fossify.commons.R as commonsR

/**
 * Building and painting [TabChooser]'s rows, split out of it to stay under detekt's function count
 * the way [GlassMenuParts] and [MetadataRows] are.
 */

/**
 * The number a tab wears, in the reader's own digits - the button and the rows draw the same one.
 */
internal fun tabLabel(index: Int): String = NumberFormat.getIntegerInstance().format(index + 1L)

/** How much of the cross's slot is padding, leaving the glyph its own share of the row. */
private const val CROSS_INSET = 0.25f

/** Where the label sits in a row, and where the cross that closes it sits. */
private const val LABEL_INDEX = 0
private const val CROSS_INDEX = 1

/** The sizes every row is built to, which are the same for every row in one chooser. */
data class TabRowMetrics(
    val rowWidth: Int,
    val rowHeight: Int,
    val closeWidth: Int,
    val textSize: Float,
)

/**
 * A number and, beside it, the cross that closes that tab. The cross is laid out from the start and
 * merely unseen: revealing it must not change the width of a panel already being dragged down.
 */
internal fun LinearLayout.addTabRow(label: String, closable: Boolean, metrics: TabRowMetrics) {
    val row = LinearLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(metrics.rowWidth, metrics.rowHeight)
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    row.addView(TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.textSize)
    })

    row.addView(ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(metrics.closeWidth, ViewGroup.LayoutParams.MATCH_PARENT)
        setImageResource(commonsR.drawable.ic_cross_vector)
        (metrics.closeWidth * CROSS_INSET).toInt().let { setPadding(it, it, it, it) }
        isVisible = false
        isEnabled = closable
        contentDescription = context.getString(R.string.close_tab)
    })

    addView(row)
}

internal fun LinearLayout.tabRowCross(index: Int): ImageView? =
    (getChildAt(index) as? LinearLayout)?.getChildAt(CROSS_INDEX) as? ImageView

/**
 * Repaints every row. The plate moves off the row and onto the cross the moment the finger does, so
 * it is plain that letting go now closes rather than switches.
 */
internal fun LinearLayout.paintTabRows(
    selectedIndex: Int,
    armedIndex: Int,
    isOnCross: Boolean,
    currentIndex: Int,
) {
    val highlight = context.getProperPrimaryColor()
    val content = Glass.contentColor(context)
    repeat(childCount) { index ->
        val row = getChildAt(index) as LinearLayout
        val label = row.getChildAt(LABEL_INDEX) as TextView
        val cross = row.getChildAt(CROSS_INDEX) as ImageView
        val wearsPlate = index == selectedIndex && !isOnCross
        val isArmed = index == armedIndex

        row.setBackgroundResource(if (wearsPlate) R.drawable.chooser_row_selected else 0)
        row.background?.setTint(highlight)
        label.setTextColor(if (wearsPlate) highlight.getContrastColor() else content)
        label.setTypeface(null, if (index == currentIndex) Typeface.BOLD else Typeface.NORMAL)
        cross.isVisible = isArmed
        cross.applyColorFilter(if (isOnCross && isArmed) highlight else content)
    }
}

internal fun View.screenLocation(): FloatArray {
    val location = IntArray(2)
    getLocationOnScreen(location)
    return floatArrayOf(location[0].toFloat(), location[1].toFloat())
}
