package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.fossify.gallery.R

/** How far apart the dots sit, as a multiple of their own size. */
private const val DOT_SPACING = 3f

/** The dotted rule the drop-down draws between two groups of items. */
class DottedDivider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = resources.getDimension(R.dimen.glass_menu_divider_thickness)
        val dot = resources.getDimension(R.dimen.glass_menu_divider_dot)
        pathEffect = DashPathEffect(floatArrayOf(0f, dot * DOT_SPACING), 0f)
    }

    /** Set rather than themed: the divider is painted in whatever colour its panel reads in. */
    var lineColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    init {
        // a rounded cap on a zero-length dash is the dot itself, and neither survives the hardware
        // canvas' fast path for straight lines
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val middle = height / 2f
        canvas.drawLine(
            paddingLeft.toFloat(), middle, (width - paddingRight).toFloat(), middle, paint
        )
    }
}
