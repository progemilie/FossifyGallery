package org.fossify.gallery.extensions

import android.content.Context
import org.fossify.commons.extensions.isInDownloadDir
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.gallery.helpers.MAX_QUICK_CHOOSER_FOLDERS
import org.fossify.gallery.views.QuickFolder

// The folders the copy/move quick chooser offers when a button is held, off the main thread.
fun Context.getQuickChooserFolders(sourcePath: String, callback: (List<QuickFolder>) -> Unit) {
    getCachedDirectories { dirs ->
        val source = sourcePath.trimEnd('/').getDistinctPath()
        val reachable = dirs
            .filter { !it.isRecycleBin() && !it.areFavorites() }
            .filter { it.path.trimEnd('/').getDistinctPath() != source }
            .filter { !isRestrictedWithSAFSdk30(it.path) || isInDownloadDir(it.path) }
            .distinctBy { it.path.getDistinctPath() }

        val byPath = reachable.associateBy { it.path.trimEnd('/').getDistinctPath() }
        val recent = config.recentCopyMoveDestinations.mapNotNull { byPath[it.getDistinctPath()] }
        val rest = getSortedDirectories(ArrayList(reachable)).filterNot { it in recent }

        val folders = (recent + rest)
            // two recents spelled differently resolve to one folder, which would otherwise offer it twice
            .distinctBy { it.path.trimEnd('/').getDistinctPath() }
            .take(MAX_QUICK_CHOOSER_FOLDERS)
            .map { QuickFolder(it.path.trimEnd('/'), it.name) }
            .reversed()
        callback(folders)
    }
}
