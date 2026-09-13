package org.fossify.gallery.helpers

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.PathShape
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * The shape the squircle folder style cuts its covers to: a superellipse, |x|^n + |y|^n = 1, whose
 * sides stay straight for longer than a rounded square's and bend into the corners without a seam.
 */
object Squircle {
    private const val EXPONENT = 4.0
    private const val STEPS = 256

    // the size the placeholder's path is traced at, which the shape scales from
    private const val UNIT_SIZE = 100f

    /** A rounded rect's radius, as a share of the side, close enough to stand in for the shape. */
    const val EQUIVALENT_RADIUS = 0.3f

    fun path(width: Float, height: Float, out: Path = Path()): Path {
        out.reset()
        val halfWidth = width / 2.0
        val halfHeight = height / 2.0
        val power = 2 / EXPONENT
        for (step in 0 until STEPS) {
            val angle = 2 * PI * step / STEPS
            val cos = cos(angle)
            val sin = sin(angle)
            val x = (halfWidth + halfWidth * sign(cos) * abs(cos).pow(power)).toFloat()
            val y = (halfHeight + halfHeight * sign(sin) * abs(sin).pow(power)).toFloat()
            if (step == 0) {
                out.moveTo(x, y)
            } else {
                out.lineTo(x, y)
            }
        }

        out.close()
        return out
    }

    /** A flat fill in the shape, for a cover to show while its picture loads. */
    fun placeholder(color: Int) = ShapeDrawable(PathShape(path(UNIT_SIZE, UNIT_SIZE), UNIT_SIZE, UNIT_SIZE)).apply {
        paint.color = color
        paint.isAntiAlias = true
    }
}

/** Cuts a thumbnail to a [Squircle], leaving the corners transparent. Expects it cropped already. */
class SquircleMask : BitmapTransformation() {
    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val width = toTransform.width
        val height = toTransform.height
        val result = pool.get(width, height, Bitmap.Config.ARGB_8888)
        result.setHasAlpha(true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(toTransform, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        // the lock Glide's own corner cutting draws under, which some devices need
        val lock = TransformationUtils.getBitmapDrawableLock()
        lock.lock()
        try {
            val canvas = Canvas(result)
            canvas.drawPath(Squircle.path(width.toFloat(), height.toFloat()), paint)
            canvas.setBitmap(null)
        } finally {
            lock.unlock()
        }

        return result
    }

    override fun equals(other: Any?) = other is SquircleMask

    override fun hashCode() = ID.hashCode()

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
    }

    private companion object {
        const val ID = "org.fossify.gallery.helpers.SquircleMask"
        val ID_BYTES = ID.toByteArray(Key.CHARSET)
    }
}
