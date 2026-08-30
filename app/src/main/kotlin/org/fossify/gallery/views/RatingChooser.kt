package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.gallery.R
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.helpers.markChosen
import org.fossify.gallery.helpers.XmpRating
import kotlin.math.ceil

/** How far an empty star is held back from the panel's full content colour. */
private const val IDLE_ICON_ALPHA = 0.65f

/**
 * The row of stars that pops up above the rating button while it is being held,
 * sliding right fills the stars up and sliding left empties them.
 */
class RatingChooser @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HoldChooser(context, attrs, defStyleAttr) {

    override val endMarginId = R.dimen.chooser_edge_margin

    private val stars = ArrayList<ImageView>(XmpRating.MAX_RATING)
    private val starMargin = resources.getDimensionPixelSize(R.dimen.rating_chooser_star_margin)
    private val starSize = resources.getDimensionPixelSize(R.dimen.rating_chooser_star_size)
    private val clearGap = resources.getDimensionPixelSize(R.dimen.rating_chooser_clear_gap)
    private val row = LinearLayout(context)
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
        cornerRadius = resources.getDimension(R.dimen.chooser_corner_radius)
        blurRadius = Glass.CHOOSER_RADIUS
        elevation = resources.getDimension(R.dimen.chooser_elevation)
        resources.getDimensionPixelSize(R.dimen.chooser_padding).let {
            setPadding(it, it, it, it)
        }

        row.orientation = LinearLayout.HORIZONTAL
        // the star the finger is at is drawn a little bigger than its own bounds
        row.clipChildren = false
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        clearSlot = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(starSize, starSize).apply {
                marginStart = starMargin
                marginEnd = clearGap
            }
            setImageResource(org.fossify.commons.R.drawable.ic_block_vector)
            contentDescription = context.getString(R.string.clear_rating)
        }
        row.addView(clearSlot)

        repeat(XmpRating.MAX_RATING) {
            val star = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(starSize, starSize).apply {
                    marginStart = starMargin
                    marginEnd = starMargin
                }
                contentDescription = null
            }

            stars.add(star)
            row.addView(star)
        }

        updateIcons()
    }

    override fun onGlassShown() = updateIcons()

    override fun updateSelectionFor(rawX: Float, rawY: Float) {
        rating = ratingForPosition(rawX)
    }

    // The rating the finger currently sits over, given its position on screen.
    private fun ratingForPosition(rawX: Float): Int {
        if (stars.isEmpty() || width == 0) {
            return rating
        }

        val location = IntArray(2)
        row.getLocationOnScreen(location)

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
        val idleColor = Glass.contentColor(context).adjustAlpha(IDLE_ICON_ALPHA)
        clearSlot.applyColorFilter(if (rating == 0) Glass.contentColor(context) else idleColor)
        // the star the finger is at is the rating; the block stands in for it when there is none
        clearSlot.markChosen(rating == 0)
        val filledColor = Glass.readableOnGlass(context, ContextCompat.getColor(context, R.color.star_enabled))

        stars.forEachIndexed { index, star ->
            val isFilled = index < rating
            star.setImageResource(
                if (isFilled) {
                    org.fossify.commons.R.drawable.ic_star_vector
                } else {
                    org.fossify.commons.R.drawable.ic_star_outline_vector
                }
            )

            star.applyColorFilter(if (isFilled) filledColor else idleColor)
            star.markChosen(index == rating - 1)
        }
    }
}
