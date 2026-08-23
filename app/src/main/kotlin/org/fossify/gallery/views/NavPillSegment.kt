package org.fossify.gallery.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.gallery.R

/** The wash behind the segment whose screen is up. */
private const val PLATE_ALPHA = 0.15f

/** How much more of the wash a tap lights up, over whatever the segment is already wearing. */
private const val FLASH_ALPHA = 0.18f

/** The whole answer to a tap, and the share of it the box spends being pressed in. */
private const val TAP_DURATION = 420L
private const val PRESS_SHARE = 0.22f

/** The share of the tap the wash spends lighting up; the rest of it is the fade back to the plate. */
private const val FLASH_RISE = 0.15f

// how far the box is pressed in, and the spring that carries it back out past its own size
private const val PRESSED_SCALE = 0.94f
private const val RELEASE_TENSION = 4f

/**
 * One of the pill's three boxes - an icon over a label - and how it answers being tapped: a flash of
 * the wash behind it and a small bounce of the box, which carries the plate, the icon and the label
 * with it, since scaling a group scales everything it draws.
 *
 * The platform's ripple is what this replaces. It is drawn square against a pill that is anything
 * but, having no idea about the rounded plate the segment wears.
 */
class NavPillSegment @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val pressIn = DecelerateInterpolator()
    private val springBack = OvershootInterpolator(RELEASE_TENSION)

    // one animator for both halves of the feedback, so a second tap has a single thing to cut short
    private var tap: ValueAnimator? = null

    /** What the wash is painted in - the panel's content colour - with its weight worked out here. */
    private var washColor = Color.TRANSPARENT

    /** Whether this is the screen you are on, and so wears the plate rather than nothing. */
    private var isCurrent = false

    init {
        // mutated, or tinting one segment's plate would tint all three: they share a constant state
        background = ContextCompat.getDrawable(context, R.drawable.nav_pill_plate)?.mutate()
    }

    /** Repainted whenever the pill is, since [color] is worked out from the theme. */
    fun paintWash(color: Int, isCurrent: Boolean) {
        washColor = color
        this.isCurrent = isCurrent
        drawWash(flash = 0f)
    }

    /**
     * The framework's own press state rather than a touch listener of ours: it already knows that a
     * press slid away from is not a tap, and it is the moment a ripple would have started.
     */
    override fun setPressed(pressed: Boolean) {
        val wasPressed = isPressed
        super.setPressed(pressed)
        if (pressed && !wasPressed) {
            playTapFeedback()
        }
    }

    // a tap can outlive the screen it was aimed at, the two grids being activities that finish each
    // other; an animator still running holds on to the view it is scaling
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        tap?.cancel()
        scaleX = 1f
        scaleY = 1f
    }

    private fun playTapFeedback() {
        tap?.cancel()
        tap = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TAP_DURATION
            // the shaping is all below, and an easing of the animator's own would distort it
            interpolator = LinearInterpolator()
            addUpdateListener {
                val elapsed = it.animatedFraction
                val scale = scaleAt(elapsed)
                drawWash(flashAt(elapsed))
                scaleX = scale
                scaleY = scale
            }
            start()
        }
    }

    /** Up quickly, back down slowly: a flash, rather than a highlight held for as long as it took. */
    private fun flashAt(elapsed: Float) = if (elapsed < FLASH_RISE) {
        elapsed / FLASH_RISE
    } else {
        1f - (elapsed - FLASH_RISE) / (1f - FLASH_RISE)
    }

    private fun scaleAt(elapsed: Float) = if (elapsed < PRESS_SHARE) {
        lerp(1f, PRESSED_SCALE, pressIn.getInterpolation(elapsed / PRESS_SHARE))
    } else {
        lerp(PRESSED_SCALE, 1f, springBack.getInterpolation((elapsed - PRESS_SHARE) / (1f - PRESS_SHARE)))
    }

    private fun lerp(from: Float, to: Float, fraction: Float) = from + (to - from) * fraction

    private fun drawWash(flash: Float) {
        val plate = if (isCurrent) PLATE_ALPHA else 0f
        background?.setTint(washColor.adjustAlpha((plate + flash * FLASH_ALPHA).coerceAtMost(1f)))
    }
}
