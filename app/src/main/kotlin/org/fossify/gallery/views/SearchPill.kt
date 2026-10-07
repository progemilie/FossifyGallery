package org.fossify.gallery.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.gallery.R

// the fill a pill wears at rest, when it is the filter already on, and under a finger
private const val REST_FILL = 0.14f
private const val LIT_FILL = 0.34f
private const val PRESSED_FILL = 0.26f

private const val TEXT_ALPHA = 0.92f

/**
 * One of the search's options: a word on a soft fill, standing on the dimmed grid. The dim is dark
 * whatever the theme, so the pill is always light on it.
 */
class SearchPill @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val fill = GradientDrawable().apply {
        // past half the height, which rounds the ends off whatever the label makes the width
        cornerRadius = resources.getDimension(R.dimen.search_pill_height)
    }

    /** Whether this is the filter the grid is already narrowed by. */
    var isLit = false
        set(value) {
            field = value
            paintFill()
        }

    init {
        background = fill
        gravity = Gravity.CENTER
        minHeight = resources.getDimensionPixelSize(R.dimen.search_pill_height)
        val padding = resources.getDimensionPixelSize(R.dimen.search_pill_padding)
        setPaddingRelative(padding, 0, padding, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(org.fossify.commons.R.dimen.normal_text_size))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(Color.WHITE.adjustAlpha(TEXT_ALPHA))
        maxLines = 1
        paintFill()
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        paintFill()
    }

    private fun paintFill() {
        val alpha = when {
            isPressed -> PRESSED_FILL
            isLit -> LIT_FILL
            else -> REST_FILL
        }

        fill.setColor(Color.WHITE.adjustAlpha(alpha))
    }
}
