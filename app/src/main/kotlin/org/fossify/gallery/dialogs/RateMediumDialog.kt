package org.fossify.gallery.dialogs

import android.widget.ImageView
import androidx.core.content.ContextCompat
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogRateMediumBinding
import org.fossify.gallery.helpers.XmpRating

/**
 * The tappable way to rate a photo, for when holding the button and sliding is not wanted or not
 * possible. Tapping the star that is already the rating clears it, so no separate button is needed
 * for going back to unrated.
 */
class RateMediumDialog(
    val activity: BaseSimpleActivity,
    private val currentRating: Int,
    val callback: (rating: Int) -> Unit
) {
    private val binding = DialogRateMediumBinding.inflate(activity.layoutInflater)
    private val stars: List<ImageView>
    private var rating = currentRating.coerceIn(0, XmpRating.MAX_RATING)

    init {
        stars = with(binding) {
            listOf(rateMediumStar1, rateMediumStar2, rateMediumStar3, rateMediumStar4, rateMediumStar5)
        }

        stars.forEachIndexed { index, star ->
            star.setOnClickListener {
                val tapped = index + 1
                rating = if (rating == tapped) 0 else tapped
                updateStars()
            }
        }

        updateStars()

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .setNeutralButton(R.string.clear_rating) { _, _ -> callback(0) }
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.rate)
            }
    }

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
            star.applyColorFilter(ContextCompat.getColor(activity, color))
        }
    }

    private fun dialogConfirmed() {
        if (rating != currentRating) {
            callback(rating)
        }
    }
}
