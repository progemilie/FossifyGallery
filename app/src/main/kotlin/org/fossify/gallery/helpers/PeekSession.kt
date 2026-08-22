package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium

/**
 * What the peek viewer is opened on and what it hands back, held in memory rather than put in the
 * intent that starts it: a folder runs to thousands of paths, well past what a binder transaction
 * carries. It is the same handoff [org.fossify.gallery.activities.ViewPagerActivity] already makes
 * through `MediaActivity.mMedia`.
 *
 * [selectedPaths] is the one thing the viewer writes; the grid reads it back on the way in.
 */
object PeekSession {
    /** The media the grid is showing, in its order - which a search narrows. */
    var media: List<Medium> = emptyList()

    /** Which of them are selected. The viewer edits this set in place. */
    var selectedPaths = LinkedHashSet<String>()

    /** The item the peek button was pressed on. */
    var startPath = ""

    /** Empty means the process was killed mid peek: there is nothing left to show. */
    fun isEmpty() = media.isEmpty()

    fun open(media: List<Medium>, selectedPaths: Set<String>, startPath: String) {
        this.media = media
        this.selectedPaths = LinkedHashSet(selectedPaths)
        this.startPath = startPath
    }

    fun clear() {
        media = emptyList()
        selectedPaths = LinkedHashSet()
        startPath = ""
    }
}
