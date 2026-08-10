package org.fossify.gallery.helpers

import android.content.Intent
import org.fossify.gallery.adapters.MediaGridNavigator

/**
 * Points out the thumbnail the fullscreen viewer was just left from, which is easy to lose track of
 * after swiping through a folder. The viewer hands back the path it ended up on; until it does, the
 * thumbnail that was tapped stands.
 *
 * Kept apart from the grid screens so both of them - and any later one that opens the viewer - come
 * back the same way.
 */
class ViewerReturn {
    companion object {
        /** What the grid screens start the viewer with, so its result is recognised on the way back. */
        const val REQUEST_CODE = 1
    }

    private var path = ""
    private var isPending = false

    /** The viewer is being opened on [path]; that is where to look on the way back. */
    fun opening(path: String) {
        this.path = path
        isPending = true
    }

    /** Takes the path the viewer swiped to off its result, if it had one to give. */
    fun onViewerResult(data: Intent?) {
        val viewedPath = data?.getStringExtra(PATH).orEmpty()
        if (viewedPath.isNotEmpty()) {
            path = viewedPath
        }
    }

    /**
     * Points the item out if the grid has it. The request stays pending otherwise - a refresh still
     * on its way may yet bring the item in.
     */
    fun reveal(gridNavigator: MediaGridNavigator?) {
        if (isPending && gridNavigator?.revealItem(path) == true) {
            isPending = false
        }
    }
}
