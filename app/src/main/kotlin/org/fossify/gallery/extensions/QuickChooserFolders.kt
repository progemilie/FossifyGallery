package org.fossify.gallery.extensions

import android.content.Context
import org.fossify.commons.extensions.isInDownloadDir
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.gallery.helpers.MAX_QUICK_CHOOSER_FOLDERS
import org.fossify.gallery.views.QuickFolder

// The folders the copy/move quick chooser offers when a button is held, off the main thread.
fun Context.getQuickChooserFolders(sourcePath: String, callback: (List<QuickFolder>) -> Unit) {
    getCachedDirectories { dirs ->
        // resolving a path is a filesystem call, and every folder below is resolved several times
        // over - once each is enough
        val resolved = HashMap<String, String>()
        fun distinctPathOf(path: String) = resolved.getOrPut(path) { path.getDistinctPath() }

        val source = distinctPathOf(sourcePath.trimEnd('/'))
        val reachable = dirs
            .filter { !it.isRecycleBin() && !it.areFavorites() }
            .filter { distinctPathOf(it.path.trimEnd('/')) != source }
            .filter { !isRestrictedWithSAFSdk30(it.path) || isInDownloadDir(it.path) }
            .distinctBy { distinctPathOf(it.path) }

        val byPath = reachable.associateBy { distinctPathOf(it.path.trimEnd('/')) }
        val recent = config.recentCopyMoveDestinations.mapNotNull { byPath[distinctPathOf(it)] }
        val rest = getSortedDirectories(ArrayList(reachable)).filterNot { it in recent }

        val folders = (recent + rest)
            // two recents spelled differently resolve to one folder, which would otherwise offer it twice
            .distinctBy { distinctPathOf(it.path.trimEnd('/')) }
            .take(MAX_QUICK_CHOOSER_FOLDERS)
            .map { QuickFolder(it.path.trimEnd('/'), it.name) }
            .reversed()
        callback(folders)
    }
}
