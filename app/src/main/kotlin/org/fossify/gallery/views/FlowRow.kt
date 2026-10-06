package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isGone
import kotlin.math.max

/** Lays its children out along a line, starting a new one wherever the next would not fit. */
class FlowRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    var horizontalGap = 0
    var verticalGap = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val room = width - paddingLeft - paddingRight
        val childWidthSpec = MeasureSpec.makeMeasureSpec(room, MeasureSpec.AT_MOST)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        shownChildren().forEach { it.measure(childWidthSpec, childHeightSpec) }

        val height = place(room) { _, _, _ -> }
        setMeasuredDimension(width, height + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val room = right - left - paddingLeft - paddingRight
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        place(room) { child, x, y ->
            val start = if (isRtl) right - left - paddingRight - x - child.measuredWidth else paddingLeft + x
            child.layout(start, paddingTop + y, start + child.measuredWidth, paddingTop + y + child.measuredHeight)
        }
    }

    /** Walks the children through the lines they fall on, and returns how tall that leaves the lot. */
    private inline fun place(room: Int, at: (child: View, x: Int, y: Int) -> Unit): Int {
        var x = 0
        var y = 0
        var lineHeight = 0
        shownChildren().forEach { child ->
            if (x > 0 && x + child.measuredWidth > room) {
                x = 0
                y += lineHeight + verticalGap
                lineHeight = 0
            }

            at(child, x, y)
            x += child.measuredWidth + horizontalGap
            lineHeight = max(lineHeight, child.measuredHeight)
        }

        return y + lineHeight
    }

    private fun shownChildren() = children.filterNot { it.isGone }
}
