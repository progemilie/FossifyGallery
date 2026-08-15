package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.gallery.R

/**
 * What a picked up item looks like while it travels, shared by the two grids that let one be
 * dragged - media in `MediaReorderMode`, folder tiles in `FolderDragMode`.
 */

/**
 * Animators of our own rather than the view's animate() builder: the grid's item animator uses that
 * builder for the items it slides around and cancels whatever it finds on it, which would strand a
 * picked up item half lifted. The caller keeps what comes back, to cancel on the next lift.
 */
fun View.animateDragLift(scale: Float, elevation: Float, alpha: Float = 1f): Animator =
    AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@animateDragLift, View.SCALE_X, scale),
            ObjectAnimator.ofFloat(this@animateDragLift, View.SCALE_Y, scale),
            ObjectAnimator.ofFloat(this@animateDragLift, View.TRANSLATION_Z, elevation),
            ObjectAnimator.ofFloat(this@animateDragLift, View.ALPHA, alpha)
        )
        duration = DRAG_LIFT_DURATION_MS
        start()
    }

/**
 * An accent ring following the picture's own corners, [fraction] of its width so it stays in
 * proportion whatever column count the grid is on.
 *
 * [GradientDrawable] draws the stroke inside the bounds it is given, so [insetCorners] pulls the
 * radius in by half of it - at the picture's own radius the ring would come out rounder than the
 * picture and leave its corners sticking out past it.
 */
fun Context.dragAccentRing(
    pictureWidth: Int,
    cornerRadius: Float,
    fraction: Float,
    insetCorners: Boolean = false
): GradientDrawable {
    val strokeWidth = (pictureWidth * fraction).coerceIn(
        resources.getDimension(R.dimen.accent_border_min_width),
        resources.getDimension(R.dimen.accent_border_max_width)
    )

    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        this.cornerRadius = if (insetCorners) {
            (cornerRadius - strokeWidth / 2).coerceAtLeast(0f)
        } else {
            cornerRadius
        }

        setStroke(strokeWidth.toInt(), getProperPrimaryColor())
    }
}

/**
 * Traces the picture inside an item view rather than the view itself: the view carries the padding
 * that spaces the grid out and, on a folder tile, the name and count below the cover, so a shadow
 * around the whole of it would sit off the picture.
 */
fun dragPictureOutline(cornerRadius: () -> Float, picture: (View) -> View?) =
    object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val cover = picture(view) ?: return
            if (cover.width == 0) {
                return
            }

            outline.setRoundRect(cover.left, cover.top, cover.right, cover.bottom, cornerRadius())
        }
    }
