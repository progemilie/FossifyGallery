package org.fossify.gallery.views

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beInvisible
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.gallery.R
import org.fossify.gallery.helpers.Glass
import org.fossify.commons.R as commonsR

/**
 * The cross that closes a tab: a circle of glass of its own, held beside the row it belongs to
 * rather than inside [TabChooser].
 *
 * Beside rather than within because it is a second thing to aim at - the list stays a column of
 * numbers, and only a finger that has left it entirely is asking to close anything. A panel clips to
 * its own rounded rect, so a separate circle is also the only way to have one at all.
 *
 * It is laid out at its parent's top left and translated to where it belongs by [growInAt], the way
 * every other chooser places itself.
 */
class TabCloseButton(context: Context) : GlassPanel(context) {

    /** The circle is square and never measured before it is placed, so its size is known up front. */
    val size = resources.getDimensionPixelSize(R.dimen.tab_chooser_close_size)

    private val cross = ImageView(context)

    // where it was last put, in screen coordinates. Kept rather than read back off the view, which
    // reports where the grow-in animation has scaled it to rather than where it is going to land
    private var placedLeft = 0f
    private var placedTop = 0f

    init {
        cornerRadius = size / 2f
        blurRadius = Glass.CHOOSER_RADIUS
        elevation = resources.getDimension(R.dimen.chooser_elevation)
        contentDescription = context.getString(R.string.close_tab)

        resources.getDimensionPixelSize(R.dimen.tab_chooser_close_padding).let {
            cross.setPadding(it, it, it, it)
        }

        cross.setImageResource(commonsR.drawable.ic_cross_vector)
        addView(
            cross,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
    }

    /**
     * Lays the circle out beside [chooser] - in the same parent, added after it so it draws over it,
     * frosting the same [contentToFrost] - after which it is only ever placed and shown.
     */
    fun attachBeside(chooser: View, contentToFrost: ViewGroup?) {
        if (parent != null) {
            return
        }

        val host = chooser.parent as? ViewGroup ?: return
        hide()
        host.addView(this, ViewGroup.LayoutParams(size, size))
        contentToFrost?.let { frost(it) }
    }

    /**
     * Puts the circle's centre at [centreY] with its right hand edge at [rightEdge] on the screen,
     * and grows it into shape there - quickly, since it turns up under a finger already resting on
     * the row it belongs to.
     */
    fun growInAt(rightEdge: Float, centreY: Float) {
        val origin = untranslatedTopLeft()
        placedLeft = rightEdge - size
        placedTop = centreY - size / 2f
        translationX = placedLeft - origin[0]
        translationY = placedTop - origin[1]

        animate().cancel()
        paint(isUnderFinger = false)
        beVisible()
        scaleX = GROW_FROM
        scaleY = GROW_FROM
        alpha = 0f
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(GROW_MS)
            .setInterpolator(OvershootInterpolator(GROW_OVERSHOOT))
            .start()
    }

    /** INVISIBLE rather than GONE: laid out even while unseen, so [growInAt] knows where it is. */
    fun hide() {
        animate().cancel()
        beInvisible()
    }

    /** Repaints from the theme, which is also how it says whether the finger has reached it. */
    fun paint(isUnderFinger: Boolean) {
        updateColors()
        cross.applyColorFilter(
            if (isUnderFinger) context.getProperPrimaryColor() else Glass.contentColor(context)
        )
    }

    /**
     * Whether a finger here has reached the circle. Nothing bounds it on the left: it is come at
     * from the list on its right, and overshooting past it still means the same thing.
     */
    fun isReachedBy(rawX: Float, rawY: Float, verticalSlop: Float) = isVisible
        && rawX <= placedLeft + size
        && rawY >= placedTop - verticalSlop
        && rawY <= placedTop + size + verticalSlop

    // where the parent laid it out, worked out through the parent rather than off its own reported
    // position, which the scale of the grow-in is folded into
    private fun untranslatedTopLeft(): FloatArray {
        val parent = parent as? View ?: return floatArrayOf(0f, 0f)
        val parentLocation = IntArray(2)
        parent.getLocationOnScreen(parentLocation)
        return floatArrayOf((parentLocation[0] + left).toFloat(), (parentLocation[1] + top).toFloat())
    }

    private companion object {
        /** How small it starts, how long it takes, and how far past its size it swings on the way. */
        const val GROW_FROM = 0.3f
        const val GROW_MS = 160L
        const val GROW_OVERSHOOT = 2.5f
    }
}
