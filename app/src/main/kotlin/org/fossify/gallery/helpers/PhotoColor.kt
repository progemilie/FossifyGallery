package org.fossify.gallery.helpers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

private const val SAMPLE_SIZE = 24
private const val HUE_BINS = 18
private const val DEGREES = 360f
private const val CACHE_SIZE = 256

// pixels darker than this say nothing about a photo's colour, and neither does a photo this grey
private const val MIN_VALUE = 0.15f
private const val MIN_COLORFULNESS = 0.08f

// where the picked colour is held, so an edge reads against the theme whatever the photo
private const val MIN_SATURATION = 0.45f
private const val DARK_THEME_LIGHTNESS_MIN = 0.55f
private const val DARK_THEME_LIGHTNESS_MAX = 0.72f
private const val LIGHT_THEME_LIGHTNESS_MIN = 0.35f
private const val LIGHT_THEME_LIGHTNESS_MAX = 0.5f
private const val FULL_CHANNEL = 255f
private const val COLOR_COMPONENTS = 3

/**
 * TEMPORARY, see OutlineStyle. The colour a photo is most strongly of - the most saturated hue rather
 * than the average, which comes out a muddy brown for most pictures - pulled into a lightness an edge
 * can be seen at over the theme's background. Remembered per bitmap, so a grid pays for it once.
 */
object PhotoColor {
    private val cache = LruCache<Long, Int>(CACHE_SIZE)

    fun of(drawable: Drawable?, darkTheme: Boolean): Int? {
        val bitmap = bitmapOf(drawable) ?: return null
        val key = (System.identityHashCode(bitmap).toLong() shl Int.SIZE_BITS) or
            (bitmap.generationId.toLong() shl 1) or
            (if (darkTheme) 1L else 0L)

        cache.get(key)?.let { return it }
        val color = runCatching { pick(bitmap, darkTheme) }.getOrNull() ?: return null
        cache.put(key, color)
        return color
    }

    private fun bitmapOf(drawable: Drawable?): Bitmap? = when (drawable) {
        is BitmapDrawable -> drawable.bitmap
        // a picture fading in shows the new one on top
        is LayerDrawable -> drawable.numberOfLayers.takeIf { it > 0 }?.let { bitmapOf(drawable.getDrawable(it - 1)) }
        null -> null
        else -> runCatching {
            createBitmap(SAMPLE_SIZE, SAMPLE_SIZE).also {
                val bounds = drawable.copyBounds()
                drawable.setBounds(0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
                drawable.draw(Canvas(it))
                drawable.bounds = bounds
            }
        }.getOrNull()
    }

    private fun pick(bitmap: Bitmap, darkTheme: Boolean): Int? {
        if (bitmap.isRecycled) {
            return null
        }

        // a hardware bitmap cannot be read from, or drawn into a canvas that can
        val readable = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        val sample = readable.scale(SAMPLE_SIZE, SAMPLE_SIZE)
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        sample.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)

        val weights = FloatArray(HUE_BINS)
        val hsv = FloatArray(COLOR_COMPONENTS)
        pixels.forEach { pixel ->
            Color.colorToHSV(pixel, hsv)
            if (hsv[2] >= MIN_VALUE) {
                weights[binOf(hsv[0])] += hsv[1] * hsv[2] * Color.alpha(pixel) / FULL_CHANNEL
            }
        }

        // a bin's neighbours count for half, so a hue straddling two bins is not split in two
        val best = weights.indices.maxBy {
            weights[it] + (weights[(it + 1) % HUE_BINS] + weights[(it + HUE_BINS - 1) % HUE_BINS]) / 2
        }

        if (weights.sum() / pixels.size < MIN_COLORFULNESS) {
            return null
        }

        val hsl = averageOfBin(pixels, best)
        hsl[1] = hsl[1].coerceAtLeast(MIN_SATURATION)
        hsl[2] = if (darkTheme) {
            hsl[2].coerceIn(DARK_THEME_LIGHTNESS_MIN, DARK_THEME_LIGHTNESS_MAX)
        } else {
            hsl[2].coerceIn(LIGHT_THEME_LIGHTNESS_MIN, LIGHT_THEME_LIGHTNESS_MAX)
        }

        return ColorUtils.HSLToColor(hsl)
    }

    // the photo's own hue, saturation and lightness within the winning bin, so a pastel keeps a softer edge
    private fun averageOfBin(pixels: IntArray, bin: Int): FloatArray {
        val hsv = FloatArray(COLOR_COMPONENTS)
        val hsl = FloatArray(COLOR_COMPONENTS)
        val total = FloatArray(COLOR_COMPONENTS)
        var count = 0
        pixels.forEach { pixel ->
            Color.colorToHSV(pixel, hsv)
            ColorUtils.colorToHSL(pixel, hsl)
            if (hsv[2] >= MIN_VALUE && Color.alpha(pixel) > 0 && binOf(hsv[0]) == bin) {
                hsl.indices.forEach { total[it] += hsl[it] }
                count++
            }
        }

        return FloatArray(COLOR_COMPONENTS) { total[it] / count.coerceAtLeast(1) }
    }

    private fun binOf(hue: Float) = (hue / DEGREES * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
}
