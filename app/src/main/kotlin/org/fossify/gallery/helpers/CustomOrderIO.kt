package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.extensions.writeLn
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.createFolderGroup
import org.fossify.gallery.extensions.folderGroups
import org.fossify.gallery.extensions.mediaOrderDB
import org.fossify.gallery.extensions.saveCustomMediaOrder
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

private const val COMMENT_MARKER = "#"
private const val SECTION_OPEN = "["
private const val SECTION_CLOSE = "]"

/**
 * What marks a section as a folder group rather than a folder. A folder path can never begin with
 * it, so version 1 files - which had no groups - still read exactly as they always did.
 */
private const val GROUP_SECTION_PREFIX = "group:"

private val FILE_HEADER = listOf(
    "# Fossify Gallery custom order 2",
    "# One section per folder: the header is the folder, the lines under it are its media in",
    "# order. A [group:name] header instead names a folder group, and the lines under it are its",
    "# folders. Lines starting with # are ignored."
)

/**
 * Writes every saved media order to [out] as plain text and returns how many folders it covered.
 *
 * A bracketed line names a folder, the lines under it are its media in order:
 *
 *     [/storage/emulated/0/dcim/camera]
 *     /storage/emulated/0/DCIM/Camera/IMG_002.jpg
 *     /storage/emulated/0/DCIM/Camera/IMG_001.jpg
 *
 * Media paths are absolute, so a bracketed line can never be mistaken for one. Folders are written
 * verbatim, which carries the sentinel ones - [SHOW_ALL], favorites - with no case of their own.
 * Blocking, call it off the main thread.
 */
fun Context.exportCustomMediaOrder(out: OutputStream): Int {
    val orders = LinkedHashMap<String, List<String>>()
    // the table is the authority on which folders have an order, the prefs set only mirrors it.
    // sorted so that exporting the same arrangement twice gives the same file
    mediaOrderDB.getOrderedFolders().sorted().forEach { folder ->
        val paths = mediaOrderDB.getOrderedPaths(folder)
        if (paths.isNotEmpty()) {
            orders[folder] = paths
        }
    }

    // groups ride along in the same file - both are arrangements the user made by hand, and
    // keeping them together means one export to carry off and one file to restore from
    val groups = folderGroups().filter { it.paths.isNotEmpty() }

    if (orders.isEmpty() && groups.isEmpty()) {
        return 0
    }

    out.bufferedWriter().use { writer ->
        FILE_HEADER.forEach { writer.writeLn(it) }
        groups.forEach { group ->
            writer.writeLn("")
            writer.writeLn("$SECTION_OPEN$GROUP_SECTION_PREFIX${group.name}$SECTION_CLOSE")
            group.paths.forEach { writer.writeLn(it) }
        }

        orders.forEach { (folder, paths) ->
            writer.writeLn("")
            writer.writeLn("$SECTION_OPEN$folder$SECTION_CLOSE")
            paths.forEach { writer.writeLn(it) }
        }
    }

    return orders.size + groups.size
}

/**
 * Restores the orders and folder groups held in [input] and returns how many of both it applied.
 *
 * Folders the file names are replaced outright, folders it never mentions are left alone, so
 * importing one folder's arrangement cannot cost the user another's. Groups are added rather than
 * replacing what is there, and a folder joining an imported group leaves whatever group it was in.
 * Blocking, call it off the main thread.
 */
fun Context.importCustomMediaOrder(input: InputStream): Int {
    val parsed = parseCustomMediaOrder(input)
    parsed.orders.forEach { (folder, paths) ->
        saveCustomMediaOrder(folder, paths)
        // the rows alone are inert - a folder only comes up in its arrangement once it is on
        // custom sorting too, the same pair of steps saving an arrangement in the grid takes
        config.saveCustomSorting(folder, SORT_BY_CUSTOM)
    }

    parsed.groups.forEach { (name, paths) ->
        createFolderGroup(name, paths)
    }

    return parsed.orders.size + parsed.groups.size
}

/** What one custom order file holds: folder -> ordered media, and group name -> its folders. */
private class ParsedCustomOrder(
    val orders: Map<String, List<String>>,
    val groups: Map<String, List<String>>
)

/**
 * Reads [input] into its sections. Unreadable lines are skipped rather than failing the whole
 * file - a half restored order is worth more than none. Files written before groups existed have
 * no group headers and read exactly as they did then.
 */
private fun parseCustomMediaOrder(input: InputStream): ParsedCustomOrder {
    val orders = LinkedHashMap<String, MutableList<String>>()
    val groups = LinkedHashMap<String, MutableList<String>>()
    var current: MutableList<String>? = null

    input.bufferedReader().forEachLine { rawLine ->
        val line = rawLine.trim()
        when {
            line.isEmpty() || line.startsWith(COMMENT_MARKER) -> Unit

            line.startsWith(SECTION_OPEN) && line.endsWith(SECTION_CLOSE) -> {
                val header = line.substring(1, line.length - 1).trim()
                current = when {
                    header.isEmpty() -> null

                    header.startsWith(GROUP_SECTION_PREFIX) -> {
                        // a group name is the user's own text, so it is kept exactly as written
                        val name = header.removePrefix(GROUP_SECTION_PREFIX).trim()
                        if (name.isEmpty()) null else groups.getOrPut(name) { mutableListOf() }
                    }

                    // folders are stored lowercased, so headers differing only in case are one folder
                    else -> orders.getOrPut(header.lowercase(Locale.getDefault())) { mutableListOf() }
                }
            }

            // a path standing before any header belongs to nothing, there is no use for it
            else -> current?.add(line)
        }
    }

    return ParsedCustomOrder(
        orders = orders.filterValues { it.isNotEmpty() },
        groups = groups.filterValues { it.isNotEmpty() }
    )
}
