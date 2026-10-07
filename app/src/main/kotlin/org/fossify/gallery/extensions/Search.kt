package org.fossify.gallery.extensions

import android.content.Context
import android.provider.MediaStore
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.queryCursor
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.helpers.MediaFacts
import org.fossify.gallery.helpers.TraitIndex
import org.fossify.gallery.helpers.TraitLookup
import org.fossify.gallery.interfaces.SearchDao
import org.fossify.gallery.models.Medium

val Context.searchDB: SearchDao get() = GalleryDatabase.getInstance(applicationContext).SearchDao()

/**
 * The library as Albums shows it - every file in [folders] the scans have seen - which is what a pill
 * picked there narrows Pictures by. Blocking, call it off the main thread.
 */
fun Context.libraryMedia(folders: Set<String>): List<Medium> = try {
    searchDB.getLibrary().filter { it.parentPath in folders }
} catch (ignored: Exception) {
    emptyList()
}

/** What the trait index has read so far, for the search's options and filters. Blocking the first time. */
fun Context.traitLookup(): TraitLookup = TraitIndex.lookup(this)

/** Everything the search's options and filters ask of a file beyond its [Medium]. Blocking the first time. */
fun Context.mediaFacts(): MediaFacts {
    val context = applicationContext
    return MediaFacts(traitLookup()) { context.mediaStoreSizes() }
}

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
