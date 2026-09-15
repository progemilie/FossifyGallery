package org.fossify.gallery.extensions

import android.content.Context
import android.provider.MediaStore
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import android.text.format.DateFormat
import androidx.exifinterface.media.ExifInterface
import com.awxkee.jxlcoder.JxlCoder
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getExifCameraModel
import org.fossify.commons.extensions.getExifProperties
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getResolution
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.humanizePath
import org.fossify.gallery.helpers.EXT_CAMERA_MODEL
import org.fossify.gallery.helpers.EXT_DATE_TAKEN
import org.fossify.gallery.helpers.EXT_DESCRIPTION
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private const val EXIF_DATE_PATTERN = "yyyy:MM:dd HH:mm:ss"
private const val PIXELS_PER_MEGAPIXEL = 1_000_000.0

// past this a tenth of a megapixel is noise, and the decimal is two characters better spent
private const val WHOLE_MEGAPIXELS_FROM = 10

/**
 * The fields the user picked under "Manage extended details", each one already formatted, in the
 * order the settings dialog lists them. Touches the file, so keep it off the main thread.
 *
 * Every field says its piece in as little room as it can: these sit over the photo, so a field
 * spelling out what it could imply costs a strip of the picture. Anything the file has nothing to
 * say about is dropped rather than printed empty, and two fields coming out word for word the same
 * - a photo last modified when it was taken, most often - are shown once.
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
        .distinct()
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
    // the storage's name in place of its mount point: "/storage/emulated/0/" is most of a line
    // spent saying "internal", while an SD card still reads as one
    EXT_PATH to { "${humanizePath(file.parent.orEmpty()).trimEnd('/')}/" },
    EXT_SIZE to { file.length().formatSize() },
    EXT_RESOLUTION to { getMediumResolution(medium, file) },
    EXT_LAST_MODIFIED to { formatDetailDate(getFileLastModified(file)) },
    EXT_DATE_TAKEN to { formatDetailDate(exif.getDateTaken()) },
    EXT_CAMERA_MODEL to { exif.getExifCameraModel() },
    EXT_EXIF_PROPERTIES to { exif.getExifProperties() },
    EXT_GPS to { getLatLonAltitude(medium.path) },
    // an unturned photo has nothing to report, so this only appears when there is a turn
    EXT_ORIENTATION to { exif.getOrientationChange(this) },
    // an empty one is dropped along with the rest of the fields the file has nothing to say about
    EXT_DESCRIPTION to { getFileDescription(medium.path) },
    EXT_RATING to {
        // straight out of the file rather than off the Medium, which may be a copy made before the
        // rating was last changed. no label: stars need none, and an unrated file says nothing
        val rating = XmpRating.read(exif.getAttributeBytes(ExifInterface.TAG_XMP)?.toString(Charsets.UTF_8))
        if (rating > 0) getRatingLabel(rating) else ""
    },
)

/**
 * A date as the header prints it: the user's own date format with the month name shortened, so
 * "15 September 2026" reads "15 Sep 2026" without reordering the parts or dropping the year.
 */
private fun Context.formatDetailDate(millis: Long): String {
    if (millis <= 0) {
        return ""
    }

    val calendar = Calendar.getInstance()
    calendar.timeInMillis = millis
    val pattern = "${config.dateFormat.replace("MMMM", "MMM")}, ${getTimeFormat()}"
    return DateFormat.format(pattern, calendar).toString()
}

/** When the photo was taken, 0 when it does not say. */
private fun ExifInterface.getDateTaken(): Long {
    val dateTime = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        ?: getAttribute(ExifInterface.TAG_DATETIME)
        ?: return 0

    return try {
        SimpleDateFormat(EXIF_DATE_PATTERN, Locale.ENGLISH).parse(dateTime.trim())?.time ?: 0
    } catch (ignored: Exception) {
        0
    }
}

private fun Context.getMediumResolution(medium: Medium, file: File): String {
    val size = if (medium.name.endsWith(".jxl", ignoreCase = true)) {
        try {
            JxlCoder.getSize(file.readBytes())?.let { it.width to it.height }
        } catch (ignored: OutOfMemoryError) {
            null
        }
    } else {
        getResolution(file.absolutePath)?.let { it.x to it.y }
    }

    return size?.let { (width, height) -> formatResolution(width, height) }.orEmpty()
}

/** "6000x4000 (24MP)" - a times sign rather than a spaced x, and no megapixel decimal to spare. */
private fun formatResolution(width: Int, height: Int): String {
    val megaPixels = width.toLong() * height / PIXELS_PER_MEGAPIXEL
    val rounded = if (megaPixels >= WHOLE_MEGAPIXELS_FROM) {
        megaPixels.roundToInt().toString()
    } else {
        "%.1f".format(megaPixels)
    }

    return "$width×$height (${rounded}MP)"
}

private fun Context.getFileLastModified(file: File): Long {
    val projection = arrayOf(Images.Media.DATE_MODIFIED)
    val uri = Files.getContentUri("external")
    val selection = "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs = arrayOf(file.absolutePath)
    val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
    cursor?.use {
        return if (cursor.moveToFirst()) {
            cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000L
        } else {
            file.lastModified()
        }
    }

    return 0
}

private fun getLatLonAltitude(path: String): String {
    val exif = try {
        ExifInterface(path)
    } catch (ignored: Exception) {
        return ""
    }

    val parts = mutableListOf<String>()
    val latLon = FloatArray(2)
    if (exif.getLatLong(latLon)) {
        // five decimals is a bit over a metre - past that a coordinate is only costing room
        parts += "%.5f".format(Locale.US, latLon[0])
        parts += "%.5f".format(Locale.US, latLon[1])
    }

    val altitude = exif.getAltitude(0.0)
    if (altitude != 0.0) {
        parts += "${altitude.roundToInt()}m"
    }

    return parts.joinToString(", ")
}
