package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withMatrix
import org.fossify.gallery.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PERCENT = 100f
private const val DEGREES = 360f
private const val FULL_ALPHA = 255
private const val HSL_COMPONENTS = 3

// how far past a blur's radius its mask has to reach before it has faded to nothing
private const val BLUR_REACH = 2f

// a tinted shadow sits this far below the shape, for each dp of its size, and this much narrower
private const val SHADOW_DROP = 0.35f
private const val SHADOW_NARROWING = 0.15f

private const val DOUBLE_INNER_WIDTH = 0.4f
private const val DOUBLE_INNER_MIX = 0.5f
private const val NEON_CORE_WHITENING = 0.55f
private const val MAX_HUE_SPREAD = 90f

private const val MASK_CACHE_SIZE = 16

/**
 * TEMPORARY, see OutlineStyle. Draws an [OutlineLook] around a rounded rect. [drawInside] keeps within
 * the shape; [drawOutside] lies around it, and so needs a canvas that is not clipped to the shape.
 *
 * Every blur is baked once into an alpha mask per size and drawn in the look's colour, so a grid of
 * covers costs a bitmap draw apiece rather than a blur, and a glass pill redrawing every frame
 * blurs nothing.
 */
class OutlinePainter(private val density: Float) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val mask = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shape = RectF()
    private val innerShape = RectF()
    private val path = Path()
    private val haloMask = MaskSlot()
    private val innerMask = MaskSlot()

    // a shader is kept for as long as what it was made for stays the same
    private var shader: Shader? = null
    private var shaderKey = ""

    fun drawInside(canvas: Canvas, bounds: RectF, radius: Float, look: OutlineLook) {
        val width = look.widthPx(density)
        when (look.style) {
            OutlineStyle.NONE, OutlineStyle.OUTSET_RING -> Unit
            OutlineStyle.HAIRLINE, OutlineStyle.THICK, OutlineStyle.SOFT_GLOW, OutlineStyle.TINTED_SHADOW ->
                stroke(canvas, bounds, radius, width, look.lineColor)

            OutlineStyle.INNER_GLOW, OutlineStyle.FROST_BLEED -> {
                innerGlow(canvas, bounds, radius, look.sizePx(density), look.glowColor)
                stroke(canvas, bounds, radius, width, look.lineColor)
            }

            OutlineStyle.LIT_EDGE -> litEdge(canvas, bounds, radius, width, look)
            OutlineStyle.DOUBLE -> double(canvas, bounds, radius, width, look)
            OutlineStyle.IRIDESCENT -> iridescent(canvas, bounds, radius, width, look)
            OutlineStyle.CORNERS -> corners(canvas, bounds, radius, width, look)
            OutlineStyle.NEON -> {
                innerGlow(canvas, bounds, radius, look.sizePx(density), look.glowColor)
                val core = ColorUtils.blendARGB(look.lineColor, Color.WHITE, NEON_CORE_WHITENING)
                stroke(canvas, bounds, radius, width, ColorUtils.setAlphaComponent(core, Color.alpha(look.lineColor)))
            }
        }
    }

    fun drawOutside(canvas: Canvas, bounds: RectF, radius: Float, look: OutlineLook) {
        val size = look.sizePx(density)
        when (look.style) {
            OutlineStyle.SOFT_GLOW, OutlineStyle.NEON -> halo(canvas, bounds, radius, size, drop = 0f, look.glowColor)
            OutlineStyle.TINTED_SHADOW -> halo(canvas, bounds, radius, size, drop = size * SHADOW_DROP, look.glowColor)
            OutlineStyle.OUTSET_RING -> {
                val width = look.widthPx(density)
                val reach = size + width / 2
                shape.set(bounds)
                shape.inset(-reach, -reach)
                line.shader = null
                line.strokeWidth = width
                line.color = look.lineColor
                canvas.drawRoundRect(shape, radius + reach, radius + reach, line)
            }

            else -> Unit
        }
    }

    // a stroke is drawn centred on its path, so it is pulled in by half its width to stay inside
    private fun stroke(canvas: Canvas, bounds: RectF, radius: Float, width: Float, color: Int, shader: Shader? = null) {
        if (width <= 0f || (Color.alpha(color) == 0 && shader == null)) {
            return
        }

        shape.set(bounds)
        shape.inset(width / 2, width / 2)
        val inner = (radius - width / 2).coerceAtLeast(0f)
        line.strokeWidth = width
        line.strokeCap = Paint.Cap.BUTT
        line.color = if (shader == null) color else Color.BLACK
        line.shader = shader
        canvas.drawRoundRect(shape, inner, inner, line)
        line.shader = null
    }

    /** Light catching the top of the rim and falling away down the sides. */
    private fun litEdge(canvas: Canvas, bounds: RectF, radius: Float, width: Float, look: OutlineLook) {
        val top = look.lineColor
        val fadedAlpha = Color.alpha(top) * (1 - look.params.strength / PERCENT)
        val bottom = ColorUtils.setAlphaComponent(top, fadedAlpha.roundToInt())
        val shader = shaderFor("lit:${bounds.top}:${bounds.bottom}:$top:$bottom") {
            LinearGradient(0f, bounds.top, 0f, bounds.bottom, top, bottom, Shader.TileMode.CLAMP)
        }

        stroke(canvas, bounds, radius, width, top, shader)
    }

    /** The line, and a finer one inside it in a softer shade, [OutlineParams.size] apart. */
    private fun double(canvas: Canvas, bounds: RectF, radius: Float, width: Float, look: OutlineLook) {
        stroke(canvas, bounds, radius, width, look.lineColor)

        val innerWidth = max(1f, width * DOUBLE_INNER_WIDTH)
        val inset = width + look.sizePx(density)
        val innerColor = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(look.hue, look.neutral, DOUBLE_INNER_MIX),
            (look.params.strength / PERCENT * FULL_ALPHA).roundToInt()
        )

        innerShape.set(bounds)
        innerShape.inset(inset, inset)
        stroke(canvas, innerShape, (radius - inset).coerceAtLeast(0f), innerWidth, innerColor)
    }

    /** The colour swept round the shape through its neighbours on the colour wheel. */
    private fun iridescent(canvas: Canvas, bounds: RectF, radius: Float, width: Float, look: OutlineLook) {
        val base = look.lineColor
        val spread = look.params.strength / PERCENT * MAX_HUE_SPREAD
        val shader = shaderFor("iris:${bounds.centerX()}:${bounds.centerY()}:$base:$spread") {
            val colors = intArrayOf(base, shiftHue(base, spread), base, shiftHue(base, -spread), base)
            SweepGradient(bounds.centerX(), bounds.centerY(), colors, null)
        }

        stroke(canvas, bounds, radius, width, base, shader)
    }

    /** Only the corners, each carried [OutlineParams.size] along both sides - a viewfinder's brackets. */
    @Suppress("MagicNumber")
    private fun corners(canvas: Canvas, bounds: RectF, radius: Float, width: Float, look: OutlineLook) {
        if (width <= 0f || Color.alpha(look.lineColor) == 0) {
            return
        }

        val half = width / 2
        val left = bounds.left + half
        val top = bounds.top + half
        val right = bounds.right - half
        val bottom = bounds.bottom - half
        val r = (radius - half).coerceAtLeast(0f)
        val run = look.sizePx(density).coerceAtMost(min(right - left, bottom - top) / 2 - r).coerceAtLeast(0f)

        path.reset()
        path.moveTo(left, top + r + run)
        path.arcTo(left, top, left + 2 * r, top + 2 * r, 180f, 90f, false)
        path.lineTo(left + r + run, top)

        path.moveTo(right - r - run, top)
        path.arcTo(right - 2 * r, top, right, top + 2 * r, 270f, 90f, false)
        path.lineTo(right, top + r + run)

        path.moveTo(right, bottom - r - run)
        path.arcTo(right - 2 * r, bottom - 2 * r, right, bottom, 0f, 90f, false)
        path.lineTo(right - r - run, bottom)

        path.moveTo(left + r + run, bottom)
        path.arcTo(left, bottom - 2 * r, left + 2 * r, bottom, 90f, 90f, false)
        path.lineTo(left, bottom - r - run)

        line.shader = null
        line.strokeWidth = width
        line.strokeCap = Paint.Cap.ROUND
        line.color = look.lineColor
        canvas.drawPath(path, line)
    }

    private fun halo(canvas: Canvas, bounds: RectF, radius: Float, blur: Float, drop: Float, color: Int) {
        if (blur < 1f || Color.alpha(color) == 0) {
            return
        }

        val width = bounds.width().roundToInt()
        val height = bounds.height().roundToInt()
        val bitmap = haloMask.get(OutlineMasks.haloKey(width, height, radius, blur, drop)) {
            OutlineMasks.halo(width, height, radius, blur, drop)
        }

        val pad = OutlineMasks.padFor(blur)
        mask.color = color
        canvas.drawBitmap(bitmap, bounds.left - pad, bounds.top - pad, mask)
    }

    private fun innerGlow(canvas: Canvas, bounds: RectF, radius: Float, blur: Float, color: Int) {
        if (blur < 1f || Color.alpha(color) == 0) {
            return
        }

        val width = bounds.width().roundToInt()
        val height = bounds.height().roundToInt()
        val bitmap = innerMask.get(OutlineMasks.innerKey(width, height, radius, blur)) {
            OutlineMasks.innerGlow(width, height, radius, blur)
        }

        mask.color = color
        canvas.drawBitmap(bitmap, bounds.left, bounds.top, mask)
    }

    private inline fun shaderFor(key: String, create: () -> Shader): Shader {
        if (key != shaderKey || shader == null) {
            shader = create()
            shaderKey = key
        }

        return shader!!
    }

    /** The mask a painter last drew, so a redraw of the same size never goes as far as the cache. */
    private class MaskSlot {
        private var key = 0L
        private var bitmap: Bitmap? = null

        inline fun get(key: Long, create: () -> Bitmap): Bitmap {
            val current = bitmap
            if (current != null && key == this.key) {
                return current
            }

            return OutlineMasks.cached(key, create).also {
                this.key = key
                bitmap = it
            }
        }
    }
}

private fun shiftHue(color: Int, degrees: Float): Int {
    val hsl = FloatArray(HSL_COMPONENTS)
    ColorUtils.colorToHSL(color, hsl)
    hsl[0] = (hsl[0] + degrees + DEGREES) % DEGREES
    return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), Color.alpha(color))
}

/** The blurred alpha masks, shared by every painter: a grid's covers are all one size. */
private object OutlineMasks {
    private const val HALO = 1L
    private const val INNER = 2L
    private const val PRIME = 31L

    private val cache = LruCache<Long, Bitmap>(MASK_CACHE_SIZE)

    fun padFor(blur: Float) = ceil(blur * BLUR_REACH).toInt()

    fun haloKey(width: Int, height: Int, radius: Float, blur: Float, drop: Float) =
        keyOf(HALO, width, height, radius, blur, drop)

    fun innerKey(width: Int, height: Int, radius: Float, blur: Float) = keyOf(INNER, width, height, radius, blur, 0f)

    inline fun cached(key: Long, create: () -> Bitmap): Bitmap = cache.get(key) ?: create().also { cache.put(key, it) }

    /** A blurred copy of the shape [drop] below it, with the shape itself cut back out. */
    fun halo(width: Int, height: Int, radius: Float, blur: Float, drop: Float): Bitmap {
        val pad = padFor(blur)
        val bitmap = createBitmap(width + 2 * pad, height + 2 * pad + ceil(drop).toInt(), Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) }
        val narrowing = drop * SHADOW_NARROWING / SHADOW_DROP
        val left = pad.toFloat()
        val top = pad.toFloat()
        val right = left + width
        val bottom = top + height
        canvas.drawRoundRect(left + narrowing, top + drop, right - narrowing, bottom + drop, radius, radius, paint)

        paint.maskFilter = null
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        return bitmap
    }

    /** Everything outside the shape blurred into it, and then cut away again outside it. */
    fun innerGlow(width: Int, height: Int, radius: Float, blur: Float): Bitmap {
        val bitmap = createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        val reach = blur * BLUR_REACH
        val outside = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(-reach, -reach, width + reach, height + reach, Path.Direction.CW)
            addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Path.Direction.CW)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) }
        canvas.drawPath(outside, paint)

        paint.maskFilter = null
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawPath(outside, paint)
        return bitmap
    }

    // worked out on every draw of a pill that redraws every frame, so without a list to allocate
    private fun keyOf(kind: Long, width: Int, height: Int, radius: Float, blur: Float, drop: Float): Long {
        var key = kind
        key = key * PRIME + width
        key = key * PRIME + height
        key = key * PRIME + radius.roundToInt()
        key = key * PRIME + blur.roundToInt()
        return key * PRIME + drop.roundToInt()
    }
}

/** TEMPORARY, see OutlineStyle. The inside of an outline, for a view's foreground. */
class OutlineDrawable(context: Context, private val look: OutlineLook, private val cornerRadius: Float) : Drawable() {
    private val painter = OutlinePainter(context.resources.displayMetrics.density)
    private val shape = RectF()

    override fun draw(canvas: Canvas) {
        shape.set(bounds)
        painter.drawInside(canvas, shape, cornerRadius, look)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("abstract on Drawable, so it has to be answered whatever its own docs say")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * TEMPORARY, see OutlineStyle. Lays what [look] draws outside a shape around this view, over [host] -
 * a glass panel clips all it draws to its own shape, and a thumbnail to its bounds. [host] has to be
 * an ancestor that leaves room: a view drawn translucent is drawn into a layer cut to its bounds, so a
 * faded, lifted thumbnail hands its halo to the grid. Null, or a look drawing nothing outside, takes it
 * off again.
 */
fun View.setOutlineHalo(look: OutlineLook?, cornerRadius: Float, host: ViewGroup? = parent as? ViewGroup) {
    (getTag(R.id.outline_halo) as? OutlineHalo)?.let {
        it.host.overlay.remove(it)
        removeOnLayoutChangeListener(it)
        setTag(R.id.outline_halo, null)
    }

    if (host == null || look == null || !look.style.reachesOutside) {
        return
    }

    val halo = OutlineHalo(this, host, look, cornerRadius)
    host.overlay.add(halo)
    addOnLayoutChangeListener(halo)
    setTag(R.id.outline_halo, halo)
}

private class OutlineHalo(
    private val target: View,
    val host: ViewGroup,
    private val look: OutlineLook,
    private val cornerRadius: Float
) : Drawable(), View.OnLayoutChangeListener {
    private val painter = OutlinePainter(target.resources.displayMetrics.density)
    private val shape = RectF()
    private val toHost = Matrix()

    init {
        updateBounds()
    }

    // drawn in the target's own coordinates, carried through every move and scale between it and the host
    override fun draw(canvas: Canvas) {
        if (target.visibility != View.VISIBLE || !mapTargetToHost()) {
            return
        }

        shape.set(0f, 0f, target.width.toFloat(), target.height.toFloat())
        canvas.withMatrix(toHost) {
            painter.drawOutside(this, shape, cornerRadius, look)
        }
    }

    private fun mapTargetToHost(): Boolean {
        toHost.reset()
        var view = target
        while (view !== host) {
            val parent = view.parent as? ViewGroup ?: return false
            toHost.postConcat(view.matrix)
            toHost.postTranslate((view.left - parent.scrollX).toFloat(), (view.top - parent.scrollY).toFloat())
            view = parent
        }

        return true
    }

    override fun onLayoutChange(
        v: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int
    ) {
        updateBounds()
        invalidateSelf()
    }

    private fun updateBounds() = setBounds(target.left, target.top, target.right, target.bottom)

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("abstract on Drawable, so it has to be answered whatever its own docs say")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
