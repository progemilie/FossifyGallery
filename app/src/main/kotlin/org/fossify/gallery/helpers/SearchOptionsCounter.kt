package org.fossify.gallery.helpers

import android.os.Handler
import android.os.Looper
import org.fossify.commons.helpers.ensureBackgroundThread

/** Counting one grid's options: set up on the main thread, from what the grid holds then, and run off it. */
typealias OptionsCount = () -> SearchOptions

/**
 * A grid's search options, counted off the main thread from what [prepare] hands over - null while
 * the grid has nothing to count yet. The last count is kept, so an opening search is answered with it
 * at once, and counted again only once the grid's media or the traits read of them have changed since.
 * One count runs at a time: asked for meanwhile, it counts once more when done, from whatever the grid
 * holds by then. Main thread only.
 */
class SearchOptionsCounter(private val prepare: () -> OptionsCount?) {
    private val mainThread = Handler(Looper.getMainLooper())
    private var counted: SearchOptions? = null
    private var isCounting = false
    private var isRecountDue = false

    // what the last count was made from: whether the media have changed since, and the traits as they were
    private var isMediaChanged = false
    private var countedTraits = 0

    // the open search waiting on a count, which a change to the grid is counted again for
    private var wanted: ((SearchOptions) -> Unit)? = null

    private val isOutOfDate get() = counted == null || isMediaChanged || countedTraits != TraitIndex.version

    /** An opening search: answered at once with the last count, and again if the grid has to be counted again. */
    fun load(onLoaded: (SearchOptions) -> Unit) {
        counted?.let(onLoaded)
        wanted = onLoaded
        if (isOutOfDate) {
            count()
        }
    }

    /**
     * What the grid holds has changed: counted again for a search waiting on it. [isSettled] by a
     * scan, it is counted ahead of the first search if it never has been - after the scan rather than
     * alongside it, and still in time for that search's pills to come up with the dim.
     */
    fun onMediaChanged(isSearchOpen: Boolean, isSettled: Boolean = false) {
        isMediaChanged = true
        if (!isSearchOpen) {
            wanted = null
        }

        if (wanted != null || isSettled && counted == null) {
            count()
        }
    }

    private fun count() {
        if (isCounting) {
            isRecountDue = true
            return
        }

        val count = prepare() ?: return
        isCounting = true
        isMediaChanged = false
        countedTraits = TraitIndex.version
        ensureBackgroundThread {
            val options = try {
                Perf.section("search.count", count)
            } catch (ignored: Exception) {
                null
            }

            mainThread.post { onCounted(options) }
        }
    }

    private fun onCounted(options: SearchOptions?) {
        isCounting = false
        if (options == null) {
            isMediaChanged = true
        } else {
            counted = options
            wanted?.invoke(options)
        }

        if (isRecountDue) {
            isRecountDue = false
            if (isOutOfDate) {
                count()
            }
        }
    }
}
