package org.fossify.gallery.extensions

import android.content.Context
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.gallery.models.FolderGroup
import java.util.Locale

/**
 * Folder groups: several folders drawn under one tile in the folder grid. The grouping is the
 * user's alone - no file or folder moves, the members keep their real paths. This file is what
 * holds and edits the definitions; FolderGroupTiles.kt is what turns them into grid tiles.
 *
 * The definitions live in Config as JSON rather than in Room because the grid reads them on the
 * main thread, where a Room query would throw. A folder belongs to at most one group.
 */

internal fun String.groupKey() = lowercase(Locale.getDefault())

/** Takes [paths] out of every group, so a folder can never end up standing under two of them. */
private fun List<FolderGroup>.detach(paths: Collection<String>) {
    val keys = paths.mapTo(HashSet()) { it.groupKey() }
    forEach { group -> group.paths.removeAll { keys.contains(it.groupKey()) } }
}

fun Context.folderGroups(): ArrayList<FolderGroup> = config.parseFolderGroups()

/** Bundles [paths] under a new group named [name] and returns it. */
fun Context.createFolderGroup(name: String, paths: List<String>): FolderGroup {
    val groups = folderGroups()
    // ids are handed out above every one in use rather than by count, so a group deleted and one
    // added right after cannot end up sharing an id with whatever still points at the old one
    val group = FolderGroup(
        id = (groups.maxOfOrNull { it.id } ?: 0L) + 1,
        name = name,
        paths = paths.toMutableList()
    )

    // a folder belongs to one group at a time, so moving it here takes it out of any other
    groups.detach(paths)
    groups.add(group)
    config.saveFolderGroups(groups.filter { it.paths.isNotEmpty() })
    return group
}

/** Appends [paths] to the group [id] holds, dropping them from whatever group had them before. */
fun Context.addToFolderGroup(id: Long, paths: List<String>) {
    val groups = folderGroups()
    groups.detach(paths)
    groups.firstOrNull { it.id == id }?.paths?.addAll(paths)
    config.saveFolderGroups(groups.filter { it.paths.isNotEmpty() })
}

/** Dissolves the groups [ids] name; their folders go back to standing on their own. */
fun Context.removeFolderGroups(ids: Collection<Long>) {
    config.saveFolderGroups(folderGroups().filterNot { ids.contains(it.id) })
}

fun Context.renameFolderGroup(id: Long, name: String) {
    val groups = folderGroups()
    groups.firstOrNull { it.id == id }?.name = name
    config.saveFolderGroups(groups)
}

/** Stores the order the user dragged the group's folders into - the collage reads the first four. */
fun Context.saveFolderGroupOrder(id: Long, paths: List<String>) {
    val groups = folderGroups()
    val group = groups.firstOrNull { it.id == id } ?: return
    // paths the caller never mentioned are kept: the grid it reordered may have been filtered by a
    // search or by hidden folders, and a member missing from the screen is still a member
    val moved = paths.mapTo(HashSet()) { it.groupKey() }
    group.paths = (paths + group.paths.filterNot { moved.contains(it.groupKey()) }).toMutableList()
    config.saveFolderGroups(groups)
}

/**
 * Drops members that are no longer on disk and deletes any group left with none. Only outright
 * absence counts - a folder merely hidden or filtered out of the current scan is still a member,
 * or turning a filter on for a minute would cost the user the group. Blocking, call it off the
 * main thread.
 */
fun Context.pruneFolderGroups() {
    val otgPath = config.OTGPath
    val groups = folderGroups()
    var changed = false

    groups.forEach { group ->
        val alive = group.paths.filter { getDoesFilePathExist(it, otgPath) }
        if (alive.size != group.paths.size) {
            group.paths = alive.toMutableList()
            changed = true
        }
    }

    val remaining = groups.filter { it.paths.isNotEmpty() }
    if (changed || remaining.size != groups.size) {
        config.saveFolderGroups(remaining)
    }
}
