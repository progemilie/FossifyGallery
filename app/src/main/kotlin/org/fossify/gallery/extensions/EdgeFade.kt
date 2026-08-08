package org.fossify.gallery.extensions

import android.graphics.drawable.GradientDrawable
import android.view.View
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperBackgroundColor

// how solid each fade is where it meets its edge of the screen, and a third of the way in. The
// bottom one has the navigation buttons to keep readable, the top one only has to settle the
// status bar and give the search pill something to sit against.
private const val BOTTOM_FADE_EDGE_ALPHA = 0.85f
private const val BOTTOM_FADE_MID_ALPHA = 0.32f
private const val TOP_FADE_EDGE_ALPHA = 0.55f
private const val TOP_FADE_MID_ALPHA = 0.16f

/**
 * Paints the softening at one end of a grid, in the theme's own background colour - white under a
 * light theme, near black under a dark one - so the system bars stay readable over whatever photo
 * is scrolling past under them.
 *
 * The clear end is that same colour at zero alpha rather than [android.graphics.Color.TRANSPARENT],
 * which is a transparent *black* and would drag a grey cast through the middle of a white fade.
 */
fun View.applyEdgeFade(atTop: Boolean) {
    val base = context.getProperBackgroundColor()
    val edgeAlpha = if (atTop) TOP_FADE_EDGE_ALPHA else BOTTOM_FADE_EDGE_ALPHA
    val midAlpha = if (atTop) TOP_FADE_MID_ALPHA else BOTTOM_FADE_MID_ALPHA
    val orientation = if (atTop) {
        GradientDrawable.Orientation.TOP_BOTTOM
    } else {
        GradientDrawable.Orientation.BOTTOM_TOP
    }

    background = GradientDrawable(
        orientation,
        intArrayOf(base.adjustAlpha(edgeAlpha), base.adjustAlpha(midAlpha), base.adjustAlpha(0f))
    )
}
