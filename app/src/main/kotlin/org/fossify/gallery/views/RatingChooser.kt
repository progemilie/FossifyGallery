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
 * The row of stars that pops up above the rating button while it is being held,
 * sliding right fills the stars up and sliding left empties them.
 */
class RatingChooser @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val stars = ArrayList<ImageView>(XmpRating.MAX_RATING)
    private val starMargin = resources.getDimensionPixelSize(R.dimen.rating_chooser_star_margin)
    private val starSize = resources.getDimensionPixelSize(R.dimen.rating_chooser_star_size)
    private val clearGap = resources.getDimensionPixelSize(R.dimen.rating_chooser_clear_gap)
    private val clearSlot: ImageView

    var rating: Int = 0
        set(value) {
            val coerced = value.coerceIn(0, XmpRating.MAX_RATING)
            if (field != coerced) {
                field = coerced
                updateIcons()
            }
        }

    init {
        orientation = HORIZONTAL
        setBackgroundResource(R.drawable.chooser_background)
        elevation = resources.getDimension(R.dimen.chooser_elevation)
        resources.getDimensionPixelSize(R.dimen.chooser_padding).let {
            setPadding(it, it, it, it)
        }

        clearSlot = ImageView(context).apply {
            layoutParams = LayoutParams(starSize, starSize).apply {
                marginStart = starMargin
                marginEnd = clearGap
            }
            setImageResource(org.fossify.commons.R.drawable.ic_block_vector)
            contentDescription = context.getString(R.string.clear_rating)
        }
        addView(clearSlot)

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

        updateIcons()
    }

    // The rating the finger currently sits over, given its position on screen.
    fun ratingForPosition(rawX: Float): Int {
        if (stars.isEmpty() || width == 0) {
            return rating
        }

        val location = IntArray(2)
        getLocationOnScreen(location)

        // measured off where the stars actually landed rather than off the whole chooser, so the
        // clear slot is not mistaken for part of the scale. taking the outer bounds of the row
        // keeps this right in RTL too, where the layout runs the other way
        val rowLeft = location[0] + stars.minOf { it.left } - starMargin
        val rowRight = location[0] + stars.maxOf { it.right } + starMargin
        val rowWidth = (rowRight - rowLeft).toFloat()
        if (rowWidth <= 0f) {
            return rating
        }

        val fraction = (rawX - rowLeft) / rowWidth
        val filled = if (isRtl()) 1f - fraction else fraction
        return ceil(XmpRating.MAX_RATING * filled).toInt().coerceIn(0, XmpRating.MAX_RATING)
    }

    private fun isRtl() = layoutDirection == LAYOUT_DIRECTION_RTL

    private fun updateIcons() {
        val clearColor = if (rating == 0) R.color.rating_clear_enabled else R.color.star_disabled
        clearSlot.applyColorFilter(ContextCompat.getColor(context, clearColor))

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
