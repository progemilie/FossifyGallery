package org.fossify.gallery.extensions

import android.content.Context
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.R
import org.fossify.gallery.helpers.FOLDER_GROUP_PATH_PREFIX
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.SHOW_ALL
import java.io.File

/**
 * What the app opens on. Held in `Config.defaultFolder`, which upstream only ever put a folder path
 * in - a startup target may also be one of the sentinel folders, or a folder group's synthetic
 * path, and the empty string is the folder grid itself.
 *
 * The stored target is only ever a name for a screen, never a promise that the screen still exists:
 * a folder can be deleted and a group dissolved long after either was picked, so
 * [isStartupTargetGone] is checked on the way out of the grid rather than on the way in.
 */

/** The screens offered by the setting, in the order it lists them, as target to label. */
fun Context.startupTargets(): List<Pair<String, String>> {
    val fixed = buildList {
        add("" to getString(R.string.startup_folder_grid))
        add(SHOW_ALL to getString(R.string.all_folders))
        add(FAVORITES to getString(org.fossify.commons.R.string.favorites))
        if (config.useRecycleBin) {
            add(RECYCLE_BIN to getString(org.fossify.commons.R.string.recycle_bin))
        }
    }

    return fixed + folderGroups().map { it.syntheticPath() to it.name }
}

/** How [target] reads in the settings row, whatever kind of screen it names. */
fun Context.startupTargetLabel(target: String): String {
    startupTargets().firstOrNull { it.first == target }?.let { return it.second }

    // a group that has since been dissolved, or a folder: either way the path is all there is
    return if (target.startsWith(FOLDER_GROUP_PATH_PREFIX)) {
        getString(R.string.startup_folder_grid)
    } else {
        target.substringAfterLast('/').ifEmpty { target }
    }
}

/** The folder group [target] names, or 0 when it names any other kind of screen. */
fun startupGroupId(target: String) = if (target.startsWith(FOLDER_GROUP_PATH_PREFIX)) {
    target.removePrefix(FOLDER_GROUP_PATH_PREFIX).toLongOrNull() ?: 0L
} else {
    0L
}

/**
 * Whether the screen [target] names is no longer there - a deleted folder, a dissolved group - and
 * the setting should fall back to the folder grid. The sentinel folders are exempt: nothing on disk
 * answers to them.
 */
fun Context.isStartupTargetGone(target: String): Boolean = when {
    target.isEmpty() || target == SHOW_ALL || target == FAVORITES || target == RECYCLE_BIN -> false
    target.startsWith(FOLDER_GROUP_PATH_PREFIX) ->
        folderGroups().none { it.id == startupGroupId(target) }

    else -> !File(target).isDirectory
}
