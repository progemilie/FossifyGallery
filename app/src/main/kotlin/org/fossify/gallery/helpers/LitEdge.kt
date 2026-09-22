package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withTranslation
import org.fossify.commons.extensions.getProperTextColor
import kotlin.math.roundToInt

private const val DEFAULT_OPACITY = 0.25f
private const val DEFAULT_WIDTH_DP = 0.75f
private const val DEFAULT_FADE = 0.8f
private const val FULL_ALPHA = 255

/**
 * The edge a card stands out by - a folder's cover and a stack's cards, a thumbnail held in the
 * reorder mode, the reorder mode's Save: a fine line in the text colour, lit along the top and fading
 * down the sides. The defaults are the app's look; something edged differently passes its own.
 */
data class LitEdge(
    /** The line's opacity along the top, 0 to 1. */
    val opacity: Float = DEFAULT_OPACITY,
    /** In dp. */
    val width: Float = DEFAULT_WIDTH_DP,
    /** How much of [opacity] is gone by the bottom, 0 to 1. */
    val fade: Float = DEFAULT_FADE
)

/** Draws a [LitEdge] just inside a rounded rect, for a view that draws its own shapes. */
class LitEdgePainter(context: Context, private val edge: LitEdge = LitEdge()) {
    private val width = edge.width * context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    private val shape = RectF()
    private var gradientHeight = 0f

    /** The line's colour before [LitEdge.opacity] - the text colour stands out from any theme's background. */
    var color = context.getProperTextColor()
        set(value) {
            if (field != value) {
                field = value
                paint.shader = null
            }
        }

    fun draw(canvas: Canvas, bounds: RectF, cornerRadius: Float) {
        val height = bounds.height()
        if (paint.shader == null || height != gradientHeight) {
            val top = colorAt(edge.opacity)
            val bottom = colorAt(edge.opacity * (1 - edge.fade))
            paint.shader = LinearGradient(0f, 0f, 0f, height, top, bottom, Shader.TileMode.CLAMP)
            gradientHeight = height
        }

        // drawn from the origin so shapes of one height share a gradient, as a stack's cards do. A stroke
        // is centred on its path, so it is pulled in by half its width to stay inside the shape
        val half = width / 2
        val radius = (cornerRadius - half).coerceAtLeast(0f)
        shape.set(half, half, bounds.width() - half, height - half)
        canvas.withTranslation(bounds.left, bounds.top) {
            drawRoundRect(shape, radius, radius, paint)
        }
    }

    private fun colorAt(opacity: Float) = ColorUtils.setAlphaComponent(color, (opacity * FULL_ALPHA).roundToInt())
}

/** A [LitEdge] around a rect [cornerRadius] round, for a view's foreground or background. */
class LitEdgeDrawable(context: Context, private val cornerRadius: Float, edge: LitEdge = LitEdge()) : Drawable() {
    private val painter = LitEdgePainter(context, edge)
    private val shape = RectF()

    /** See [LitEdgePainter.color]. */
    var color: Int
        get() = painter.color
        set(value) {
            if (painter.color != value) {
                painter.color = value
                invalidateSelf()
            }
        }

    override fun draw(canvas: Canvas) {
        shape.set(bounds)
        painter.draw(canvas, shape, cornerRadius)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("abstract on Drawable, so it has to be answered whatever its own docs say")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
