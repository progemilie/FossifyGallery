package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.gallery.R
import org.fossify.gallery.helpers.XmpRating
import kotlin.math.ceil

/**
 * The row of stars that pops up above the rating button while it is being held, ported from Aves'
 * RateQuickChooser: the finger never leaves the screen, sliding right fills the stars up and
 * sliding left past the first one empties them.
 */
class RatingChooser @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val stars = ArrayList<ImageView>(XmpRating.MAX_RATING)
    private val starMargin = resources.getDimensionPixelSize(R.dimen.rating_chooser_star_margin)
    private val starSize = resources.getDimensionPixelSize(R.dimen.rating_chooser_star_size)

    var rating: Int = 0
        set(value) {
            val coerced = value.coerceIn(0, XmpRating.MAX_RATING)
            if (field != coerced) {
                field = coerced
                updateStars()
            }
        }

    init {
        orientation = HORIZONTAL
        setBackgroundResource(R.drawable.rating_chooser_background)
        elevation = resources.getDimension(R.dimen.rating_chooser_elevation)
        resources.getDimensionPixelSize(R.dimen.rating_chooser_padding).let {
            setPadding(it, it, it, it)
        }

        repeat(XmpRating.MAX_RATING) {
            val star = ImageView(context).apply {
                layoutParams = LayoutParams(starSize, starSize).apply {
                    marginStart = starMargin
                    marginEnd = starMargin
                }
                contentDescription = null
            }

            stars.add(star)
            addView(star)
        }

        updateStars()
    }

    /**
     * The rating the finger currently sits over, given its position on screen. Anywhere left of the
     * first star means no rating at all, which is how a rating gets cleared without lifting off.
     */
    fun ratingForPosition(rawX: Float): Int {
        val location = IntArray(2)
        getLocationOnScreen(location)

        val contentStart = location[0] + paddingStart
        val contentWidth = (width - paddingStart - paddingEnd).toFloat()
        if (contentWidth <= 0f) {
            return rating
        }

        val fraction = (rawX - contentStart) / contentWidth
        val filled = if (isRtl()) 1f - fraction else fraction
        return ceil(XmpRating.MAX_RATING * filled).toInt().coerceIn(0, XmpRating.MAX_RATING)
    }

    private fun isRtl() = layoutDirection == LAYOUT_DIRECTION_RTL

    private fun updateStars() {
        stars.forEachIndexed { index, star ->
            val isFilled = index < rating
            star.setImageResource(
                if (isFilled) {
                    org.fossify.commons.R.drawable.ic_star_vector
                } else {
                    org.fossify.commons.R.drawable.ic_star_outline_vector
                }
            )

            val color = if (isFilled) R.color.star_enabled else R.color.star_disabled
            star.applyColorFilter(ContextCompat.getColor(context, color))
        }
    }
}
