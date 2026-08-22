package org.fossify.gallery.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.darkenColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.lightenColor
import org.fossify.gallery.R

/**
 * The pieces [Dropdown] is put together out of - the surface a field and its list are drawn on, how
 * much room the options want, and where there is room to put them. Split off it to keep either side
 * small.
 */

/** How far a dropdown's surface is carried off the theme's background. */
private const val SURFACE_SHIFT = 6
private const val STROKE_ALPHA = 0.2f
private const val RIPPLE_ALPHA = 0.2f

/** Above this a background is light enough to be raised by darkening rather than by lightening. */
private const val LIGHT_LUMINANCE = 0.5

/**
 * A rounded surface one step off the theme's background, outlined so it still reads as one on the
 * pure black theme - where [lightenColor] leaves black exactly as it found it and an elevation
 * shadow has nothing to fall on.
 */
internal fun Context.dropdownSurface(rippled: Boolean): Drawable {
    val background = getProperBackgroundColor()
    val raised = if (ColorUtils.calculateLuminance(background) > LIGHT_LUMINANCE) {
        background.darkenColor(SURFACE_SHIFT)
    } else {
        background.lightenColor(SURFACE_SHIFT)
    }

    val radius = resources.getDimension(R.dimen.dropdown_corner_radius)
    val surface = GradientDrawable().apply {
        cornerRadius = radius
        setColor(raised)
        setStroke(
            resources.getDimensionPixelSize(R.dimen.dropdown_stroke),
            getProperTextColor().adjustAlpha(STROKE_ALPHA)
        )
    }

    if (!rippled) {
        return surface
    }

    // the mask is what holds the ripple inside the rounded corners rather than filling the box
    val mask = GradientDrawable().apply {
        cornerRadius = radius
        setColor(Color.WHITE)
    }

    return RippleDrawable(
        ColorStateList.valueOf(getProperTextColor().adjustAlpha(RIPPLE_ALPHA)), surface, mask
    )
}

/**
 * How wide the list has to be to hold its widest option, up to the width it keeps within.
 *
 * Options are laid out at the list's width rather than at their own, so each has to be asked what it
 * would like before there is a width to lay them out at.
 */
internal fun ViewGroup.widestDropdownRow(): Int {
    val unbounded = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    var widest = 0
    children.forEach {
        it.measure(unbounded, unbounded)
        widest = maxOf(widest, it.measuredWidth)
    }

    return widest.coerceAtMost(resources.getDimensionPixelSize(R.dimen.dropdown_max_width))
}

/** How tall the list wants to be once laid out [width] across, before there has been a layout pass. */
internal fun ViewGroup.dropdownHeightAt(width: Int): Int {
    measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )

    return measuredHeight
}

/**
 * Where a popup [wanted] pixels tall goes: under the field if there is room for it there, otherwise
 * over whichever side has more of it, cut down to fit and scrolling inside. Returns the height to
 * give the popup and the offset to hand [android.widget.PopupWindow.showAsDropDown].
 *
 * Measured against the window's visible frame on the screen, which is what a popup is clamped to and
 * what follows the window into split screen or freeform. A dialog is its own window, far shorter
 * than the display it is centred on, so measuring inside that window instead leaves the list a
 * sliver of the room it actually has.
 */
internal fun View.dropdownPlacement(wanted: Int, gap: Int, room: Int, margin: Int): Pair<Int, Int> {
    val frame = Rect().also { getWindowVisibleDisplayFrame(it) }
    val top = IntArray(2).also { getLocationOnScreen(it) }[1]

    // both measured for the popup itself, which sits [room] outside the panel that is seen: opening
    // downwards it starts at gap - room below the field, opening upwards it ends at gap - room above
    val below = (frame.bottom - margin - (top + height + gap - room)).coerceAtLeast(0)
    val above = (top - gap + room - frame.top - margin).coerceAtLeast(0)
    val floor = resources.getDimensionPixelSize(R.dimen.dropdown_row_height) + room * 2

    if (wanted <= below || below >= above) {
        return minOf(wanted, below).coerceAtLeast(floor) to gap - room
    }

    val fitted = minOf(wanted, above).coerceAtLeast(floor)
    return fitted to -(height + gap - room + fitted)
}
