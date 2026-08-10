package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.isSPlus
import org.fossify.gallery.extensions.config

/**
 * The frosted glass the app lays over its own content - the search pill, the choosers held open over
 * a bottom action button. Every panel is the same material: one colour, one translucency, worked out
 * from the theme rather than stored in resources. The only thing a panel picks for itself is how
 * hard it blurs, and those numbers live here too so there is one place to retune the lot.
 *
 * See [org.fossify.gallery.views.GlassPanel] for the view that wears it.
 */
object Glass {
    /** 25 is as far as the blur goes. */
    const val DEFAULT_RADIUS = 20f
    const val SEARCH_PILL_RADIUS = DEFAULT_RADIUS
    const val CHOOSER_RADIUS = DEFAULT_RADIUS

    /** How much of the blur is left showing through the panel's own colour. */
    private const val TINT_ALPHA = 0.5f

    /** What a panel falls back to where there is no cheap blur to be had. */
    private const val FLAT_ALPHA = 0.97f

    // how far a panel's colour is carried off the theme's background towards white, at its darkest
    // and at its lightest. A dark theme gets the most of it: a near black panel over a photo reads as
    // a hole punched in the picture rather than as glass laid over it. A light one has barely
    // anywhere to go and wants no more than a hint, or the panel turns into a white slab.
    private const val MAX_LIFT = 0.20f
    private const val MIN_LIFT = 0.05f

    // WCAG's floor for anything that is not text, and how far an icon is carried towards black at a
    // time looking for it. A colour that cannot reach it is left as dark as it got: past this much
    // darkening it is no longer the colour that was asked for.
    private const val MIN_ICON_CONTRAST = 3.0
    private const val DARKENING_STEP = 0.05f
    private const val MAX_DARKENING = 0.6f

    // Rec. 601 luma weights: perceived brightness, in which a mid grey lands near the middle. The
    // relative luminance androidx works in linearises first, which would read that same grey as dark
    // and hand it most of the lift.
    private const val LUMA_RED = 0.299f
    private const val LUMA_GREEN = 0.587f
    private const val LUMA_BLUE = 0.114f
    private const val FULL_CHANNEL = 255f

    /** There is no cheap hardware blur below Android 12, so panels are painted flat there instead. */
    fun isSupported() = isSPlus()

    /** Whether panels actually frost: the platform can, and the user has left the Glass UI on. */
    fun isEnabled(context: Context) = isSupported() && context.config.glassUI

    /** What a panel is a copy of, and what its blur has to be cleared to frame by frame. */
    fun baseColor(context: Context) = context.getProperBackgroundColor()

    /**
     * A panel's own colour: the theme's background carried towards white by an amount that falls away
     * as that background gets lighter, so a panel reads as glass laid over the app rather than as a
     * patch of the same paint it is sitting on.
     */
    fun tint(context: Context): Int {
        val background = baseColor(context)
        val brightness = (
            LUMA_RED * Color.red(background) +
                LUMA_GREEN * Color.green(background) +
                LUMA_BLUE * Color.blue(background)
            ) / FULL_CHANNEL

        val darkness = 1f - brightness
        val lift = MIN_LIFT + (MAX_LIFT - MIN_LIFT) * darkness * darkness
        return ColorUtils.blendARGB(background, Color.WHITE, lift)
    }

    /** The wash laid over the blurred copy. */
    fun overlay(context: Context) = tint(context).adjustAlpha(TINT_ALPHA)

    /** The fill a panel wears instead of a blur. */
    fun flatFill(context: Context) = tint(context).adjustAlpha(FLAT_ALPHA)

    /** What a panel's own text and icons have to be painted in to read against it. */
    fun contentColor(context: Context) = context.getProperTextColor()

    /**
     * An icon that keeps a colour of its own - a star's amber - carried far enough from the panel to
     * still read on it. The light theme's glass is near white, where colours chosen to glow against a
     * dark panel wash out; darkening them until they clear [MIN_ICON_CONTRAST] leaves the dark theme's
     * panels, which they already stand out from, untouched.
     */
    fun readableOnGlass(context: Context, color: Int): Int {
        val panel = tint(context)
        var darkened = color
        var darkening = 0f
        while (
            darkening < MAX_DARKENING &&
            ColorUtils.calculateContrast(darkened, panel) < MIN_ICON_CONTRAST
        ) {
            darkening += DARKENING_STEP
            darkened = ColorUtils.blendARGB(color, Color.BLACK, darkening)
        }

        return darkened
    }
}
