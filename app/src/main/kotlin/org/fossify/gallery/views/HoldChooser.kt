package org.fossify.gallery.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.DimenRes
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beInvisible
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.realScreenSize
import org.fossify.gallery.R

/**
 * A picker that opens over a bottom action button while it is held, is driven by the same finger
 * without it ever lifting off, and is read back when it lets go. [holdToChoose] is what puts one on
 * a button; a plain tap on that button is left to the button's own click listener.
 *
 * Subclasses say what the finger is currently over. What was picked has to outlive the closing, so
 * the caller can still read it once the chooser is off the screen.
 */
abstract class HoldChooser @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GlassPanel(context, attrs, defStyleAttr) {

    /** How much room to leave between the chooser and the end of the screen it opens towards. */
    @get:DimenRes
    protected abstract val endMarginId: Int

    /** Re-reads what the finger at these screen coordinates is over. */
    abstract fun updateSelectionFor(rawX: Float, rawY: Float)

    /** The chooser is going away, for anything a subclass has running while it is up. */
    protected open fun onChooserClosed() = Unit

    /** GONE rather than not-VISIBLE, since a chooser spends its first frame laid out but not drawn. */
    val isChooserUp get() = !isGone()

    /** Lays the chooser out unseen, positions it over [button] and only then draws it. */
    fun revealOver(button: View) {
        beInvisible()
        post {
            position(button)
            beVisible()
        }
    }

    /**
     * Where the chooser sits once it has been measured. Sideways alone by default, which is all a
     * chooser laid out along the bottom of the screen needs - one hanging off a button at the top
     * has to place itself down the screen as well.
     */
    protected open fun position(button: View) = centerOver(button)

    fun close() {
        onChooserClosed()
        beGone()
    }

    protected fun centerOver(button: View) {
        if (width == 0) {
            return
        }

        val buttonLocation = IntArray(2)
        button.getLocationOnScreen(buttonLocation)
        val chooserLocation = IntArray(2)
        getLocationOnScreen(chooserLocation)

        val untranslatedLeft = chooserLocation[0] - translationX
        val wanted = buttonLocation[0] + button.width / 2f - width / 2f
        val furthestLeft = resources.getDimensionPixelSize(R.dimen.chooser_edge_margin).toFloat()
        val endMargin = resources.getDimensionPixelSize(endMarginId)
        val furthestRight = maxOf(furthestLeft, (context.realScreenSize.x - width - endMargin).toFloat())
        translationX = wanted.coerceIn(furthestLeft, furthestRight) - untranslatedLeft
    }
}

/**
 * Answers a hold on this button with [chooser] and leaves a tap to the button's own click listener,
 * so the same button offers the picker to a finger that stays down and the usual dialog to one that
 * does not.
 *
 * [onOpen] fills the chooser and answers false when it has nothing to offer, in which case the hold
 * is ignored and letting go still counts as a tap. [onChosen] runs once the chooser has been taken
 * away - what was picked is still readable off it by then.
 */
// the listener does call performClick() on a tap, which is the thing this check exists to make sure
// of - it just cannot see that through the lambda
@SuppressLint("ClickableViewAccessibility")
fun View.holdToChoose(chooser: HoldChooser, onOpen: () -> Boolean, onChosen: () -> Unit) {
    var openRunnable: Runnable? = null

    fun cancelOpening() {
        openRunnable?.let { removeCallbacks(it) }
        openRunnable = null
    }

    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.isPressed = true
                view.parent?.requestDisallowInterceptTouchEvent(true)
                openRunnable = Runnable {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    if (onOpen()) {
                        chooser.revealOver(view)
                    }
                }.also { view.postDelayed(it, ViewConfiguration.getLongPressTimeout().toLong()) }
            }

            MotionEvent.ACTION_MOVE -> {
                if (chooser.isChooserUp) {
                    chooser.updateSelectionFor(event.rawX, event.rawY)
                }
            }

            MotionEvent.ACTION_UP -> {
                view.isPressed = false
                cancelOpening()
                if (chooser.isChooserUp) {
                    // taken away before acting on it: what follows may be a dialog, and the chooser
                    // has no business sitting there behind one
                    chooser.close()
                    onChosen()
                } else {
                    // through performClick rather than straight to the dialog, so the tap is still
                    // announced to accessibility services
                    view.performClick()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                cancelOpening()
                chooser.close()
            }
        }

        true
    }
}
