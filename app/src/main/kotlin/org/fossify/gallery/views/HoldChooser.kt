package org.fossify.gallery.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.DimenRes
import org.fossify.commons.extensions.beInvisible
import org.fossify.commons.extensions.realScreenSize
import org.fossify.gallery.R
import org.fossify.gallery.helpers.PanelMotion
import org.fossify.gallery.helpers.PanelPivot
import org.fossify.gallery.helpers.clearPanelMotion
import org.fossify.gallery.helpers.hidePanel
import org.fossify.gallery.helpers.showPanel

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

    /** How this chooser comes and goes. See [PanelMotion]. */
    var motion = PanelMotion.GROW

    // whether the finger is still driving this chooser, which stops the moment it is closed rather
    // than when it finishes leaving - the button it hangs off may be pressed again before the fade
    // is over, and a chooser still fading counts as gone
    private var isOpen = false

    // where the last reveal grew the chooser out of, so it goes back into the same place
    private var openPivot = PanelPivot.CENTER

    /** Whether the chooser is up and being read from. */
    val isChooserUp get() = isOpen

    /** Lays the chooser out unseen, positions it over [button] and only then draws it. */
    fun revealOver(button: View) {
        isOpen = true
        animate().cancel()
        beInvisible()
        post {
            // a hold let go of inside the one frame this waits for
            if (!isOpen) {
                return@post
            }

            // placed untransformed: a chooser caught still leaving would otherwise be positioned by
            // its shrunken self, and the pivot worked out off the wrong rectangle
            clearPanelMotion()
            position(button)
            openPivot = PanelPivot.over(this, button)
            showPanel(motion, openPivot)
        }
    }

    /**
     * Where the chooser sits once it has been measured. Sideways alone by default, which is all a
     * chooser laid out along the bottom of the screen needs - one hanging off a button at the top
     * has to place itself down the screen as well.
     */
    protected open fun position(button: View) = centerOver(button)

    fun close() {
        if (!isOpen) {
            return
        }

        isOpen = false
        onChooserClosed()
        hidePanel(motion, openPivot)
    }

    protected fun centerOver(button: View) {
        placeLeftEdgeAt(button.centerX() - width / 2f)
    }

    /** Where on the screen [button] is across, which is what a chooser places itself against. */
    protected fun View.centerX(): Float {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[0] + width / 2f
    }

    /** Puts the chooser's left hand edge here on the screen, or as near to it as the margins allow. */
    protected fun placeLeftEdgeAt(wantedLeft: Float) {
        if (width == 0) {
            return
        }

        val chooserLocation = IntArray(2)
        getLocationOnScreen(chooserLocation)

        val untranslatedLeft = chooserLocation[0] - translationX
        val furthestLeft = resources.getDimensionPixelSize(R.dimen.chooser_edge_margin).toFloat()
        val endMargin = resources.getDimensionPixelSize(endMarginId)
        val furthestRight = maxOf(furthestLeft, (context.realScreenSize.x - width - endMargin).toFloat())
        translationX = wantedLeft.coerceIn(furthestLeft, furthestRight) - untranslatedLeft
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
