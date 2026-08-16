package org.fossify.gallery.extensions

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getFilenameExtension
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getSomeDocumentFile
import org.fossify.commons.extensions.needsStupidWritePermissions
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateLastModified
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.helpers.XmpDescription
import java.io.File
import java.util.Locale

/**
 * The caption a file carries about itself, written into the file the same way a rating is - see
 * [XmpDescription] for where in the file, and [updateFileRating] for the shape of all this.
 *
 * Nothing is cached: the description is shown by the metadata sheet and the extended details, both
 * of which read the file as it is now rather than as the last scan found it.
 */

// the formats androidx's ExifInterface is able to write metadata back into. everything else can be
// read, so a description already in one is still shown - it just cannot be changed here
private val DESCRIBABLE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

fun String.canHaveDescription() =
    getFilenameExtension().lowercase(Locale.getDefault()) in DESCRIBABLE_EXTENSIONS

/**
 * The description stored in the file at [path], empty when it has none or cannot be read. Blocking,
 * call it off the main thread.
 */
fun getFileDescription(path: String): String {
    return try {
        val exif = ExifInterface(path)
        XmpDescription.read(exif.getXmpPacket()).ifEmpty {
            // a file captioned by something that only knows Exif still has something to say
            exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION).orEmpty().trim()
        }
    } catch (ignored: Exception) {
        ""
    } catch (ignored: OutOfMemoryError) {
        ""
    }
}

/**
 * Writes [description] into the metadata of [path], returning whether the file was actually touched
 * - false means it already said exactly this. Blocking, and it does not restore the last-modified
 * date; [BaseSimpleActivity.updateFileDescription] is what callers should be using.
 */
fun Context.saveFileDescription(path: String, description: String): Boolean {
    return if (!needsStupidWritePermissions(path)) {
        writeDescription(ExifInterface(path), description)
    } else {
        val documentFile = getSomeDocumentFile(path) ?: return false
        contentResolver.openFileDescriptor(documentFile.uri, "rw")?.use { pfd ->
            writeDescription(ExifInterface(pfd.fileDescriptor), description)
        } == true
    }
}

private fun writeDescription(exif: ExifInterface, description: String): Boolean {
    val currentXmp = exif.getXmpPacket()
    val updatedXmp = XmpDescription.apply(currentXmp, description)

    // an Exif caption already in the file is kept in step, never introduced: two places saying
    // different things is worse than the one place other apps may not read
    val currentExif = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)?.trim().orEmpty()
    val exifNeedsWriting = currentExif.isNotEmpty() && currentExif != description
    if (updatedXmp == currentXmp && !exifNeedsWriting) {
        return false
    }

    // null removes the tag outright, which is what clearing a description comes down to
    exif.setAttribute(ExifInterface.TAG_XMP, updatedXmp)
    if (exifNeedsWriting) {
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, description.ifEmpty { null })
    }

    exif.saveAttributes()
    return true
}

/**
 * Sets the description of [path] to [description] (an empty one clears it), off the main thread,
 * and calls [callback] on the main thread with whether it worked. A format that cannot carry one is
 * refused with an explanation, so no caller has to make that check for itself.
 *
 * As with a rating, the file's last-modified date is put back afterwards however "keep last
 * modified" is set - captioning a photo is not a change to the photo, and letting it re-date the
 * file would reshuffle every date-sorted grid in the app. No image cache is invalidated either:
 * writing XMP rewrites the container without touching a single pixel.
 */
@Suppress("TooGenericExceptionCaught") // anything the write throws lands the user in the same place: the toast
fun BaseSimpleActivity.updateFileDescription(
    path: String,
    description: String,
    callback: (success: Boolean) -> Unit,
) {
    if (!path.canHaveDescription()) {
        toast(R.string.description_unsupported_format)
        callback(false)
        return
    }

    ensureWriteAccess(path) {
        ensureBackgroundThread {
            val success = try {
                val file = File(path)
                val lastModified = file.lastModified()
                if (saveFileDescription(path, description.trim())) {
                    if (lastModified != 0L) {
                        file.setLastModified(lastModified)
                        updateLastModified(path, lastModified)
                    }

                    // after the date is back on disk, so the rescan records the restored value
                    // rather than the one the write just produced
                    rescanPaths(arrayListOf(path)) {
                        updateDirectoryPath(path.getParentPath())
                    }
                }

                true
            } catch (e: Exception) {
                runOnUiThread { showErrorToast(e) }
                false
            } catch (ignored: OutOfMemoryError) {
                runOnUiThread { toast(org.fossify.commons.R.string.out_of_memory_error) }
                false
            }

            runOnUiThread { callback(success) }
        }
    }
}
