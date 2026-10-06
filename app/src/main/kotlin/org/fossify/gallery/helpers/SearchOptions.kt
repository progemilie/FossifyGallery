package org.fossify.gallery.helpers

import android.content.Context
import android.provider.MediaStore
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.queryCursor
import org.fossify.gallery.models.MediaKind
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.SearchFilter
import org.fossify.gallery.models.SizeRange
import java.io.File

/**
 * What the search can offer to narrow one grid by. Only what would change the grid is offered: a
 * kind nothing is, or that everything already is, has no pill.
 */
data class SearchOptions(
    val kinds: List<MediaKind> = emptyList(),
    /** Most photos first. */
    val devices: List<String> = emptyList(),
    val sizes: List<SizeRange> = emptyList(),
) {
    val isEmpty get() = kinds.isEmpty() && devices.isEmpty() && sizes.isEmpty()
}

/** What the trait index has read out of one photo's metadata. */
data class PhotoTraits(val device: String, val isSelfie: Boolean, val isPanorama: Boolean)

/** The trait index's answer for a path, null for anything it has not read. */
fun interface TraitLookup {
    operator fun get(path: String): PhotoTraits?

    companion object {
        val NONE = TraitLookup { null }
    }
}

/**
 * What the options and filters need to know of a file beyond what a scan put in its [Medium]: what
 * the trait index has read out of it, and its size - which a folder scan only fills in when sorting
 * by size, leaving the rest at 0. Those come out of MediaStore in one query, the first time a size is
 * asked for. Blocking, use it off the main thread.
 */
class MediaFacts(private val context: Context, private val traits: TraitLookup) {
    private val storeSizes by lazy { context.mediaStoreSizes() }

    fun traitsOf(medium: Medium) = traits[medium.path]

    fun sizeOf(medium: Medium): Long =
        medium.size.takeIf { it > 0 } ?: storeSizes[medium.path] ?: File(medium.path).length()
}

/** Counts what [media] holds of each option. Linear in [media], so off the main thread for a library. */
fun searchOptionsOf(media: List<Medium>, facts: MediaFacts): SearchOptions {
    val kinds = IntArray(MediaKind.entries.size)
    val sizes = IntArray(SizeRange.entries.size)
    val devices = HashMap<String, Int>()
    for (medium in media) {
        val photo = facts.traitsOf(medium)
        MediaKind.entries.forEach {
            if (medium.isOfKind(it, photo)) kinds[it.ordinal]++
        }

        val size = facts.sizeOf(medium)
        SizeRange.entries.firstOrNull { size in it }?.let { sizes[it.ordinal]++ }
        photo?.device?.takeIf { it.isNotEmpty() }?.let { devices[it] = (devices[it] ?: 0) + 1 }
    }

    val narrows = 1 until media.size
    return SearchOptions(
        kinds = MediaKind.entries.filter { kinds[it.ordinal] in narrows },
        devices = devices.filterValues { it in narrows }.entries
            .sortedByDescending { it.value }
            .map { it.key },
        sizes = SizeRange.entries.filter { sizes[it.ordinal] in narrows },
    )
}

/** Whether [medium] is let through by this filter. */
fun SearchFilter.matches(medium: Medium, facts: MediaFacts): Boolean = when (this) {
    is SearchFilter.Kind -> medium.isOfKind(kind, facts.traitsOf(medium))
    is SearchFilter.Device -> facts.traitsOf(medium)?.device == name
    is SearchFilter.Size -> facts.sizeOf(medium) in range
}

private fun Medium.isOfKind(kind: MediaKind, photo: PhotoTraits?) = when (kind) {
    MediaKind.VIDEOS -> isVideo()
    MediaKind.SELFIES -> photo?.isSelfie == true
    MediaKind.PANORAMAS -> photo?.isPanorama == true || name.isPanoramaName()
    MediaKind.SCREENSHOTS -> path.isScreenshotPath()
    MediaKind.GIFS -> isGIF()
    MediaKind.RAWS -> isRaw()
    MediaKind.SVGS -> isSVG()
    MediaKind.FAVOURITES -> isFavorite
}

// the folders screenshots and screen recordings are saved to - AOSP, Samsung, Xiaomi and the like
private val SCREENSHOT_FOLDER =
    Regex("screenshots?|screen ?(recordings?|records?|recorder|captures?)", RegexOption.IGNORE_CASE)

// and the names they are given wherever they end up: Screenshot_…, AOSP's screen-…, Screen_Recording_…
private val SCREENSHOT_NAME = Regex("^(screenshot|screen[-_ ]?(record|capture)|screen-\\d)", RegexOption.IGNORE_CASE)

/** A screenshot or a screen recording, by where it was saved or what it was called. */
fun String.isScreenshotPath() =
    SCREENSHOT_FOLDER.matches(getParentPath().getFilenameFromPath()) ||
        SCREENSHOT_NAME.containsMatchIn(getFilenameFromPath())

// Google Camera's PANO_…, and the Pixel's …PANO.jpg and …PHOTOSPHERE.jpg
private val PANORAMA_NAME = Regex("^pano_|[._]pano\\.|[._]photosphere\\.", RegexOption.IGNORE_CASE)

/** A panorama the camera said was one in the name it gave the file. */
fun String.isPanoramaName() = PANORAMA_NAME.containsMatchIn(this)

/** Every photo's and video's size MediaStore knows, by path. Blocking. */
private fun Context.mediaStoreSizes(): Map<String, Long> {
    val sizes = HashMap<String, Long>()
    val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE)
    val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
    val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
    )

    queryCursor(MediaStore.Files.getContentUri("external"), projection, selection, selectionArgs) { cursor ->
        try {
            val size = cursor.getLongValue(MediaStore.MediaColumns.SIZE)
            if (size > 0) {
                sizes[cursor.getStringValue(MediaStore.MediaColumns.DATA)] = size
            }
        } catch (ignored: Exception) {
        }
    }

    return sizes
}
