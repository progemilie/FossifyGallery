package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.R

/**
 * The one line the app edges things with - a folder's cover and the cards of a stack, a dropdown's
 * surface, the glass pill that has to stand apart from the ones beside it. Its colour is worked out
 * here and its weight is [R.dimen.hairline_width], so every outline is retuned from those two.
 */
object Hairline {
    // the text colour always stands out from the theme's background, so a faint wash of it edges
    // anything laid on that background without turning into a frame
    private const val ALPHA = 0x40

    fun width(context: Context) = context.resources.getDimensionPixelSize(R.dimen.hairline_width)

    /** The line's colour, taken from [textColor] for something that keeps a text colour of its own. */
    fun color(textColor: Int) = ColorUtils.setAlphaComponent(textColor, ALPHA)

    fun color(context: Context) = color(context.getProperTextColor())

    /**
     * The line around a rect [cornerRadius] round. A stroke is drawn inside the bounds, so its radius
     * is pulled in by half of it - at the shape's own radius its corners would poke out past it.
     */
    fun drawable(context: Context, cornerRadius: Float) = GradientDrawable().apply {
        val width = width(context)
        this.cornerRadius = (cornerRadius - width / 2f).coerceAtLeast(0f)
        setStroke(width, color(context))
    }
}
