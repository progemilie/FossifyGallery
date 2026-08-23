package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.writeLn
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.folderGroups
import org.fossify.gallery.extensions.mediaOrderDB
import org.fossify.gallery.extensions.replaceFolderGroup
import org.fossify.gallery.extensions.saveCustomMediaOrder
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

private const val COMMENT_MARKER = "#"
private const val SECTION_OPEN = "["
private const val SECTION_CLOSE = "]"

/**
 * What marks a section as a folder group rather than a folder, and what stands for a group inside
 * the folder order. A folder path can never begin with it, so a file that predates groups still
 * reads exactly as it always did.
 */
private const val GROUP_PREFIX = "group:"

/** The one section holding the folder grid's own order rather than a folder's media. */
private const val FOLDER_ORDER_SECTION = "order:folders"

/** The folders that stand for a query rather than a place on disk, so nothing can stat them. */
private val SENTINEL_FOLDERS = setOf(SHOW_ALL, FAVORITES, RECYCLE_BIN)

private val FILE_HEADER = listOf(
    "# Fossify Gallery order and groups 3",
    "# Bracketed lines open a section.",
    "#   [group:name]     a folder group; the lines under it are its folders, in order.",
    "#   [order:folders]  the folder grid's own order; a group appears in it as group:name.",
    "#   [any other]      a folder; the lines under it are its media, in order.",
    "# Entries naming something that no longer exists are dropped on import, and a section left",
    "# empty by that is dropped with them. Lines starting with # are ignored."
)

/**
 * Writes every arrangement the user has made by hand to [out] as plain text, and returns how many
 * sections it wrote.
 *
 * Three kinds ride in the one file, because they are one thing to the user - how the gallery is
 * laid out - and one file is one thing to carry off and restore from:
 *
 *     [group:Trips]
 *     /storage/emulated/0/Pictures/Italy
 *
 *     [order:folders]
 *     /storage/emulated/0/DCIM/Camera
 *     group:Trips
 *
 *     [/storage/emulated/0/dcim/camera]
 *     /storage/emulated/0/DCIM/Camera/IMG_002.jpg
 *
 * Media and folder paths are absolute, so a bracketed line can never be mistaken for one. Folders
 * are written verbatim, which carries the sentinel ones with no case of their own. Groups are
 * written by name rather than by id, since an id means nothing on the install that reads this
 * back. Blocking, call it off the main thread.
 */
fun Context.exportOrderAndGroups(out: OutputStream): Int {
    val sections = LinkedHashMap<String, List<String>>()
    folderGroups().filter { it.paths.isNotEmpty() }.forEach { group ->
        sections["$GROUP_PREFIX${group.name}"] = group.paths
    }

    val folderOrder = exportableFolderOrder()
    if (folderOrder.isNotEmpty()) {
        sections[FOLDER_ORDER_SECTION] = folderOrder
    }

    // the table is the authority on which folders have a media order, the prefs set only mirrors
    // it. sorted so that exporting the same arrangement twice gives the same file
    mediaOrderDB.getOrderedFolders().sorted().forEach { folder ->
        // the all media grid can no longer be arranged, so an order left over from when it could
        // is not written out - import would only drop it again
        val paths = if (folder == SHOW_ALL) emptyList() else mediaOrderDB.getOrderedPaths(folder)
        if (paths.isNotEmpty()) {
            sections[folder] = paths
        }
    }

    if (sections.isEmpty()) {
        return 0
    }

    out.bufferedWriter().use { writer ->
        FILE_HEADER.forEach { writer.writeLn(it) }
        sections.forEach { (header, lines) ->
            writer.writeLn("")
            writer.writeLn("$SECTION_OPEN$header$SECTION_CLOSE")
            lines.forEach { writer.writeLn(it) }
        }
    }

    return sections.size
}

/**
 * The hand made folder order with its group tiles named rather than numbered. A tile stands in the
 * order under a synthetic folder_group:<id> path, and that id belongs to this install alone - so
 * it goes out as group:<name>, which the reading install can resolve to whatever id it gave the
 * group. Tiles whose group is gone are dropped; they are already dead entries here.
 */
private fun Context.exportableFolderOrder(): List<String> {
    val stored = config.customFoldersOrder.split(PATH_SEPARATOR).filter { it.isNotEmpty() }
    if (stored.isEmpty()) {
        return emptyList()
    }

    val nameOfGroupId = folderGroups().associate { it.syntheticPath() to it.name }
    return stored.mapNotNull { path ->
        if (path.startsWith(FOLDER_GROUP_PATH_PREFIX)) {
            nameOfGroupId[path]?.let { "$GROUP_PREFIX$it" }
        } else {
            path
        }
    }
}

/**
 * Restores what [input] holds and returns how many sections it applied.
 *
 * Anything naming a folder or file that is not there is dropped, and a section left empty by that
 * is dropped with it - a group whose folders have all gone is no longer a group. Groups are
 * applied first so that the folder order can resolve the names it refers to.
 *
 * Sections the file names replace what was there; anything it never mentions is left alone, so
 * importing one folder's arrangement cannot cost the user another's. Blocking, call it off the
 * main thread.
 */
fun Context.importOrderAndGroups(input: InputStream): Int {
    val parsed = parseOrderAndGroups(input)
    var applied = 0

    val groupIds = HashMap<String, Long>()
    parsed.groups.forEach { (name, paths) ->
        val existing = paths.filter { folderExists(it) }
        if (existing.isNotEmpty()) {
            groupIds[name] = replaceFolderGroup(name, existing).id
            applied++
        }
    }

    val folderOrder = parsed.folderOrder.mapNotNull { entry ->
        if (entry.startsWith(GROUP_PREFIX)) {
            val name = entry.removePrefix(GROUP_PREFIX).trim()
            // a group the file placed but never defined, or one whose folders are all gone, has
            // no tile to place - the rest of the order still stands
            groupIds[name]?.let { "$FOLDER_GROUP_PATH_PREFIX$it" }
        } else {
            entry.takeIf { folderExists(it) }
        }
    }

    if (folderOrder.isNotEmpty()) {
        config.customFoldersOrder = folderOrder.joinToString(PATH_SEPARATOR)
        // the order alone is inert - the grid only walks it once it is on custom sorting, the same
        // pair of steps that dragging folders into place takes
        config.directorySorting = SORT_BY_CUSTOM
        applied++
    }

    parsed.mediaOrders.forEach { (folder, paths) ->
        // nothing can arrange the all media grid any more, so nothing may hand it an order either
        if (folder == SHOW_ALL || !folderExists(folder)) {
            return@forEach
        }

        val existing = paths.filter { getDoesFilePathExist(it, config.OTGPath) }
        if (existing.isNotEmpty()) {
            saveCustomMediaOrder(folder, existing)
            // same two steps again, per folder this time
            config.saveCustomSorting(folder, SORT_BY_CUSTOM)
            applied++
        }
    }

    return applied
}

/** Whether [path] is somewhere the app can still show. The sentinel folders always are. */
private fun Context.folderExists(path: String) =
    SENTINEL_FOLDERS.contains(path.lowercase(Locale.getDefault())) ||
        getDoesFilePathExist(path, config.OTGPath)

/** What one file holds: groups by name, the folder order, and folder -> its media in order. */
private class ParsedOrderAndGroups(
    val groups: Map<String, List<String>>,
    val folderOrder: List<String>,
    val mediaOrders: Map<String, List<String>>
)

/**
 * Reads [input] into its sections. Unreadable lines are skipped rather than failing the whole file
 * - a half restored arrangement is worth more than none. Files written before the folder order and
 * groups joined this one carry neither section and read exactly as they did then.
 */
private fun parseOrderAndGroups(input: InputStream): ParsedOrderAndGroups {
    val groups = LinkedHashMap<String, MutableList<String>>()
    val folderOrder = mutableListOf<String>()
    val mediaOrders = LinkedHashMap<String, MutableList<String>>()
    var current: MutableList<String>? = null

    input.bufferedReader().forEachLine { rawLine ->
        val line = rawLine.trim()
        when {
            line.isEmpty() || line.startsWith(COMMENT_MARKER) -> Unit

            line.startsWith(SECTION_OPEN) && line.endsWith(SECTION_CLOSE) -> {
                current = sectionFor(
                    header = line.substring(1, line.length - 1).trim(),
                    groups = groups,
                    folderOrder = folderOrder,
                    mediaOrders = mediaOrders
                )
            }

            // a path standing before any header belongs to nothing, there is no use for it
            else -> current?.add(line)
        }
    }

    return ParsedOrderAndGroups(
        groups = groups.filterValues { it.isNotEmpty() },
        folderOrder = folderOrder,
        mediaOrders = mediaOrders.filterValues { it.isNotEmpty() }
    )
}

private fun sectionFor(
    header: String,
    groups: LinkedHashMap<String, MutableList<String>>,
    folderOrder: MutableList<String>,
    mediaOrders: LinkedHashMap<String, MutableList<String>>
): MutableList<String>? = when {
    header.isEmpty() -> null

    header.startsWith(GROUP_PREFIX) -> {
        // a group name is the user's own text, so it is kept exactly as written
        val name = header.removePrefix(GROUP_PREFIX).trim()
        if (name.isEmpty()) null else groups.getOrPut(name) { mutableListOf() }
    }

    header.equals(FOLDER_ORDER_SECTION, true) -> folderOrder

    // folders are stored lowercased, so headers differing only in case are one folder
    else -> mediaOrders.getOrPut(header.lowercase(Locale.getDefault())) { mutableListOf() }
}
