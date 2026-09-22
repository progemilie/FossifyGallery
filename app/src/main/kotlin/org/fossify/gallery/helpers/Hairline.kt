package org.fossify.gallery.helpers

import android.content.Context
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.R

/**
 * The plain line a surface is edged with where it only has to be told apart from what it lies on - a
 * dropdown's surface. Its colour is worked out here and its weight is [R.dimen.hairline_width]. A card
 * that has to stand out wears a [LitEdge] instead.
 */
object Hairline {
    // the text colour always stands out from the theme's background, so a faint wash of it edges
    // anything laid on that background without turning into a frame
    private const val ALPHA = 0x40

    fun width(context: Context) = context.resources.getDimensionPixelSize(R.dimen.hairline_width)

    fun color(context: Context) = ColorUtils.setAlphaComponent(context.getProperTextColor(), ALPHA)
}
