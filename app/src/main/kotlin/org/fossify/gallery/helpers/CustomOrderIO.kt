package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.extensions.writeLn
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaOrderDB
import org.fossify.gallery.extensions.saveCustomMediaOrder
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

private const val COMMENT_MARKER = "#"
private const val SECTION_OPEN = "["
private const val SECTION_CLOSE = "]"

private val FILE_HEADER = listOf(
    "# Fossify Gallery custom order 1",
    "# One section per folder: the header is the folder, the lines under it are its media in",
    "# order. Lines starting with # are ignored."
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

    if (orders.isEmpty()) {
        return 0
    }

    out.bufferedWriter().use { writer ->
        FILE_HEADER.forEach { writer.writeLn(it) }
        orders.forEach { (folder, paths) ->
            writer.writeLn("")
            writer.writeLn("$SECTION_OPEN$folder$SECTION_CLOSE")
            paths.forEach { writer.writeLn(it) }
        }
    }

    return orders.size
}

/**
 * Restores the orders held in [input] and returns how many folders it applied.
 *
 * Folders the file names are replaced outright, folders it never mentions are left alone, so
 * importing one folder's arrangement cannot cost the user another's. Blocking, call it off the
 * main thread.
 */
fun Context.importCustomMediaOrder(input: InputStream): Int {
    val orders = parseCustomMediaOrder(input)
    orders.forEach { (folder, paths) ->
        saveCustomMediaOrder(folder, paths)
        // the rows alone are inert - a folder only comes up in its arrangement once it is on
        // custom sorting too, the same pair of steps saving an arrangement in the grid takes
        config.saveCustomSorting(folder, SORT_BY_CUSTOM)
    }

    return orders.size
}

/**
 * Reads [input] into a folder -> ordered paths map. Unreadable lines are skipped rather than
 * failing the whole file - a half restored order is worth more than none.
 */
private fun parseCustomMediaOrder(input: InputStream): Map<String, List<String>> {
    val orders = LinkedHashMap<String, MutableList<String>>()
    var current: MutableList<String>? = null

    input.bufferedReader().forEachLine { rawLine ->
        val line = rawLine.trim()
        when {
            line.isEmpty() || line.startsWith(COMMENT_MARKER) -> Unit

            line.startsWith(SECTION_OPEN) && line.endsWith(SECTION_CLOSE) -> {
                val folder = line.substring(1, line.length - 1)
                    .trim()
                    .lowercase(Locale.getDefault())
                // folders are stored lowercased, so headers differing only in case are one folder
                current = if (folder.isEmpty()) null else orders.getOrPut(folder) { mutableListOf() }
            }

            // a path standing before any header belongs to no folder, there is nothing to do with it
            else -> current?.add(line)
        }
    }

    return orders.filterValues { it.isNotEmpty() }
}
