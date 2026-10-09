package org.fossify.gallery.extensions

import android.widget.Toast
import androidx.core.util.Pair
import org.fossify.commons.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.asynctasks.CopyMoveTask
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.canManageMedia
import org.fossify.commons.extensions.deleteFromMediaStore
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getAvailableStorageB
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileUrisFromFileDirItems
import org.fossify.commons.extensions.isAccessibleWithSAFSdk30
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isPathOnSD
import org.fossify.commons.extensions.isRecycleBinPath
import org.fossify.commons.extensions.isRestrictedSAFOnlyRoot
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.getConflictResolution
import org.fossify.commons.interfaces.CopyMoveListener
import org.fossify.commons.models.FileDirItem
import java.io.File

/**
 * Commons' `copyMoveFilesTo`, ported so the toasts saying a copy or move has begun and has
 * succeeded follow [org.fossify.gallery.helpers.Config.showCopyMoveToasts]. Failures and partial
 * successes are always told. Kept step for step with the original, to diff against it.
 */
fun BaseSimpleActivity.copyMoveFiles(
    fileDirItems: ArrayList<FileDirItem>,
    source: String,
    destination: String,
    isCopyOperation: Boolean,
    copyPhotoVideoOnly: Boolean,
    copyHidden: Boolean,
    callback: (destinationPath: String) -> Unit,
) {
    if (source == destination) {
        toast(R.string.source_and_destination_same)
        return
    }

    if (!getDoesFilePathExist(destination)) {
        toast(R.string.invalid_destination)
        return
    }

    val listener = CopyMoveReport(this, callback)
    handleSAFDialog(destination) { destinationGranted ->
        if (!destinationGranted) {
            listener.copyFailed()
            return@handleSAFDialog
        }

        handleSAFDialogSdk30(destination) { sdk30Granted ->
            if (!sdk30Granted) {
                listener.copyFailed()
                return@handleSAFDialogSdk30
            }

            val start = {
                val copyMove = {
                    startCopyMove(fileDirItems, destination, isCopyOperation, copyPhotoVideoOnly, copyHidden, listener)
                }
                if (canManageMedia() && !fileDirItems.first().isRecycleBinPath(this)) {
                    updateSDK30Uris(getFileUrisFromFileDirItems(fileDirItems)) { sdk30UriSuccess ->
                        if (sdk30UriSuccess) {
                            copyMove()
                        }
                    }
                } else {
                    copyMove()
                }
            }

            if (isCopyOperation) {
                start()
            } else if (fileDirItems.first().isDirectory || !canMoveByRenaming(source, destination)) {
                handleSAFDialog(source) { safSuccess ->
                    if (safSuccess) {
                        start()
                    }
                }
            } else {
                renameIntoFolder(fileDirItems, destination, listener)
            }
        }
    }
}

private fun BaseSimpleActivity.canMoveByRenaming(source: String, destination: String) =
    listOf(source, destination).none {
        isPathOnOTG(it) || isPathOnSD(it) || isRestrictedSAFOnlyRoot(it) || isAccessibleWithSAFSdk30(it)
    }

// A move within internal storage, done by renaming rather than through CopyMoveTask
private fun BaseSimpleActivity.renameIntoFolder(
    fileDirItems: ArrayList<FileDirItem>,
    destination: String,
    listener: CopyMoveReport,
) {
    checkConflicts(fileDirItems, destination, 0, LinkedHashMap()) { resolutions ->
        listener.started(R.string.moving)
        ensureBackgroundThread {
            var fileCountToCopy = fileDirItems.size
            val updatedPaths = ArrayList<String>(fileDirItems.size)
            val destinationFolder = File(destination)
            for (oldFileDirItem in fileDirItems) {
                var newFile = File(destinationFolder, oldFileDirItem.name)
                if (newFile.exists()) {
                    when (getConflictResolution(resolutions, newFile.absolutePath)) {
                        CONFLICT_SKIP -> fileCountToCopy--
                        CONFLICT_KEEP_BOTH -> newFile = getAlternativeFile(newFile)
                        // this file is guaranteed to be on the internal storage, so just delete it this way
                        else -> newFile.delete()
                    }
                }

                if (!newFile.exists() && File(oldFileDirItem.path).renameTo(newFile)) {
                    if (!baseConfig.keepLastModified) {
                        newFile.setLastModified(System.currentTimeMillis())
                    }
                    updatedPaths.add(newFile.absolutePath)
                    deleteFromMediaStore(oldFileDirItem.path)
                }
            }

            runOnUiThread {
                listener.copySucceeded(
                    copyOnly = false,
                    copiedAll = if (updatedPaths.isEmpty()) {
                        fileCountToCopy == 0
                    } else {
                        fileCountToCopy <= updatedPaths.size
                    },
                    destinationPath = destination,
                    wasCopyingOneFileOnly = updatedPaths.size == 1
                )
            }
        }
    }
}

private fun BaseSimpleActivity.startCopyMove(
    files: ArrayList<FileDirItem>,
    destinationPath: String,
    isCopyOperation: Boolean,
    copyPhotoVideoOnly: Boolean,
    copyHidden: Boolean,
    listener: CopyMoveReport,
) {
    val availableSpace = destinationPath.getAvailableStorageB()
    val sumToCopy = files.sumOf { it.getProperSize(applicationContext, copyHidden) }
    if (availableSpace != -1L && sumToCopy >= availableSpace) {
        val text = String.format(getString(R.string.no_space), sumToCopy.formatSize(), availableSpace.formatSize())
        toast(text, Toast.LENGTH_LONG)
        return
    }

    checkConflicts(files, destinationPath, 0, LinkedHashMap()) { resolutions ->
        listener.started(if (isCopyOperation) R.string.copying else R.string.moving)
        handleNotificationPermission { granted ->
            if (granted) {
                listener.hold()
                CopyMoveTask(
                    activity = this,
                    copyOnly = isCopyOperation,
                    copyMediaOnly = copyPhotoVideoOnly,
                    conflictResolutions = resolutions,
                    listener = listener,
                    copyHidden = copyHidden
                ).execute(Pair(files, destinationPath))
            } else {
                PermissionRequiredDialog(
                    activity = this,
                    textId = R.string.allow_notifications_files,
                    positiveActionCallback = { openNotificationSettings() })
            }
        }
    }
}

private class CopyMoveReport(
    private val activity: BaseSimpleActivity,
    private val callback: (destinationPath: String) -> Unit,
) : CopyMoveListener {
    fun started(messageId: Int) {
        if (activity.config.showCopyMoveToasts) {
            activity.toast(messageId)
        }
    }

    // CopyMoveTask holds its listener weakly, so something has to hold it until the task reports
    fun hold() {
        held.add(this)
    }

    override fun copySucceeded(
        copyOnly: Boolean,
        copiedAll: Boolean,
        destinationPath: String,
        wasCopyingOneFileOnly: Boolean,
    ) {
        held.remove(this)
        val messageId = when {
            !copiedAll -> if (copyOnly) R.string.copying_success_partial else R.string.moving_success_partial
            !activity.config.showCopyMoveToasts -> null
            wasCopyingOneFileOnly -> if (copyOnly) R.string.copying_success_one else R.string.moving_success_one
            else -> if (copyOnly) R.string.copying_success else R.string.moving_success
        }
        messageId?.let { activity.toast(it) }
        callback(destinationPath)
    }

    override fun copyFailed() {
        held.remove(this)
        activity.toast(R.string.copy_move_failed)
    }

    companion object {
        private val held = HashSet<CopyMoveReport>()
    }
}
