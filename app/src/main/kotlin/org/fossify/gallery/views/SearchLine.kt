package org.fossify.gallery.views

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import org.fossify.commons.views.MySearchMenu

/**
 * The search bar's line - the filter chip, when there is one, and commons' field after it - scrolling
 * sideways as one. The field takes whatever room the chip leaves, where its hint is cut short as it
 * always was; typed text that would not fit there widens it instead, and the line follows the cursor
 * as the field asks it to, so what is being typed always shows and the chip gives way to it.
 */
@SuppressLint("ViewConstructor")
class SearchLine private constructor(context: Context, private val field: EditText) : HorizontalScrollView(context) {
    /** What scrolls: the chip goes in ahead of the field. */
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    // so the cursor at the end of the text is not drawn against the edge of the line
    private val cursorRoom = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = true
        addView(row, LayoutParams(WRAP_CONTENT, MATCH_PARENT))
    }

    /** The row as wide as the line, or as the chip and the typed text need - never as the hint. */
    override fun measureChildWithMargins(
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int,
    ) {
        super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed)
        val typed = if (field.text.isEmpty()) 0 else field.measuredWidth + cursorRoom
        val ahead = child.measuredWidth - field.measuredWidth
        val room = MeasureSpec.getSize(parentWidthMeasureSpec) - paddingLeft - paddingRight
        child.measure(
            MeasureSpec.makeMeasureSpec(maxOf(room, ahead + typed), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(child.measuredHeight, MeasureSpec.EXACTLY)
        )
    }

    companion object {
        /** Moves [topBar]'s field into a line of its own, which takes the field's place in the bar. */
        fun around(topBar: MySearchMenu): SearchLine {
            val field = topBar.binding.topToolbarSearch
            val bar = field.parent as ViewGroup
            val line = SearchLine(topBar.context, field)
            val place = RelativeLayout.LayoutParams(field.layoutParams as RelativeLayout.LayoutParams)
            bar.addView(line, bar.indexOfChild(field), place)
            bar.removeView(field)
            // wrapping its text rather than a share of the row alone, or typing would never ask for
            // the field to be measured again - a TextView of a fixed width only redraws
            line.row.addView(field, LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT, 1f))
            return line
        }
    }
}
