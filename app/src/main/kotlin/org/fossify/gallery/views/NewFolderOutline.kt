package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.gallery.R
import kotlin.math.roundToInt

// the dashes are a placeholder's, quieter than the text beside them; the plus is drawn at full strength
private const val OUTLINE_ALPHA = 0.5f

/**
 * Where a folder cover would be, drawn as a dashed outline round a plus: the folder picker's tile for
 * making a new folder. Sized like a cover - as wide as its column and [aspectRatio] times as tall, or
 * the other way round when the grid scrolls sideways.
 */
class NewFolderOutline(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var aspectRatio = 1f
    var cornerRadius = 0f
    var isHorizontalScrolling = false

    private val strokeWidth = resources.getDimension(R.dimen.new_folder_outline_width)
    private val plusSize = resources.getDimensionPixelSize(R.dimen.new_folder_plus_size)
    private val plus = AppCompatResources.getDrawable(context, org.fossify.commons.R.drawable.ic_plus_vector)!!.mutate()
    private val outline = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@NewFolderOutline.strokeWidth
        pathEffect = DashPathEffect(
            floatArrayOf(
                resources.getDimension(R.dimen.new_folder_outline_dash),
                resources.getDimension(R.dimen.new_folder_outline_gap)
            ),
            0f
        )
    }

    fun setColor(color: Int) {
        paint.color = color.adjustAlpha(OUTLINE_ALPHA)
        plus.setTint(color)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val isSized = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY &&
            MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
        when {
            // the list view's small square
            isSized -> super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            isHorizontalScrolling -> {
                val height = MeasureSpec.getSize(heightMeasureSpec)
                setMeasuredDimension((height / aspectRatio).roundToInt(), height)
            }

            else -> {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                setMeasuredDimension(width, (width * aspectRatio).roundToInt())
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        // inset by half the stroke, or its outer half is cut off at the view's edge
        val inset = strokeWidth / 2
        outline.set(inset, inset, width - inset, height - inset)
        canvas.drawRoundRect(outline, cornerRadius, cornerRadius, paint)

        val left = (width - plusSize) / 2
        val top = (height - plusSize) / 2
        plus.setBounds(left, top, left + plusSize, top + plusSize)
        plus.draw(canvas)
    }
}
