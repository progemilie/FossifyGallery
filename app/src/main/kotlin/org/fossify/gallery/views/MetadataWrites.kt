package org.fossify.gallery.views

import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.activities.BaseViewerActivity
import org.fossify.gallery.dialogs.EditDescriptionDialog
import org.fossify.gallery.dialogs.RemoveMetadataDialog
import org.fossify.gallery.extensions.getFileDescription
import org.fossify.gallery.extensions.removeFileMetadata
import org.fossify.gallery.extensions.updateFileDescription
import org.fossify.gallery.models.MetadataGroup

/**
 * The two things the metadata sheet writes rather than reads: the caption a file carries about
 * itself, and taking metadata back off it.
 *
 * Kept out of [MetadataSheet], which is about how the panel behaves rather than about editing the
 * file behind it. Neither of these patches what is on screen - both call [onFileChanged], which
 * reads the file again: a write may have been refused, or have shifted more than it was asked to,
 * and what the file says now is the only thing worth showing.
 */
internal class MetadataWrites(
    private val viewer: BaseViewerActivity,
    private val onFileChanged: () -> Unit,
) {
    fun editDescription(path: String) {
        ensureBackgroundThread {
            val current = getFileDescription(path)
            viewer.runOnUiThread {
                EditDescriptionDialog(viewer, current) { description ->
                    viewer.updateFileDescription(path, description) { success ->
                        if (success) onFileChanged()
                    }
                }
            }
        }
    }

    fun removeMetadata(path: String, removable: List<MetadataGroup>) {
        if (removable.isEmpty()) return

        RemoveMetadataDialog(viewer, path, removable) { groups, copyPath ->
            viewer.removeFileMetadata(path, groups, copyPath) { writtenPath ->
                // a copy leaves the file on screen exactly as it was, so there is nothing to redraw
                if (writtenPath != null && copyPath == null) {
                    onFileChanged()
                }
            }
        }
    }
}
