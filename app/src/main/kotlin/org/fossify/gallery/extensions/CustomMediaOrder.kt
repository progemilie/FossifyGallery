package org.fossify.gallery.extensions

import android.content.Context
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.interfaces.MediaOrderDao
import java.util.Locale

// Where a folder's hand made media order is kept: the arranged paths in the media_order table, and
// an index of which folders have one in Config, which is what the main thread can ask.

val Context.mediaOrderDB: MediaOrderDao
    get() = GalleryDatabase.getInstance(applicationContext).MediaOrderDao()

/**
 * Persists the order the user arranged [paths] in for [path], and marks the folder as custom
 * ordered so [Config.hasCustomMediaOrder] can answer without touching the database. Blocking, call
 * it off the main thread.
 */
fun Context.saveCustomMediaOrder(path: String, paths: List<String>) {
    val folderPath = path.lowercase(Locale.getDefault())
    mediaOrderDB.replaceFolderOrder(folderPath, paths)
    config.addCustomMediaOrderFolder(folderPath)
}

/**
 * The saved order of [path] as a path -> position map, empty if the folder has none. Blocking,
 * call it off the main thread.
 */
fun Context.getCustomMediaOrder(path: String): Map<String, Int> {
    val folderPath = path.lowercase(Locale.getDefault())
    if (!config.hasCustomMediaOrder(folderPath)) {
        return emptyMap()
    }

    val positions = HashMap<String, Int>()
    try {
        mediaOrderDB.getOrderedPaths(folderPath).forEachIndexed { index, mediumPath ->
            positions[mediumPath.lowercase(Locale.getDefault())] = index
        }
    } catch (ignored: Exception) {
    }

    // the index and the table drifted apart somehow, do not keep claiming an order that is not there
    if (positions.isEmpty()) {
        config.removeCustomMediaOrderFolder(folderPath)
    }

    return positions
}

/**
 * Drops the custom order of [path], leaving the folder to be sorted as any other. Blocking, call it
 * off the main thread.
 */
fun Context.removeCustomMediaOrder(path: String) {
    val folderPath = path.lowercase(Locale.getDefault())
    config.removeCustomMediaOrderFolder(folderPath)
    try {
        mediaOrderDB.deleteFolderOrder(folderPath)
    } catch (ignored: Exception) {
    }
}
