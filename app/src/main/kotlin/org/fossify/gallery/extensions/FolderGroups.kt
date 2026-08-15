@file:Suppress("TooManyFunctions") // one small edit apiece over the one stored list

package org.fossify.gallery.extensions

import android.content.Context
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

/**
 * Saves [groups], dropping any left holding no folders - a group of nothing is not a group - and
 * clears out what the ones that went stood for. A tile's synthetic path is keyed into the pinned
 * folders and the folder order like any real one, and left behind there it would attach itself to
 * whatever group is handed that id next.
 */
private fun Context.storeFolderGroups(groups: List<FolderGroup>) {
    val kept = groups.filter { it.paths.isNotEmpty() }
    val keptIds = kept.mapTo(HashSet()) { it.id }
    val gone = folderGroups().filterNot { keptIds.contains(it.id) }.map { it.syntheticPath() }
    config.saveFolderGroups(kept)
    if (gone.isNotEmpty()) {
        config.removePinnedFolders(gone.toHashSet())
        removeFromCustomFolderOrder(gone)
    }
}

fun Context.folderGroups(): ArrayList<FolderGroup> = config.parseFolderGroups()

/** Bundles [paths] under a new group named [name] and returns it. */
fun Context.createFolderGroup(name: String, paths: List<String>): FolderGroup {
    val groups = folderGroups()
    // counted past the groups themselves so an id is never handed out twice, not even after the
    // group holding the highest one is dissolved. maxOf covers groups made before the counter did
    val id = maxOf(config.lastFolderGroupId, groups.maxOfOrNull { it.id } ?: 0L) + 1
    config.lastFolderGroupId = id
    val group = FolderGroup(id = id, name = name, paths = paths.toMutableList())

    // a folder belongs to one group at a time, so moving it here takes it out of any other
    groups.detach(paths)
    groups.add(group)
    storeFolderGroups(groups)
    return group
}

/**
 * Makes the group named [name] hold exactly [paths], creating it if there is none. Matching by
 * name is what lets an import run twice without leaving two tiles behind - the file has no id to
 * go on, and a name is what the user recognises the group by.
 */
fun Context.replaceFolderGroup(name: String, paths: List<String>): FolderGroup {
    val groups = folderGroups()
    val existing = groups.firstOrNull { it.name.equals(name, true) }
    if (existing == null) {
        return createFolderGroup(name, paths)
    }

    groups.detach(paths)
    existing.paths = paths.toMutableList()
    storeFolderGroups(groups)
    return existing
}

/** Appends [paths] to the group [id] holds, dropping them from whatever group had them before. */
fun Context.addToFolderGroup(id: Long, paths: List<String>) {
    val groups = folderGroups()
    groups.detach(paths)
    groups.firstOrNull { it.id == id }?.paths?.addAll(paths)
    storeFolderGroups(groups)
}

/**
 * Takes [paths] out of the group [id] holds; they go back to standing on their own in the grid.
 * A group emptied by this is dissolved with them - a group of nothing is not a group.
 */
fun Context.removeFromFolderGroup(id: Long, paths: Collection<String>) {
    val groups = folderGroups()
    val group = groups.firstOrNull { it.id == id } ?: return
    val keys = paths.mapTo(HashSet()) { it.groupKey() }
    group.paths.removeAll { keys.contains(it.groupKey()) }
    storeFolderGroups(groups)
}

/** Dissolves the groups [ids] name; their folders go back to standing on their own. */
fun Context.removeFolderGroups(ids: Collection<Long>) {
    storeFolderGroups(folderGroups().filterNot { ids.contains(it.id) })
}

fun Context.renameFolderGroup(id: Long, name: String) {
    val groups = folderGroups()
    groups.firstOrNull { it.id == id }?.name = name
    storeFolderGroups(groups)
}

/** Stores the order the user dragged the group's folders into - the collage reads the first four. */
fun Context.saveFolderGroupOrder(id: Long, paths: List<String>) {
    val groups = folderGroups()
    val group = groups.firstOrNull { it.id == id } ?: return
    // paths the caller never mentioned are kept: the grid it reordered may have been filtered by a
    // search or by hidden folders, and a member missing from the screen is still a member
    val moved = paths.mapTo(HashSet()) { it.groupKey() }
    group.paths = (paths + group.paths.filterNot { moved.contains(it.groupKey()) }).toMutableList()
    storeFolderGroups(groups)
}
