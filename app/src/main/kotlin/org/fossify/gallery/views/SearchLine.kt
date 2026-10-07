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
 * as the field asks it to, so what is being typed always shows and the chip gives way to it. Even
 * empty, the field keeps room for its cursor beside a chip as wide as the line.
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
        // with nothing typed, still an empty field's room for the cursor: beside a chip as wide as the
        // line it would have no width at all, and so take no focus - which is what opens the search
        val typed = if (field.text.isEmpty()) field.paddingLeft + field.paddingRight else field.measuredWidth
        val ahead = child.measuredWidth - field.measuredWidth
        val room = MeasureSpec.getSize(parentWidthMeasureSpec) - paddingLeft - paddingRight
        child.measure(
            MeasureSpec.makeMeasureSpec(maxOf(room, ahead + typed + cursorRoom), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(child.measuredHeight, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        restIfIdle()
    }

    override fun clearChildFocus(child: View?) {
        super.clearChildFocus(child)
        restIfIdle()
    }

    /** With no cursor to follow and nothing typed, the line goes back to its start and the chip shows whole. */
    private fun restIfIdle() {
        if (scrollX != 0 && !field.isFocused && field.text.isEmpty()) {
            smoothScrollTo(0, 0)
        }
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
