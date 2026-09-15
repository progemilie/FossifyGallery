package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config

/**
 * The one line the app edges things with - a folder's cover and the cards of a stack, a dropdown's
 * surface, a held thumbnail, the glass pill that has to stand apart from the ones beside it. Its colour
 * is worked out here and its weight is [R.dimen.hairline_width], so every outline is retuned from those two.
 */
object Hairline {
    const val MAX_TINT_STRENGTH = 100
    const val DEFAULT_TINT_STRENGTH = 50

    // the text colour always stands out from the theme's background, so a faint wash of it edges
    // anything laid on that background without turning into a frame
    private const val ALPHA = 0x40

    fun width(context: Context) = context.resources.getDimensionPixelSize(R.dimen.hairline_width)

    /** The plain line, for a surface that only has to be told apart from what it lies on. */
    fun color(context: Context) = ColorUtils.setAlphaComponent(context.getProperTextColor(), ALPHA)

    /**
     * The line around something that stands apart - a cover, a held thumbnail, Save. With the tint on
     * it is carried towards the theme's primary colour opacity and all, so full strength is a solid
     * line of that colour. The white and black and white themes answer with their accent, their
     * primary being the colour of the bars.
     */
    fun tintedColor(context: Context): Int {
        val config = context.config
        if (!config.tintOutlines) {
            return color(context)
        }

        val strength = config.outlineTintStrength.toFloat() / MAX_TINT_STRENGTH
        return ColorUtils.blendARGB(color(context), context.getProperPrimaryColor(), strength)
    }

    /**
     * The tinted line around a rect [cornerRadius] round. A stroke is drawn inside the bounds, so its
     * radius is pulled in by half of it - at the shape's own radius its corners would poke out past it.
     */
    fun drawable(context: Context, cornerRadius: Float) = GradientDrawable().apply {
        val width = width(context)
        this.cornerRadius = (cornerRadius - width / 2f).coerceAtLeast(0f)
        setStroke(width, tintedColor(context))
    }
}
