package org.fossify.gallery.extensions

import android.content.Context
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getFileInputStreamSync
import org.fossify.commons.extensions.getFileOutputStream
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getSomeDocumentFile
import org.fossify.commons.extensions.isJpg
import org.fossify.commons.extensions.needsStupidWritePermissions
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.helpers.TransformedMedia
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Mirroring a photo in place. It is an Exif edit wherever the format can carry one, which leaves the
// pixels untouched - hence TransformedMedia, without which no cache in the app would notice.

// anything the encode or the write throws lands the user in the same place: the toast
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun BaseSimpleActivity.saveMirroredImageToFile(
    oldPath: String,
    newPath: String,
    showToasts: Boolean,
    callback: () -> Unit,
) {
    if (oldPath == newPath && oldPath.isJpg()) {
        if (tryMirrorByExif(oldPath, showToasts, callback)) {
            return
        }
    }

    val tmpPath = "$recycleBinPath/.tmp_${newPath.getFilenameFromPath()}"
    val tmpFileDirItem = FileDirItem(tmpPath, tmpPath.getFilenameFromPath())
    try {
        getFileOutputStream(tmpFileDirItem) {
            if (it == null) {
                if (showToasts) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
                return@getFileOutputStream
            }

            val oldLastModified = File(oldPath).lastModified()
            if (oldPath.isJpg()) {
                copyFile(oldPath, tmpPath)
                saveExifMirror(ExifInterface(tmpPath))
            } else {
                val inputstream = getFileInputStreamSync(oldPath)
                val bitmap = BitmapFactory.decodeStream(inputstream)
                saveFile(tmpPath, bitmap, it as FileOutputStream, flipHorizontal = true)
            }

            copyFile(tmpPath, newPath)
            TransformedMedia.onTransformed(newPath)
            // restore the last-modified date before rescanning, so MediaStore's DATE_MODIFIED
            // picks up the restored value rather than the one this write just produced
            fileTransformedSuccessfully(newPath, oldLastModified)
            rescanPaths(arrayListOf(newPath)) {
                updateDirectoryPath(newPath.getParentPath())
            }

            it.flush()
            it.close()
            callback.invoke()
        }
    } catch (e: OutOfMemoryError) {
        if (showToasts) {
            toast(org.fossify.commons.R.string.out_of_memory_error)
        }
    } catch (e: Exception) {
        if (showToasts) {
            showErrorToast(e)
        }
    } finally {
        tryDeleteFileDirItem(tmpFileDirItem, false, true)
    }
}

// same, minus the IOException the Exif write throws after having saved the file anyway
@Suppress("TooGenericExceptionCaught", "InstanceOfCheckForException")
fun BaseSimpleActivity.tryMirrorByExif(path: String, showToasts: Boolean, callback: () -> Unit): Boolean {
    return try {
        val file = File(path)
        val oldLastModified = file.lastModified()
        if (saveImageMirror(path)) {
            // record the transform before touching any cache: with "Keep last modified" enabled the
            // file comes out of this byte-identical in every respect the caches can see, so this is
            // the only thing that makes their keys move (see TransformedMedia)
            TransformedMedia.onTransformed(path)
            // restore the last-modified date before rescanning, so MediaStore's DATE_MODIFIED
            // picks up the restored value rather than the one the EXIF write just produced
            fileTransformedSuccessfully(path, oldLastModified)
            rescanPaths(arrayListOf(path)) {
                updateDirectoryPath(path.getParentPath())
            }

            callback.invoke()
            if (showToasts) {
                toast(org.fossify.commons.R.string.file_saved)
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        // lets not show IOExceptions, mirroring is saved just fine even with them
        if (showToasts && e !is IOException) {
            showErrorToast(e)
        }
        false
    }
}

fun saveExifMirror(exif: ExifInterface) {
    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.mirroredOrientation().toString())
    exif.saveAttributes()
}

fun Context.saveImageMirror(path: String): Boolean {
    return if (!needsStupidWritePermissions(path)) {
        saveExifMirror(ExifInterface(path))
        true
    } else {
        val documentFile = getSomeDocumentFile(path) ?: return false
        contentResolver.openFileDescriptor(documentFile.uri, "rw")?.use { pfd ->
            saveExifMirror(ExifInterface(pfd.fileDescriptor))
        }
        true
    }
}
