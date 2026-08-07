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
import org.fossify.gallery.helpers.XmpRating
import java.io.File
import java.util.Locale

// the formats androidx's ExifInterface is able to write metadata back into. everything else can be
// read, but a rating could never be saved to it, so the action is not offered for those
private val RATEABLE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

fun String.canBeRated() = getFilenameExtension().lowercase(Locale.getDefault()) in RATEABLE_EXTENSIONS

/**
 * The rating stored in the file at [path], 0 when it has none or cannot be read. Blocking, call it
 * off the main thread.
 */
fun getFileRating(path: String): Int {
    return try {
        XmpRating.read(ExifInterface(path).getXmpPacket())
    } catch (ignored: Exception) {
        0
    } catch (ignored: OutOfMemoryError) {
        0
    }
}

/**
 * Writes [rating] into the metadata of [path], returning whether the file was actually touched -
 * false means it already said exactly this, and rewriting it would have been a pointless edit.
 *
 * Blocking, and it does not restore the last-modified date; [BaseSimpleActivity.updateFileRating]
 * is what callers should be using.
 */
fun Context.saveFileRating(path: String, rating: Int): Boolean {
    return if (!needsStupidWritePermissions(path)) {
        writeRating(ExifInterface(path), rating)
    } else {
        val documentFile = getSomeDocumentFile(path) ?: return false
        contentResolver.openFileDescriptor(documentFile.uri, "rw")?.use { pfd ->
            writeRating(ExifInterface(pfd.fileDescriptor), rating)
        } == true
    }
}

private fun writeRating(exif: ExifInterface, rating: Int): Boolean {
    val current = exif.getXmpPacket()
    val updated = XmpRating.apply(current, rating)
    if (updated == current) {
        return false
    }

    // null removes the packet outright, which is what clearing the last rating out of one we
    // created ourselves comes down to
    exif.setAttribute(ExifInterface.TAG_XMP, updated)
    exif.saveAttributes()
    return true
}

// the packet is UTF-8 by definition, while getAttribute() decodes bytes as ASCII - going through
// the raw bytes is what keeps an accented title in someone else's XMP intact
private fun ExifInterface.getXmpPacket() = getAttributeBytes(ExifInterface.TAG_XMP)?.toString(Charsets.UTF_8)

/**
 * Sets the rating of [path] to [rating] (0 clears it), off the main thread, and calls [callback] on
 * the main thread with whether it worked.
 *
 * The file's last-modified date is put back afterwards no matter how "keep last modified" is set:
 * rating a photo is not a change to the photo, and letting it re-date the file would reshuffle
 * every date-sorted grid in the app. Note that no image cache is invalidated here on purpose -
 * writing XMP rewrites the container but not a single pixel, so every decoded bitmap still stands
 * (unlike an in-place mirror, which is what helpers/TransformedMedia.kt exists for).
 */
@Suppress("TooGenericExceptionCaught") // anything the write throws lands the user in the same place: the toast
fun BaseSimpleActivity.updateFileRating(path: String, rating: Int, callback: (success: Boolean) -> Unit) {
    ensureWriteAccess(path) {
        ensureBackgroundThread {
            val success = try {
                val file = File(path)
                val lastModified = file.lastModified()
                if (saveFileRating(path, rating)) {
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

                storeRating(path, rating)
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

/**
 * Refuses the rating with an explanation when the file's format cannot carry one, so the caller
 * does not have to repeat the check everywhere the action is reachable from.
 */
fun BaseSimpleActivity.updateFileRatingIfSupported(path: String, rating: Int, callback: (success: Boolean) -> Unit) {
    if (!path.canBeRated()) {
        toast(R.string.rating_unsupported_format)
        callback(false)
        return
    }

    updateFileRating(path, rating, callback)
}
