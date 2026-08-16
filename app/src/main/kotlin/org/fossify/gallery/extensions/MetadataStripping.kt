package org.fossify.gallery.extensions

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileOutputStreamSync
import org.fossify.commons.extensions.getFilenameExtension
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateLastModified
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.helpers.MetadataStripper
import org.fossify.gallery.helpers.TransformedMedia
import org.fossify.gallery.models.MetadataGroup
import java.io.File
import java.util.Locale

/**
 * Taking metadata off a file from the metadata sheet: the write access, the copy and the caches.
 * What actually comes out of the file is [MetadataStripper]'s business.
 *
 * Stripping in place is a real edit to the file - it comes out shorter - so unlike a rating or a
 * description it goes through the app's usual transform bookkeeping: the caches are emptied and
 * [TransformedMedia] is told, since a screen that was not on top has no other way to learn that the
 * bitmap it is holding is stale.
 */

/**
 * The formats whose metadata can be rewritten: the containers
 * [org.fossify.gallery.helpers.ContainerMetadata] walks, which are also the only ones ExifInterface
 * can save into.
 */
private val STRIPPABLE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

fun String.canBeStripped() =
    getFilenameExtension().lowercase(Locale.getDefault()) in STRIPPABLE_EXTENSIONS

/**
 * A free path next to [path] for a stripped copy to be written to - the same name with a number
 * appended, which is what the app's own "save as" offers for a second version of a file.
 */
fun BaseSimpleActivity.strippedCopyPath(path: String): String {
    val parent = path.getParentPath()
    val filename = path.getFilenameFromPath()
    val base = filename.substringBeforeLast('.', "").ifEmpty { filename }
    val suffix = path.getFilenameExtension().let { if (it.isEmpty()) "" else ".$it" }

    var index = 1
    while (true) {
        val candidate = "$parent/${base}_$index$suffix"
        if (!getDoesFilePathExist(candidate)) return candidate
        index++
    }
}

/**
 * Removes [groups] from the file at [path], writing either over it or to [copyPath], off the main
 * thread, and calls [callback] on the main thread with the path that was written - null when nothing
 * was.
 *
 * The work is done into a scratch file first and only then copied over the destination: the rewrite
 * reads the source while it writes, so it could not target it directly, and a failure part way
 * through leaves the original where it was rather than half a file.
 */
@Suppress("TooGenericExceptionCaught") // anything the write throws lands the user in the same place: the toast
fun BaseSimpleActivity.removeFileMetadata(
    path: String,
    groups: Set<MetadataGroup>,
    copyPath: String?,
    callback: (writtenPath: String?) -> Unit,
) {
    if (!path.canBeStripped()) {
        toast(R.string.metadata_remove_unsupported_format)
        callback(null)
        return
    }

    val destination = copyPath ?: path
    ensureWriteAccess(if (copyPath == null) path else copyPath.getParentPath()) {
        ensureBackgroundThread {
            var written = false
            // whatever went wrong has already been named, so the generic failure is not piled on top
            var reported = false
            try {
                written = stripInto(path, destination, groups)
            } catch (e: Exception) {
                reported = true
                runOnUiThread { showErrorToast(e) }
            } catch (ignored: OutOfMemoryError) {
                reported = true
                runOnUiThread { toast(org.fossify.commons.R.string.out_of_memory_error) }
            }

            runOnUiThread {
                when {
                    written && copyPath == null -> toast(R.string.metadata_removed)
                    written -> toast(org.fossify.commons.R.string.file_saved)
                    !reported -> toast(R.string.metadata_remove_failed)
                }

                callback(if (written) destination else null)
            }
        }
    }
}

/** Blocking. Returns whether [destination] ended up holding a stripped copy of [path]. */
private fun BaseSimpleActivity.stripInto(
    path: String,
    destination: String,
    groups: Set<MetadataGroup>,
): Boolean {
    val source = File(path)
    val lastModified = source.lastModified()
    val scratch = File(cacheDir, "stripped_${System.currentTimeMillis()}.${path.getFilenameExtension()}")

    try {
        if (!MetadataStripper.strip(source, scratch, groups)) {
            return false
        }

        getFileOutputStreamSync(destination, path.getMimeType())?.use { out ->
            scratch.inputStream().use { it.copyTo(out) }
        } ?: return false
    } finally {
        scratch.delete()
    }

    if (destination == path) {
        // the file is shorter than it was, so every cache key built from its size has moved on its
        // own - but not if "keep last modified" put the date back and the caches were keyed on that
        TransformedMedia.onTransformed(path)
        fileTransformedSuccessfully(path, lastModified)
    } else if (config.keepLastModified && lastModified != 0L) {
        File(destination).setLastModified(lastModified)
        updateLastModified(destination, lastModified)
    }

    rescanPaths(arrayListOf(destination)) { updateDirectoryPath(destination.getParentPath()) }
    return true
}
