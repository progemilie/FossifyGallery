package org.fossify.gallery.extensions

import android.content.Context
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.R
import org.fossify.gallery.helpers.SHOW_ALL
import java.io.File

/**
 * What the app opens on. Held in `Config.defaultFolder`, which upstream only ever put a folder path
 * in - a startup target may also be either of the two panes or the favorites sentinel, the empty
 * string being the folder grid.
 *
 * The stored target is only ever a name for a screen, never a promise that the screen still exists:
 * a folder can be deleted long after it was picked, and older installs may still hold a target the
 * setting no longer offers, so [isStartupTargetGone] is checked on the way out of the grid rather
 * than on the way in.
 */

/** The screens offered by the setting, in the order it lists them, as target to label. */
fun Context.startupTargets(): List<Pair<String, String>> = listOf(
    SHOW_ALL to getString(R.string.nav_pictures),
    "" to getString(R.string.nav_albums),
    FAVORITES to getString(org.fossify.commons.R.string.favorites)
)

/** How [target] reads in the settings row, whatever kind of screen it names. */
fun Context.startupTargetLabel(target: String): String {
    startupTargets().firstOrNull { it.first == target }?.let { return it.second }

    // a folder is named by its path; anything else is a target this setting no longer offers - a
    // folder group, the recycle bin - and reads as the grid it is about to fall back to
    return if (target.startsWith('/')) {
        target.substringAfterLast('/').ifEmpty { target }
    } else {
        getString(R.string.nav_albums)
    }
}

/**
 * Whether the screen [target] names is not there to be opened - a deleted folder, or one of the
 * targets the setting has stopped offering - and it should fall back to the folder grid. The
 * sentinels are exempt: nothing on disk answers to them.
 */
fun isStartupTargetGone(target: String): Boolean = when (target) {
    "", SHOW_ALL, FAVORITES -> false
    else -> !File(target).isDirectory
}
