package org.fossify.gallery.helpers

import android.content.Context
import androidx.core.content.edit
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.helpers.SORT_FOLDER_PREFIX
import org.fossify.gallery.extensions.config

/**
 * Where media are sorted or grouped by rating, which is what a scan has to read ratings for, and the
 * one-off move that turned the headers sorting by rating used to bring into a grouping of its own.
 */

private const val RATING_HEADERS_KEPT = "rating_headers_kept"

/** Whether [path]'s media are sorted or grouped by rating. */
fun Config.arrangesByRating(path: String) =
    getFolderSorting(path) and SORT_BY_RATING != 0 || isRatingGrouping(getFolderGrouping(path))

/** Whether anything at all is sorted or grouped by rating: the defaults, or any one folder. */
fun Context.isAnythingArrangedByRating(): Boolean {
    if (config.sorting and SORT_BY_RATING != 0 || isRatingGrouping(config.groupBy)) {
        return true
    }

    return getSharedPrefs().all.any { (key, value) ->
        value is Int && (key.startsWith(SORT_FOLDER_PREFIX) && value and SORT_BY_RATING != 0 ||
            key.startsWith(GROUP_FOLDER_PREFIX) && isRatingGrouping(value))
    }
}

// what Config.getFolderGrouping hands back inside a folder for a default of "by folder" has every
// low bit set, GROUP_BY_NONE among them, and means no grouping at all
private fun isRatingGrouping(grouping: Int) = grouping and GROUP_BY_NONE == 0 && grouping and GROUP_BY_RATING != 0

/**
 * Sorting by rating used to bring rating headers along whatever the grouping said; it sorts like
 * any other sorting now. So once, everywhere that was drawn with those headers is given them as its
 * grouping - nobody's grid changes, and the headers are a setting that can be seen and changed.
 *
 * Every folder with a sorting or grouping of its own keeps what it showed, and so does everything
 * falling back to the defaults, even where only the default grouping changes under a folder.
 */
fun Context.keepRatingHeaders() {
    val prefs = getSharedPrefs()
    if (prefs.getBoolean(RATING_HEADERS_KEPT, false)) {
        return
    }

    val all = prefs.all
    val kept = RatingHeaders.keep(
        defaultSorting = config.sorting,
        defaultGrouping = config.groupBy,
        folderSortings = all.intsUnder(SORT_FOLDER_PREFIX),
        folderGroupings = all.intsUnder(GROUP_FOLDER_PREFIX)
    )

    prefs.edit {
        kept.folderGroupings.forEach { (folder, grouping) -> putInt(GROUP_FOLDER_PREFIX + folder, grouping) }
        kept.defaultGrouping?.let { putInt(GROUP_BY, it) }
        putBoolean(RATING_HEADERS_KEPT, true)
    }
}

private fun Map<String, *>.intsUnder(prefix: String) = entries
    .filter { it.key.startsWith(prefix) && it.value is Int }
    .associate { it.key.removePrefix(prefix) to it.value as Int }

/** The working of [keepRatingHeaders], apart from where the settings are kept. */
internal object RatingHeaders {
    /** The groupings to write: the default's where it changes, and whichever folders' have to. */
    class Kept(val defaultGrouping: Int?, val folderGroupings: Map<String, Int>)

    fun keep(
        defaultSorting: Int,
        defaultGrouping: Int,
        folderSortings: Map<String, Int>,
        folderGroupings: Map<String, Int>,
    ): Kept {
        val newDefaultGrouping = shownGrouping(defaultSorting, defaultGrouping)
        val written = HashMap<String, Int>()
        (folderSortings.keys + folderGroupings.keys).forEach { folder ->
            val sorting = folderSortings[folder] ?: defaultSorting
            val ownGrouping = folderGroupings[folder]
            val shown = shownGrouping(sorting, ownGrouping ?: defaultGrouping)
            if (shown != (ownGrouping ?: newDefaultGrouping)) {
                written[folder] = shown
            }
        }

        return Kept(newDefaultGrouping.takeIf { it != defaultGrouping }, written)
    }
}

/** What a grid sorted by [sorting] used to be drawn grouped into. */
private fun shownGrouping(sorting: Int, grouping: Int) = if (sorting and SORT_BY_RATING != 0) {
    GROUP_BY_RATING or (grouping and GROUP_SHOW_FILE_COUNT) or
        (if (sorting and SORT_DESCENDING != 0) GROUP_DESCENDING else 0)
} else {
    grouping
}
