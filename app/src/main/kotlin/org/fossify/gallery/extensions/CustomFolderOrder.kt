package org.fossify.gallery.extensions

import android.content.Context
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.gallery.helpers.PATH_SEPARATOR

/**
 * The folder grid's hand made order: one flat list of paths in Config, walked by
 * [getSortedDirectories] when the sorting is [SORT_BY_CUSTOM].
 *
 * Folders the grid is not showing at the time - hidden, filtered out, standing inside a group, not
 * yet scanned - are in that list too, so every edit here has to leave them where they were.
 */

// distinct because a folder holds one place and one only - two entries for it would hand the merge
// below more slots than it has folders to put in them
private fun Context.customFolderOrder() =
    config.customFoldersOrder.split(PATH_SEPARATOR).filter { it.isNotEmpty() }.distinct()

private fun Context.storeCustomFolderOrder(paths: List<String>) {
    config.customFoldersOrder = paths.joinToString(PATH_SEPARATOR)
}

/**
 * Keeps the arrangement the grid is holding and turns custom sorting on, so the grid walks it.
 *
 * [shown] fills the slots the shown folders already held rather than being written out ahead of
 * everything else: an off screen folder keeps its place among them, so coming back out of a search
 * or a group does not find every folder that was hidden pushed to the end. Folders shown for the
 * first time have no slot to fill and go last.
 */
fun Context.saveCustomFolderOrder(shown: List<String>) {
    val unplaced = ArrayDeque(shown)
    val isShown = shown.toHashSet()
    val merged = customFolderOrder().map { path ->
        if (isShown.contains(path)) unplaced.removeFirst() else path
    }

    storeCustomFolderOrder(merged + unplaced)
    config.directorySorting = SORT_BY_CUSTOM
}

/**
 * Stands [newPath] where [oldPath] stood, for a group tile taking the place of the folder it was
 * made on. Without this the tile is a path the order has never heard of, and it would go to the end
 * of the grid - as if the two folders had been dropped somewhere else entirely.
 */
fun Context.replaceInCustomFolderOrder(oldPath: String, newPath: String) {
    if (config.directorySorting and SORT_BY_CUSTOM == 0) {
        return
    }

    storeCustomFolderOrder(customFolderOrder().map { if (it == oldPath) newPath else it })
}

/** Drops [paths] from the order, for tiles that no longer stand for anything. */
fun Context.removeFromCustomFolderOrder(paths: Collection<String>) {
    val gone = paths.toHashSet()
    val order = customFolderOrder()
    val kept = order.filterNot { gone.contains(it) }
    if (kept.size != order.size) {
        storeCustomFolderOrder(kept)
    }
}
