package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.use
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.core.view.isVisible
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.R
import org.fossify.gallery.helpers.Glass

/** How much of the text colour is left in the rule between two settings. */
private const val DIVIDER_ALPHA = 0.12f

/** How far a card sinks below a light background, which it cannot be lifted off. */
private const val LIGHT_CARD_SHADE = 0.05f
private const val DARK_BACKGROUND_LUMINANCE = 0.5

/**
 * The colour of a settings card: lifted off a dark background the way the app's glass is, and sunk a
 * shade below a light one - on white there is nothing to lift it towards, and it vanished.
 */
internal fun settingsCardColor(context: Context): Int {
    val background = context.getProperBackgroundColor()
    return if (ColorUtils.calculateLuminance(background) < DARK_BACKGROUND_LUMINANCE) {
        Glass.tint(context)
    } else {
        ColorUtils.blendARGB(background, Color.BLACK, LIGHT_CARD_SHADE)
    }
}

/**
 * A rounded group of settings under a heading of its own, the way One UI and the system's own
 * Settings set theirs out. Its rows are simply its children in the layout - [onFinishInflate] moves
 * them onto the group's card - so activity_settings.xml still reads as the list of settings it is,
 * and every row keeps the id and the setup code it always had.
 *
 * A row's first child, where that is an image, is its icon, and wears its page's hue.
 */
class SettingsGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val heading = TextView(context).apply {
        setTypeface(typeface, Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(org.fossify.commons.R.dimen.normal_text_size))
        val start = resources.getDimensionPixelSize(R.dimen.settings_row_padding_horizontal)
        setPaddingRelative(start, 0, start, resources.getDimensionPixelSize(R.dimen.settings_group_title_gap))
    }

    private val rows = GroupRows(context)

    /** The rows, for a screen that paints them only once they can be seen. */
    val settings: ViewGroup get() = rows

    init {
        orientation = VERTICAL
        context.obtainStyledAttributes(attrs, R.styleable.SettingsGroup).use {
            heading.text = it.getString(R.styleable.SettingsGroup_groupTitle)
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        val settings = children.toList()
        removeAllViews()
        if (!heading.text.isNullOrEmpty()) {
            addView(heading, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }

        settings.forEach { rows.addView(it) }
        addView(rows, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Re-reads the theme, and colours the heading and the row icons [hue] - none on a page of links. */
    fun updateColors(hue: Int? = null) {
        rows.cardColor = settingsCardColor(context)
        rows.lineColor = context.getProperTextColor().adjustAlpha(DIVIDER_ALPHA)
        heading.setTextColor(hue ?: context.getProperPrimaryColor())
        if (hue != null) {
            rows.children.forEach { row ->
                ((row as? ViewGroup)?.getChildAt(0) as? ImageView)?.setColorFilter(hue)
            }
        }
    }

    /**
     * Gone when every row of it is - settings this platform or this build does not offer - so no empty
     * card is left standing under its heading.
     */
    fun refreshVisibility() {
        isVisible = rows.children.any { it.isVisible }
    }
}

/**
 * The card a group's rows sit on, with a hairline between two rows. Drawn rather than laid out, so a
 * row hidden by its setup takes its rule with it; inset to where the labels start, past the icons.
 */
private class GroupRows(context: Context) : LinearLayout(context) {
    private val card = GradientDrawable().apply {
        cornerRadius = resources.getDimension(R.dimen.settings_group_corner_radius)
    }

    private val inset = resources.getDimension(R.dimen.settings_row_padding_horizontal) +
        resources.getDimension(R.dimen.settings_row_icon_size) +
        resources.getDimension(R.dimen.settings_row_icon_gap)

    private val endInset = resources.getDimension(R.dimen.settings_row_padding_horizontal)

    private val paint = Paint().apply {
        strokeWidth = resources.getDimension(R.dimen.settings_divider_thickness)
    }

    var cardColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            card.setColor(value)
        }

    var lineColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    init {
        orientation = VERTICAL
        background = card
        // a row's ripple stays inside the rounded corners
        clipToOutline = true
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val shown = children.filter { it.isVisible }.toList()
        // sat just inside each row's foot rather than on the boundary, where half of a hairline
        // would fall outside the last row and be clipped away with it
        val half = paint.strokeWidth / 2f
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val start = if (rtl) endInset else inset
        val end = width - if (rtl) inset else endInset
        shown.dropLast(1).forEach {
            canvas.drawLine(start, it.bottom - half, end, it.bottom - half, paint)
        }
    }
}
