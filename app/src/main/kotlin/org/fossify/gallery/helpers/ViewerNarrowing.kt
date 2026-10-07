package org.fossify.gallery.helpers

import android.content.Intent
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

/**
 * What the viewer keeps of its folder when it was opened from a grid narrowed by a search - something
 * typed, a filter, or both - so it swipes through the results and nothing else. Handed over in memory
 * the way the grid's list is, as the results' paths: the viewer reads its folder back in more than
 * once, and keeps only these each time.
 *
 * Only a viewer the narrowed grid itself opened asks for them, by the extra [handOver] puts in its
 * intent - any other way in, a shortcut or another app, still sees the whole folder.
 */
class ViewerNarrowing private constructor(private var paths: Set<String>?) {
    fun keeps(path: String) = paths?.contains(path) != false

    /** A file renamed in the viewer is still one of the results, under its new name. */
    fun renamed(oldPath: String, newPath: String) {
        paths = paths?.let { it - oldPath + newPath }
    }

    companion object {
        private const val EXTRA = "narrowed_to_results"
        private var handedOver: Set<String>? = null

        /** Hands the viewer [intent] opens the grid's [results], or nothing for a grid showing everything. */
        fun handOver(intent: Intent, results: List<ThumbnailItem>?) {
            handedOver = results?.filterIsInstance<Medium>()?.mapTo(HashSet()) { it.path }
            intent.putExtra(EXTRA, handedOver != null)
        }

        /** What a viewer opened with [intent] keeps: everything, unless a narrowed grid opened it. */
        fun of(intent: Intent) = ViewerNarrowing(handedOver.takeIf { intent.getBooleanExtra(EXTRA, false) })
    }
}
