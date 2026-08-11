package org.fossify.gallery.extensions

import android.content.Context
import org.fossify.commons.helpers.SORT_BY_COUNT
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_PATH
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.gallery.helpers.LOCATION_INTERNAL
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.FolderGroup

/**
 * Turning folder groups into what the grid draws, and back. A group tile is a [Directory] like any
 * other so that selection, sorting, pinning and the custom folder order carry it unchanged - but
 * it stands for no folder on disk, so it is built here and nowhere else, and never persisted.
 * See FolderGroups.kt for the definitions themselves.
 */

/**
 * Turns [dirs] into what the grid should show.
 *
 * At the root every grouped folder is taken out and one tile per group put in its place, sorted
 * back among the loose folders on values aggregated from its members. With [openGroupId] set only
 * that group's folders remain, in the order the group holds them.
 *
 * The tiles are built here and nowhere else - they are never written to Room, never scanned, and
 * [expandFolderGroups] takes them apart again on the way back out.
 */
fun Context.applyFolderGroups(dirs: ArrayList<Directory>, openGroupId: Long): ArrayList<Directory> {
    val groups = folderGroups()
    if (groups.isEmpty()) {
        return if (openGroupId == 0L) dirs else ArrayList()
    }

    if (openGroupId != 0L) {
        val group = groups.firstOrNull { it.id == openGroupId } ?: return ArrayList()
        val byPath = dirs.associateBy { it.path.groupKey() }
        return group.paths.mapNotNullTo(ArrayList()) { byPath[it.groupKey()] }
    }

    val groupOfPath = HashMap<String, FolderGroup>()
    groups.forEach { group -> group.paths.forEach { groupOfPath[it.groupKey()] = group } }

    val members = HashMap<Long, MutableList<Directory>>()
    val loose = ArrayList<Directory>(dirs.size)
    dirs.forEach { dir ->
        val group = groupOfPath[dir.path.groupKey()]
        if (group == null) {
            loose.add(dir)
        } else {
            members.getOrPut(group.id) { mutableListOf() }.add(dir)
        }
    }

    // a group whose folders are all missing from this scan has no tile to draw and is left out
    // rather than deleted - pruneFolderGroups() is what decides a group is really gone
    groups.forEach { group ->
        val found = members[group.id] ?: return@forEach
        loose.add(buildFolderGroupTile(group, found))
    }

    return getSortedDirectories(loose)
}

/**
 * Puts every group tile in [dirs] back into the folders it stood for. Anything that writes the
 * displayed list back to Room or re-scans it has to go through this - a tile's path names nothing
 * on disk, and storing one would put a phantom folder in the database.
 */
fun expandFolderGroups(dirs: ArrayList<Directory>): ArrayList<Directory> {
    if (dirs.none { it.isFolderGroup() }) {
        return dirs
    }

    return dirs.flatMapTo(ArrayList(dirs.size)) {
        if (it.isFolderGroup()) it.groupMembers else listOf(it)
    }
}

private fun Context.buildFolderGroupTile(
    group: FolderGroup,
    found: List<Directory>
): Directory {
    // in the group's own order, not the grid's: the first four are the collage, and the user
    // arranges those by dragging inside the group
    val ordered = found.sortedBy { dir ->
        group.paths.indexOfFirst { it.equals(dir.path, true) }.takeIf { it >= 0 } ?: Int.MAX_VALUE
    }

    val mediaCount = ordered.sumOf { it.mediaCnt }
    return Directory(
        id = null,
        path = group.syntheticPath(),
        tmb = ordered.firstOrNull()?.tmb.orEmpty(),
        name = group.name,
        mediaCnt = mediaCount,
        modified = ordered.maxOfOrNull { it.modified } ?: 0L,
        taken = ordered.maxOfOrNull { it.taken } ?: 0L,
        size = ordered.sumOf { it.size },
        location = ordered.firstOrNull()?.location ?: LOCATION_INTERNAL,
        types = ordered.fold(0) { types, dir -> types or dir.types },
        sortValue = folderGroupSortValue(group.name, ordered, mediaCount)
    ).apply {
        subfoldersCount = ordered.size
        subfoldersMediaCount = mediaCount
        groupMembers = ordered
    }
}

/**
 * What the tile sorts on. Size and count add up over the group; the date sortings take the member
 * that would have sorted first, so a group sits exactly where its leading folder would have.
 */
private fun Context.folderGroupSortValue(
    name: String,
    members: List<Directory>,
    mediaCount: Int
): String {
    val sorting = config.directorySorting
    return when {
        sorting and SORT_BY_NAME != 0 -> name
        // the tile has no real path to sort on, and its name is what the user sees in its place
        sorting and SORT_BY_PATH != 0 -> name
        sorting and SORT_BY_SIZE != 0 -> members.sumOf { it.size }.toString()
        sorting and SORT_BY_COUNT != 0 -> mediaCount.toString()
        else -> {
            val values = members.mapNotNull { it.sortValue.toLongOrNull() }
            val leading = if (sorting.isSortingAscending()) {
                values.minOrNull()
            } else {
                values.maxOrNull()
            }

            leading?.toString().orEmpty()
        }
    }
}
