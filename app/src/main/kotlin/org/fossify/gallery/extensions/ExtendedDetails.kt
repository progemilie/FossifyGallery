package org.fossify.gallery.extensions

import android.content.Context
import android.graphics.Point
import android.provider.MediaStore
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import androidx.exifinterface.media.ExifInterface
import com.awxkee.jxlcoder.JxlCoder
import org.fossify.commons.extensions.formatAsResolution
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getExifCameraModel
import org.fossify.commons.extensions.getExifDateTaken
import org.fossify.commons.extensions.getExifProperties
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getResolution
import org.fossify.gallery.R
import org.fossify.gallery.helpers.EXT_CAMERA_MODEL
import org.fossify.gallery.helpers.EXT_DATE_TAKEN
import org.fossify.gallery.helpers.EXT_EXIF_PROPERTIES
import org.fossify.gallery.helpers.EXT_GPS
import org.fossify.gallery.helpers.EXT_LAST_MODIFIED
import org.fossify.gallery.helpers.EXT_NAME
import org.fossify.gallery.helpers.EXT_ORIENTATION
import org.fossify.gallery.helpers.EXT_PATH
import org.fossify.gallery.helpers.EXT_RATING
import org.fossify.gallery.helpers.EXT_RESOLUTION
import org.fossify.gallery.helpers.EXT_SIZE
import org.fossify.gallery.helpers.XmpRating
import org.fossify.gallery.models.Medium
import java.io.File

/**
 * What the extended details header shows between two fields. A middle dot rather than a newline:
 * the details sit under the filename in the viewer's top bar now, where a line per field would
 * take over the screen.
 */
const val EXTENDED_DETAILS_SEPARATOR = "  ·  "

/**
 * Squeezes the fields onto as few lines as possible. Spaces inside a field are made unbreakable
 * first, so a line only ever ends at one of the dots between fields rather than halfway through a
 * date.
 */
fun List<String>.joinAsExtendedDetails() =
    joinToString(EXTENDED_DETAILS_SEPARATOR) { it.replace(' ', '\u00A0') }

/**
 * The fields the user picked under "Manage extended details", each one already formatted, in the
 * order the settings dialog lists them. Touches the file, so keep it off the main thread.
 *
 * [skipName] leaves the file name out - the viewer prints it above these as its own heading, and
 * repeating it there would only cost a line.
 */
fun Context.getMediumExtendedDetails(medium: Medium, skipName: Boolean = false): List<String> {
    val file = File(medium.path)
    if (!getDoesFilePathExist(file.absolutePath)) {
        return emptyList()
    }

    val exif = try {
        ExifInterface(medium.path)
    } catch (ignored: Exception) {
        return emptyList()
    }

    val wanted = config.extendedDetails and (if (skipName) EXT_NAME.inv() else -1)
    return extendedDetailFields(medium, file, exif)
        .filter { (flag, _) -> wanted and flag != 0 }
        .map { (_, produce) -> produce() }
        .filter { it.isNotEmpty() }
}

/**
 * Every field the settings dialog can turn on, paired with the flag that turns it on, in the order
 * that dialog lists them. Each one is only worked out if it was asked for.
 */
private fun Context.extendedDetailFields(
    medium: Medium,
    file: File,
    exif: ExifInterface,
): List<Pair<Int, () -> String>> = listOf(
    EXT_NAME to { medium.name },
    EXT_PATH to { "${file.parent.trimEnd('/')}/" },
    EXT_SIZE to { file.length().formatSize() },
    EXT_RESOLUTION to { getMediumResolution(medium, file).orEmpty() },
    EXT_LAST_MODIFIED to { getFileLastModified(file) },
    EXT_DATE_TAKEN to { exif.getExifDateTaken(this) },
    EXT_CAMERA_MODEL to { exif.getExifCameraModel() },
    EXT_EXIF_PROPERTIES to { exif.getExifProperties() },
    EXT_GPS to { getLatLonAltitude(medium.path) },
    EXT_ORIENTATION to { exif.getReadableOrientation(this) },
    EXT_RATING to {
        // straight out of the file rather than off the Medium, which may be a copy made before the
        // rating was last changed
        val rating = XmpRating.read(exif.getAttributeBytes(ExifInterface.TAG_XMP)?.toString(Charsets.UTF_8))
        "${getString(R.string.rating)}: ${getRatingLabel(rating)}"
    },
)

private fun Context.getMediumResolution(medium: Medium, file: File): String? {
    return if (medium.name.endsWith(".jxl", ignoreCase = true)) {
        val resolution = try {
            JxlCoder.getSize(file.readBytes())
        } catch (ignored: OutOfMemoryError) {
            null
        }

        resolution?.let { Point(it.width, it.height).formatAsResolution() }
    } else {
        getResolution(file.absolutePath)?.formatAsResolution()
    }
}

private fun Context.getFileLastModified(file: File): String {
    val projection = arrayOf(Images.Media.DATE_MODIFIED)
    val uri = Files.getContentUri("external")
    val selection = "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs = arrayOf(file.absolutePath)
    val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
    cursor?.use {
        return if (cursor.moveToFirst()) {
            val dateModified = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000L
            dateModified.formatDate(this)
        } else {
            file.lastModified().formatDate(this)
        }
    }

    return ""
}

private fun getLatLonAltitude(path: String): String {
    val exif = try {
        ExifInterface(path)
    } catch (ignored: Exception) {
        return ""
    }

    var result = ""
    val latLon = FloatArray(2)
    if (exif.getLatLong(latLon)) {
        result = "${latLon[0]},  ${latLon[1]}"
    }

    val altitude = exif.getAltitude(0.0)
    if (altitude != 0.0) {
        result += ",  ${altitude}m"
    }

    return result.trimStart(',').trim()
}
