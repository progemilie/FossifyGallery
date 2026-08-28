package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import org.fossify.gallery.R

/**
 * The tab button's icon: a rounded square with the number of the tab you are on inside it.
 *
 * Drawn rather than stored as a vector because the number changes with the tab, and a set of vectors
 * would have to be added to every time [org.fossify.gallery.helpers.MAX_TABS] went up.
 */
class TabBadgeDrawable(
    context: Context,
    @ColorInt private val tint: Int = Color.WHITE,
    /** How big to draw it, for a button that wants the square a shade smaller than the default. */
    private val size: Int = context.resources.getDimensionPixelSize(R.dimen.tab_badge_size),
) : Drawable() {
    private val cornerRadius = context.resources.getDimension(R.dimen.tab_badge_corner_radius)
    private val bounds = RectF()

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.getDimension(R.dimen.tab_badge_stroke)
        color = tint
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = context.resources.getDimension(R.dimen.tab_badge_text_size)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = tint
    }

    // kept rather than formatted in draw(): the label is a locale lookup and an allocation, and it
    // only ever changes with the index
    private var label = tabLabel(0)

    /** Which tab the badge is showing, as a position in the list. */
    var index: Int = 0
        set(value) {
            if (field != value) {
                field = value
                label = tabLabel(value)
                invalidateSelf()
            }
        }

    override fun getIntrinsicWidth() = size

    override fun getIntrinsicHeight() = size

    override fun draw(canvas: Canvas) {
        val inset = strokePaint.strokeWidth / 2
        bounds.set(getBounds())
        bounds.inset(inset, inset)
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, strokePaint)

        // centred on the glyph rather than on the baseline, which sits low in the square
        val middle = (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, bounds.centerX(), bounds.centerY() - middle, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        strokePaint.alpha = alpha
        textPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        strokePaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable, but still abstract")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
