package org.fossify.gallery.models

import android.content.Context
import androidx.annotation.StringRes
import org.fossify.gallery.R

/**
 * One way of narrowing a grid, picked from the options the search puts up under the bar. Only one is
 * ever on at a time, it lasts as long as a search would, and the bar carries it as a chip.
 */
sealed interface SearchFilter {
    data class Kind(val kind: MediaKind) : SearchFilter

    /** Photos taken with one camera, by the name the trait index gives it. */
    data class Device(val name: String) : SearchFilter

    data class Size(val range: SizeRange) : SearchFilter
}

/** The search's Type pills, in the order they are offered. */
enum class MediaKind(@param:StringRes val label: Int) {
    VIDEOS(R.string.videos),
    SELFIES(R.string.selfies),
    PANORAMAS(R.string.panorama),
    SCREENSHOTS(R.string.screenshots),
    GIFS(R.string.gifs),
    RAWS(R.string.raw_images),
    SVGS(R.string.svgs),
    FAVOURITES(org.fossify.commons.R.string.favorites),
}

private const val MEGABYTE = 1024L * 1024

/** The File size pills, in the binary megabytes the app writes every size in. */
enum class SizeRange(private val from: Long, private val until: Long, @param:StringRes val label: Int) {
    UNDER_1_MB(0, MEGABYTE, R.string.size_under_1_mb),
    FROM_1_TO_10_MB(MEGABYTE, 10 * MEGABYTE, R.string.size_1_to_10_mb),
    FROM_10_TO_100_MB(10 * MEGABYTE, 100 * MEGABYTE, R.string.size_10_to_100_mb),
    OVER_100_MB(100 * MEGABYTE, Long.MAX_VALUE, R.string.size_over_100_mb);

    operator fun contains(size: Long) = size in from until until
}

/** What the filter is called on its pill and on the bar's chip. */
fun SearchFilter.label(context: Context): String = when (this) {
    is SearchFilter.Kind -> context.getString(kind.label)
    is SearchFilter.Size -> context.getString(range.label)
    is SearchFilter.Device -> name
}
