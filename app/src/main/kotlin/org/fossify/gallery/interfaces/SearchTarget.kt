package org.fossify.gallery.interfaces

import org.fossify.gallery.helpers.SearchOptions
import org.fossify.gallery.models.SearchFilter

/** What the search's options ask of the grid they narrow. */
interface SearchTarget {
    /** Whether opening the search puts the dim and the options up over this grid. */
    val offersSearchOptions: Boolean get() = true

    /** What this grid is narrowed by, which the bar carries as a chip. */
    val activeFilter: SearchFilter? get() = null

    /** Works out what the search can offer to narrow this grid by, off the main thread, and hands it back on it. */
    fun loadSearchOptions(onLoaded: (SearchOptions) -> Unit)

    /** Narrows the grid by [filter], or takes the filter off for null. */
    fun applyFilter(filter: SearchFilter?)
}
