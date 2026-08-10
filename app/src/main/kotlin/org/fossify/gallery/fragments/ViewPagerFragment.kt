package org.fossify.gallery.fragments

import android.view.MotionEvent
import androidx.fragment.app.Fragment
import org.fossify.commons.extensions.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Medium
import kotlin.math.abs

abstract class ViewPagerFragment : Fragment() {
    var listener: FragmentListener? = null

    private var mTouchDownTime = 0L
    private var mTouchDownX = 0f
    private var mTouchDownY = 0f
    private var mCloseDownThreshold = 100f
    private var mIgnoreCloseDown = false

    /** Whether the gesture in progress was picked up at its ACTION_DOWN, so its end says anything. */
    private var mTrackingGesture = false

    abstract fun fullscreenToggled(isFullscreen: Boolean)

    interface FragmentListener {
        fun fragmentClicked()

        fun videoEnded(): Boolean

        fun goToPrevItem()

        fun goToNextItem()

        fun launchViewVideoIntent(path: String)

        fun isSlideShowActive(): Boolean

        fun isFullScreen(): Boolean

        /**
         * A swipe up over the media, which pulls the file's metadata in from the bottom - or, with
         * the panel already resting there, opens it the rest of the way.
         */
        fun showMetadata() {}

        /** Whether that panel is currently up over the media. */
        fun isMetadataVisible(): Boolean = false

        /** Puts it away again. */
        fun hideMetadata() {}
    }

    fun getPathToLoad(medium: Medium): String {
        val context = context ?: return medium.path
        return if (context.isPathOnOTG(medium.path)) {
            medium.path.getOTGPublicPath(context)
        } else {
            medium.path
        }
    }

    /**
     * Whether the media is sitting still rather than zoomed or panned, asked of the fragment as a
     * whole rather than of one of its views - see [handleViewerEvent].
     */
    open fun isFlickEligible() = true

    /**
     * Runs the flick detection over an event the fragment's own views did not get to see, which the
     * viewer feeds in from [android.app.Activity.dispatchTouchEvent].
     *
     * Safe to call alongside the views' own listeners: [handleEvent] answers only the first
     * ACTION_UP of a gesture, so whichever path sees the whole gesture takes the flick and the
     * other finds nothing left to do.
     */
    fun handleViewerEvent(event: MotionEvent) = handleEvent(event) { isFlickEligible() }

    /**
     * Turns a vertical flick over the media into a metadata panel or a closed viewer.
     *
     * [isEligible] - "is the media sitting still rather than zoomed or panned" - is asked once per
     * gesture, at its ACTION_DOWN, and that answer holds until the finger lifts. Asking it per event
     * tears a gesture in half whenever it changes under one: a swipe begun while the image is still
     * decoding has its ACTION_DOWN dropped and its ACTION_UP let through, and is then measured
     * against a touch-down that never happened.
     */
    protected fun handleEvent(event: MotionEvent, isEligible: () -> Boolean = { true }) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mTrackingGesture = isEligible()
                mTouchDownTime = System.currentTimeMillis()
                mTouchDownX = event.rawX
                mTouchDownY = event.rawY
            }

            MotionEvent.ACTION_POINTER_DOWN -> mIgnoreCloseDown = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasTracking = mTrackingGesture
                mTrackingGesture = false

                val diffX = mTouchDownX - event.rawX
                val diffY = mTouchDownY - event.rawY

                val downGestureDuration = System.currentTimeMillis() - mTouchDownTime
                val isFlick = wasTracking &&
                    !mIgnoreCloseDown &&
                    abs(diffY) > abs(diffX) &&
                    downGestureDuration < MAX_CLOSE_DOWN_GESTURE_DURATION

                if (isFlick) {
                    // diffY is the distance back towards the top of the screen, so a negative one
                    // is a finger that travelled downwards
                    val flickedDown = diffY < -mCloseDownThreshold
                    val metadataVisible = listener?.isMetadataVisible() == true

                    when {
                        // with the panel up, a flick down asks to be rid of the panel rather than
                        // of the viewer - the thing that came in last is the thing that goes first
                        flickedDown && metadataVisible -> listener?.hideMetadata()

                        flickedDown && context?.config?.allowDownGesture == true -> {
                            activity?.finish()
                            activity?.overridePendingTransition(0, org.fossify.commons.R.anim.slide_down)
                        }

                        // not tied to the down gesture setting: that one is about closing the
                        // viewer by accident, which pulling up a panel cannot do
                        diffY > mCloseDownThreshold -> listener?.showMetadata()
                    }
                }

                mIgnoreCloseDown = false
            }
        }
    }
}
