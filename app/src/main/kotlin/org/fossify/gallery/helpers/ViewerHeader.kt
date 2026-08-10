package org.fossify.gallery.helpers

import android.app.Activity
import android.os.Handler
import android.os.Looper
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.databinding.ViewerHeaderBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getMediumExtendedDetails
import org.fossify.gallery.extensions.joinAsExtendedDetails
import org.fossify.gallery.models.Medium

/**
 * The heading a viewer shows in place of a toolbar title: the file name, with the extended details
 * packed onto the lines below it when they are turned on. Sitting inside the toolbar means the fade
 * to fullscreen takes the whole heading with it.
 */
class ViewerHeader(private val activity: Activity, private val binding: ViewerHeaderBinding) {
    companion object {
        /**
         * Long enough to sit out the photos a scrolling thumbnail strip passes over, short enough
         * that landing on one and reading about it feels like the same moment.
         */
        const val DETAILS_DELAY = 200L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingDetails: Runnable? = null

    fun setFilename(name: String) {
        binding.viewerHeaderFilename.text = name
    }

    /**
     * Fills in the lines under the name, off the main thread. Reading the details opens the file,
     * which is why it is held back by [delay] first - scrolling a thumbnail strip crosses photos
     * faster than any of them can be opened, and only the one still on screen when that pauses is
     * worth opening.
     *
     * [isStillCurrent] is asked again with the details in hand: a fast swipe can land several reads
     * out of order, and only the one describing what is on screen gets to write itself in.
     */
    fun showDetails(
        medium: Medium?,
        delay: Long = DETAILS_DELAY,
        isStillCurrent: () -> Boolean = { true },
    ) {
        val detailsView = binding.viewerHeaderDetails
        pendingDetails?.let { handler.removeCallbacks(it) }
        if (medium == null || !activity.config.showExtendedDetails) {
            detailsView.beGone()
            return
        }

        pendingDetails = Runnable {
            ensureBackgroundThread {
                val details = activity
                    .getMediumExtendedDetails(medium, skipName = true)
                    .joinAsExtendedDetails()

                activity.runOnUiThread {
                    if (isStillCurrent()) {
                        detailsView.text = details
                        detailsView.beVisibleIf(details.isNotEmpty())
                    }
                }
            }
        }.also { handler.postDelayed(it, delay) }
    }
}
