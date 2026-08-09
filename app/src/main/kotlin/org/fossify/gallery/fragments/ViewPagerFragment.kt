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

    protected fun handleEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mTouchDownTime = System.currentTimeMillis()
                mTouchDownX = event.rawX
                mTouchDownY = event.rawY
            }

            MotionEvent.ACTION_POINTER_DOWN -> mIgnoreCloseDown = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val diffX = mTouchDownX - event.rawX
                val diffY = mTouchDownY - event.rawY

                val downGestureDuration = System.currentTimeMillis() - mTouchDownTime
                val isFlick = !mIgnoreCloseDown &&
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
