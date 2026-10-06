package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
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
 * reorder mode, the reorder mode's Save, the search pill while a search is open: a fine line in the
 * text colour, lit along the top and fading down the sides. The defaults are the app's look;
 * something edged differently passes its own.
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
    private val lightMatrix = Matrix()
    private var gradientHeight = 0f

    /** The line's colour before [LitEdge.opacity] - the text colour stands out from any theme's background. */
    var color = context.getProperTextColor()
        set(value) {
            if (field != value) {
                field = value
                paint.shader = null
            }
        }

    /**
     * Where the light falls, from 0 - along the top, where an edge keeps it - to 1, along the bottom.
     * Anything else is an edge on its way in.
     */
    var lightAt = 0f

    /** How much of the edge is drawn at all, 0 to 1. */
    var strength = 1f

    fun draw(canvas: Canvas, bounds: RectF, cornerRadius: Float) {
        if (strength <= 0f) {
            return
        }

        val height = bounds.height()
        val shader = paint.shader?.takeIf { height == gradientHeight } ?: lightShader(height)

        // the light's fall-off either side of it is laid out around 0 and slid down to where it is -
        // resting at the top, the fall-off below it is exactly the fade down the sides
        lightMatrix.setTranslate(0f, lightAt * height)
        shader.setLocalMatrix(lightMatrix)
        paint.alpha = (strength * FULL_ALPHA).roundToInt()

        // drawn from the origin so shapes of one height share a gradient, as a stack's cards do. A stroke
        // is centred on its path, so it is pulled in by half its width to stay inside the shape
        val half = width / 2
        val radius = (cornerRadius - half).coerceAtLeast(0f)
        shape.set(half, half, bounds.width() - half, height - half)
        canvas.withTranslation(bounds.left, bounds.top) {
            drawRoundRect(shape, radius, radius, paint)
        }
    }

    private fun lightShader(height: Float): Shader {
        val lit = colorAt(edge.opacity)
        val dim = colorAt(edge.opacity * (1 - edge.fade))
        gradientHeight = height
        return LinearGradient(0f, -height, 0f, height, intArrayOf(dim, lit, dim), null, Shader.TileMode.CLAMP)
            .also { paint.shader = it }
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

    /** See [LitEdgePainter.lightAt]. */
    var lightAt: Float
        get() = painter.lightAt
        set(value) {
            painter.lightAt = value
            invalidateSelf()
        }

    /** See [LitEdgePainter.strength]. */
    var strength: Float
        get() = painter.strength
        set(value) {
            painter.strength = value
            invalidateSelf()
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
