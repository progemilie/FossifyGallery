package org.fossify.gallery.helpers

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.gallery.R

/**
 * How a ticked item is drawn, wherever one is: the tick in its corner and the wash laid over the
 * picture under it. One place for the lot of it because the media grid, the folder grid and the
 * reorder mode all mark an item to mean the same thing - this is one of several the next command
 * applies to - and a mark that looked different in one of them would read as something else.
 *
 * The mark settles rather than flashes. Selecting used to be drawn only by the grid's own change
 * animation, which cross-fades the two copies of a rebound tile and reads as a tint that arrives
 * and then leaves again; the grids turn that off and the same movement is played here instead,
 * ending where it should - the picture left darker for as long as it is picked.
 */
object SelectionMark {
    /**
     * How far a ticked picture is carried towards black: enough to pick it out of a grid of its
     * neighbours at a glance, not so far that a dark photo goes black.
     */
    private const val TINT_ALPHA = 0.25f
    private const val TINT_IN_MS = 160L
    private const val TINT_OUT_MS = 140L

    // the tick grows into place rather than appearing, and starts part grown - out of nothing it
    // reads as a speck thrown at the corner rather than as a tick arriving
    private const val CHECK_START_SCALE = 0.6f
    private const val CHECK_OVERSHOOT = 2f
    private const val CHECK_IN_MS = 180L
    private const val CHECK_OUT_MS = 120L

    /** How much of the theme's contrast colour the tick's rim carries. */
    private const val BORDER_ALPHA = 0.55f
    private const val FULL_ALPHA = 255

    /** What an item's mark was last drawn as, so a rebind can tell a change from a recycled view. */
    private data class MarkState(val key: Any?, val isSelected: Boolean)

    /**
     * Takes a grid's change animation off. Ticking an item rebinds it, and the cross-fade the grid
     * answers a rebind with draws the tile twice - which over a photo reads as a tint that arrives
     * and then leaves again. The mark plays its own movement now and has to be the only one.
     */
    fun settleChangeAnimations(recyclerView: RecyclerView) {
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    /**
     * The circle behind a tick, or behind the count a carried group is drawn with: the theme's
     * accent, rimmed by a hairline of whatever the theme's background contrasts with - light on a
     * dark theme, dark on a light one. The accent alone can land on a photo of nearly its own
     * colour, and the rim is what keeps the circle an object laid on the picture rather than a
     * stain in it. Uncoloured on purpose: a tinted rim reads as a second badge around the first.
     */
    fun circleBackground(context: Context, fillColor: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fillColor)
        setStroke(
            context.resources.getDimensionPixelSize(R.dimen.selection_check_border),
            ColorUtils.setAlphaComponent(
                context.getProperBackgroundColor().getContrastColor(),
                (BORDER_ALPHA * FULL_ALPHA).toInt()
            )
        )
    }

    /**
     * Draws [itemKey]'s mark. [pictures] are whatever the tile shows the file through - one
     * thumbnail, or a folder group's cells - and may be empty where there is nothing to darken, as
     * in the list view.
     *
     * Only a tile that was already on screen in the other state moves; a fresh bind snaps, or
     * scrolling past a selection would set every tick on it animating.
     */
    fun bind(
        itemView: View,
        check: ImageView,
        pictures: List<ImageView>,
        itemKey: Any?,
        isSelected: Boolean,
        fillColor: Int,
        tickColor: Int
    ) {
        val previous = itemView.getTag(R.id.selection_mark_state) as? MarkState
        val animate = previous != null &&
            previous.key == itemKey &&
            previous.isSelected != isSelected

        val state = MarkState(itemKey, isSelected)
        itemView.setTag(R.id.selection_mark_state, state)

        bindCheck(check, isSelected, animate, fillColor, tickColor) {
            itemView.getTag(R.id.selection_mark_state) === state
        }

        bindTint(itemView, pictures, isSelected, animate)
    }

    /**
     * [isCurrent] is what the shrink out asks before hiding the tick: the item may have been
     * recycled onto another file since, whose own mark is already drawn over this one.
     */
    private fun bindCheck(
        check: ImageView,
        isSelected: Boolean,
        animate: Boolean,
        fillColor: Int,
        tickColor: Int,
        isCurrent: () -> Boolean
    ) {
        check.animate().cancel()
        if (isSelected) {
            check.background = circleBackground(check.context, fillColor)
            check.setColorFilter(tickColor)
        }

        if (!animate) {
            check.settle()
            check.beVisibleIf(isSelected)
            return
        }

        if (isSelected) {
            check.beVisible()
            check.alpha = 0f
            check.scaleX = CHECK_START_SCALE
            check.scaleY = CHECK_START_SCALE
            check.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(CHECK_IN_MS)
                .setInterpolator(OvershootInterpolator(CHECK_OVERSHOOT))
                .start()
        } else {
            check.animate()
                .alpha(0f)
                .scaleX(CHECK_START_SCALE)
                .scaleY(CHECK_START_SCALE)
                .setDuration(CHECK_OUT_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    if (isCurrent()) {
                        check.beGone()
                    }

                    check.settle()
                }
                .start()
        }
    }

    private fun View.settle() {
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
    }

    private fun bindTint(
        itemView: View,
        pictures: List<ImageView>,
        isSelected: Boolean,
        animate: Boolean
    ) {
        (itemView.getTag(R.id.selection_mark_animator) as? ValueAnimator)?.cancel()
        itemView.setTag(R.id.selection_mark_animator, null)
        if (pictures.isEmpty()) {
            return
        }

        val target = if (isSelected) TINT_ALPHA else 0f
        if (!animate) {
            pictures.forEach { it.darkenBy(target) }
            return
        }

        ValueAnimator.ofFloat(if (isSelected) 0f else TINT_ALPHA, target).apply {
            duration = if (isSelected) TINT_IN_MS else TINT_OUT_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val darkening = animator.animatedValue as Float
                pictures.forEach { it.darkenBy(darkening) }
            }

            itemView.setTag(R.id.selection_mark_animator, this)
            start()
        }
    }

    /**
     * Darkens the image rather than the view: a filter is drawn only where the drawable is, so the
     * wash follows the thumbnail's rounded corners without being told what they are, and is still
     * there when the picture itself arrives later.
     */
    private fun ImageView.darkenBy(alpha: Float) {
        if (alpha <= 0f) {
            colorFilter = null
        } else {
            setColorFilter(
                ColorUtils.setAlphaComponent(Color.BLACK, (alpha * FULL_ALPHA).toInt()),
                PorterDuff.Mode.SRC_ATOP
            )
        }
    }
}
