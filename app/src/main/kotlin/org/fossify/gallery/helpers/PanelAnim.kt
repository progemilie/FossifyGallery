package org.fossify.gallery.helpers

import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible

/**
 * The one way a piece of the app's chrome comes and goes. Everything made of glass - the choosers
 * held over a bottom action button, the settings cards - opens through [showPanel] and closes
 * through [hidePanel], so a panel's motion is switched by naming a different [PanelMotion] at its
 * one call site rather than by rewriting the animation.
 *
 * The numbers are the platform's own drop-down animation, which the three dots' popup
 * ([org.fossify.gallery.views.GlassMenu]) still gets for free and everything else is matched to: a
 * short grow out of whatever opened it, under a fade.
 */
enum class PanelMotion {
    /** Grows out of the edge nearest what opened it, under a fade. */
    GROW,

    /** The fade alone, for a panel with nothing in particular to have come out of. */
    FADE,

    /** Straight on and straight off, for a panel that has to be there in the frame it is asked for. */
    NONE,
}

/**
 * Where a panel's motion is centred, as a fraction of its own width and height - (0.5, 1) is the
 * middle of its bottom edge. A fraction rather than a pixel offset so it survives the panel being
 * measured to a different size than it was when this was worked out.
 */
data class PanelPivot(val x: Float, val y: Float) {
    companion object {
        val CENTER = PanelPivot(0.5f, 0.5f)
        val TOP = PanelPivot(0.5f, 0f)

        /** The middle of a panel's bottom edge - what one standing on the foot grows out of. */
        val BOTTOM = PanelPivot(0.5f, 1f)

        /**
         * The point on [panel] nearest [anchor]'s middle: where a panel opened from a button grows
         * out of. An anchor off to one side or wholly below the panel clamps to the nearest edge,
         * which is the usual case - a chooser opening upward out of a bottom action button.
         */
        fun over(panel: View, anchor: View): PanelPivot {
            if (panel.width == 0 || panel.height == 0) {
                return CENTER
            }

            val panelAt = IntArray(2).also { panel.getLocationOnScreen(it) }
            val anchorAt = IntArray(2).also { anchor.getLocationOnScreen(it) }
            val x = (anchorAt[0] + anchor.width / 2f - panelAt[0]) / panel.width
            val y = (anchorAt[1] + anchor.height / 2f - panelAt[1]) / panel.height
            return PanelPivot(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }
    }
}

/** How long a panel takes to arrive, and to leave - leaving is quicker, as it always is. */
const val PANEL_ENTER_MS = 180L
const val PANEL_EXIT_MS = 140L

// how small a panel starts, and how far back it goes on the way out. A grow that starts much
// further down reads as a zoom rather than as the panel settling into place
private const val GROW_FROM = 0.92f
private const val SHRINK_TO = 0.96f

/**
 * Puts this panel up with [motion], growing out of [pivot]. Safe to call on a panel already up or
 * still on its way out - whatever it was doing is dropped for this.
 *
 * Nothing here touches translation: a panel places itself against what opened it with translationX
 * and translationY, and an animation that moved those would carry it off the button it belongs to.
 */
fun View.showPanel(motion: PanelMotion = PanelMotion.GROW, pivot: PanelPivot = PanelPivot.CENTER) {
    animate().cancel()
    beVisible()
    if (motion == PanelMotion.NONE) {
        clearPanelMotion()
        return
    }

    applyPivot(pivot)
    alpha = 0f
    if (motion == PanelMotion.GROW) {
        scaleX = GROW_FROM
        scaleY = GROW_FROM
    }

    animate()
        .alpha(1f)
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(PANEL_ENTER_MS)
        .setInterpolator(enterCurve())
        .start()
}

/**
 * Takes this panel away with [motion] and runs [onGone] once it is off the screen. Anything reading
 * the panel back has to do so before this, not in [onGone] - the panel is still up for the length
 * of the animation, and the caller has usually moved on by the time it ends.
 */
fun View.hidePanel(
    motion: PanelMotion = PanelMotion.GROW,
    pivot: PanelPivot = PanelPivot.CENTER,
    onGone: (() -> Unit)? = null,
) {
    animate().cancel()
    if (motion == PanelMotion.NONE || visibility != View.VISIBLE) {
        clearPanelMotion()
        beGone()
        onGone?.invoke()
        return
    }

    applyPivot(pivot)
    val shrink = if (motion == PanelMotion.GROW) SHRINK_TO else 1f
    animate()
        .alpha(0f)
        .scaleX(shrink)
        .scaleY(shrink)
        .setDuration(PANEL_EXIT_MS)
        .setInterpolator(exitCurve())
        .withEndAction {
            clearPanelMotion()
            beGone()
            onGone?.invoke()
        }
        .start()
}

/** How much bigger the thing a finger is currently over is drawn than the rest of the list. */
const val CHOICE_SWELL = 1.25f

/** What a row carrying a whole line of text swells by instead. */
const val LINE_CHOICE_SWELL = 1.12f

// quick: this is the answer to a finger still moving, and anything slower would still be catching
// up as it passed the next one
private const val CHOICE_MS = 120L

/**
 * Swells this into the thing that would be picked if the finger let go now, or lets it settle back.
 * The one gesture every chooser held open over a button answers with - the row of stars, the folder
 * list, the tabs - so all three swell over the same time and off the same pair of numbers.
 *
 * Called on the label or the icon and never on the plate behind it: a highlight drawn bigger than
 * the row it belongs to is a highlight whose rounded corners have been clipped off. Rows already
 * where they belong are left alone, these being repainted in full on every crossing.
 */
fun View.markChosen(chosen: Boolean, swell: Float = CHOICE_SWELL) {
    val target = if (chosen) swell else 1f
    if (scaleX == target) {
        return
    }

    animate().cancel()
    animate()
        .scaleX(target)
        .scaleY(target)
        .setDuration(CHOICE_MS)
        .setInterpolator(enterCurve())
        .start()
}

/** Puts the panel back the way an untouched one looks, for anything measuring or placing it. */
fun View.clearPanelMotion() {
    alpha = 1f
    scaleX = 1f
    scaleY = 1f
}

private fun View.applyPivot(pivot: PanelPivot) {
    pivotX = width * pivot.x
    pivotY = height * pivot.y
}

internal fun View.enterCurve(): Interpolator =
    AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in)

private fun View.exitCurve(): Interpolator =
    AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_linear_in)
