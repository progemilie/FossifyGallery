package org.fossify.gallery.views

import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.gallery.R
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.helpers.LINE_CHOICE_SWELL
import org.fossify.gallery.helpers.markChosen

/**
 * Building and painting [FolderChooser]'s rows, split out of it to stay under detekt's function
 * count threshold.
 */

/**
 * Two views rather than one: the plate is the outer, which stays exactly the size of its row, and
 * the name is the inner, which is what swells when the finger reaches it. Swelling the plate
 * instead would draw it bigger than the row it belongs to, rounded corners and all.
 */
internal fun LinearLayout.addFolderRow(folder: QuickFolder, rowHeight: Int, rowPadding: Int, textSize: Float) {
    val plate = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight)
        // so a swollen name is not cut off at the edge of the plate it is sitting on
        clipChildren = false
    }

    plate.addView(TextView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        text = folder.name
        gravity = Gravity.CENTER_VERTICAL
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.MIDDLE
        minWidth = resources.getDimensionPixelSize(R.dimen.folder_chooser_min_row_width)
        maxWidth = resources.getDimensionPixelSize(R.dimen.folder_chooser_max_row_width)
        setTextColor(Glass.contentColor(context))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        setPadding(rowPadding, 0, rowPadding, 0)
        // grown out of where the name starts rather than out of the middle of the row: a name left
        // aligned in a row as wide as the list would otherwise swell off the front of it
        pivotX = 0f
        pivotY = rowHeight / 2f
    })

    addView(plate)
}

/** Puts the plate and the swell on the row the finger is over, and takes them off the rest. */
internal fun LinearLayout.paintFolderRows(selectedIndex: Int) {
    val highlight = context.getProperPrimaryColor()
    repeat(childCount) { index ->
        val plate = getChildAt(index) as ViewGroup
        val label = plate.getChildAt(0) as TextView
        val isSelected = index == selectedIndex

        plate.setBackgroundResource(if (isSelected) R.drawable.chooser_row_selected else 0)
        plate.background?.setTint(highlight)
        label.markChosen(isSelected, LINE_CHOICE_SWELL)
        label.setTextColor(if (isSelected) highlight.getContrastColor() else Glass.contentColor(context))
    }
}
