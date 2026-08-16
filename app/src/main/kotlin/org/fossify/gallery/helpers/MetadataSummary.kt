package org.fossify.gallery.helpers

import android.content.Context
import com.drew.metadata.Directory
import com.drew.metadata.exif.ExifDirectoryBase
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.drew.metadata.file.FileTypeDirectory
import com.drew.metadata.mov.QuickTimeDirectory
import com.drew.metadata.mp4.Mp4Directory
import com.drew.metadata.xmp.XmpDirectory
import org.fossify.commons.extensions.formatAsResolution
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getResolution
import org.fossify.commons.extensions.isVideoFast
import org.fossify.gallery.R
import org.fossify.gallery.extensions.canHaveDescription
import org.fossify.gallery.extensions.getFileDescription
import org.fossify.gallery.models.MetadataTag
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.Date
import java.util.Locale
import androidx.exifinterface.media.ExifInterface as AndroidExif
import com.drew.metadata.Metadata as ExtractedMetadata

/**
 * Turns what [MetadataReader] read off a file into the handful of rows pinned open at the top of
 * the sheet.
 *
 * The fields the user asked for by name always get a row, showing a dash when the file has nothing
 * to say about them - a row that comes and goes makes the sheet unreadable at a glance. The extras
 * only appear when they apply, because an empty "Duration" on a photo is noise rather than
 * information. Everything left over is still reachable in the collapsed sections below.
 */
internal object MetadataSummary {
    private const val MAX_RATING = 5
    private const val ALTITUDE_REF_BELOW_SEA_LEVEL = 1

    private const val NO_VALUE = "—"
    private const val XMP_RATING_PROPERTY = "xmp:Rating"
    private const val JXL_EXTENSION = ".jxl"

    fun build(
        context: Context,
        file: File,
        extracted: ExtractedMetadata?,
        mediaTags: Map<String, String>,
        trackTags: List<MetadataTag>,
    ): List<MetadataTag> {
        val rows = Rows(context)
        rows.addFileFields(file, extracted, mediaTags)
        rows.addMediaFields(mediaTags, trackTags)
        rows.addTrailingFields(file, extracted)
        return rows.built
    }

    /** Collects the rows in the order they are shown, keeping the label lookup in one place. */
    private class Rows(private val context: Context) {
        val built = mutableListOf<MetadataTag>()

        fun add(
            labelRes: Int,
            value: String?,
            opensMap: Boolean = false,
            editable: Boolean = false,
        ) {
            built.add(MetadataTag(context.getString(labelRes), value.orEmpty(), opensMap, editable))
        }

        fun addIfPresent(labelRes: Int, value: String?) {
            if (!value.isNullOrEmpty()) add(labelRes, value)
        }

        fun addFileFields(file: File, extracted: ExtractedMetadata?, mediaTags: Map<String, String>) {
            val path = file.absolutePath
            add(R.string.metadata_filename, file.name.ifEmpty { path.substringAfterLast('/') })
            addDescription(path)
            add(
                labelRes = R.string.metadata_date_taken,
                value = dateTaken(extracted, mediaTags)?.formatDate(context) ?: NO_VALUE
            )
            add(
                labelRes = R.string.metadata_date_created,
                value = dateCreated(file, extracted)?.formatDate(context) ?: NO_VALUE
            )
            add(
                labelRes = R.string.metadata_date_modified,
                value = file.lastModified().takeIf { it > 0 }?.formatDate(context) ?: NO_VALUE
            )
            add(R.string.metadata_resolution, resolution(context, path) ?: NO_VALUE)
            addIfPresent(R.string.metadata_format, format(file, extracted))
            add(R.string.metadata_size, file.length().takeIf { it > 0 }?.formatSize() ?: NO_VALUE)
        }

        /**
         * The file's own caption, under its name. A file that can be given one always gets the row
         * whether or not it has anything in it yet - the row is the only way to write one, and one
         * that appeared only once there was something to show could never be the first thing typed
         * into. A file that cannot carry one only gets the row if it already says something.
         */
        fun addDescription(path: String) {
            // a video's caption, if it has one at all, is in its container rather than in the Exif
            // and XMP read here - no point opening it to be told nothing
            if (path.isVideoFast()) {
                return
            }

            val description = getFileDescription(path)
            if (path.canHaveDescription()) {
                add(R.string.description, description.ifEmpty { NO_VALUE }, editable = true)
            } else if (description.isNotEmpty()) {
                add(R.string.description, description)
            }
        }

        fun addMediaFields(mediaTags: Map<String, String>, trackTags: List<MetadataTag>) {
            if (mediaTags.isEmpty() && trackTags.isEmpty()) return

            val millis = mediaTags[MetadataReader.MEDIA_KEY_DURATION]?.toLongOrNull()
            addIfPresent(R.string.metadata_duration, millis?.let { formatDuration(it) })

            // only the picture and the sound belong up here; a container's timed-metadata or
            // subtitle tracks are listed in full further down
            built.addAll(trackTags.filter { it.name in SUMMARY_TRACK_LABELS })

            // a file the retriever could not make sense of - a truncated recording, say - answers
            // with a nonsense bitrate rather than nothing at all
            val bits = mediaTags[MetadataReader.MEDIA_KEY_BITRATE]?.toLongOrNull()?.takeIf { it > 0 }
            addIfPresent(R.string.metadata_bitrate, bits?.let { formatBitrate(it) })
        }

        fun addTrailingFields(file: File, extracted: ExtractedMetadata?) {
            val path = file.absolutePath
            add(R.string.metadata_path, file.parent?.trimEnd('/')?.plus("/") ?: path)
            add(R.string.rating, rating(extracted, path)?.let { "$it / $MAX_RATING" } ?: NO_VALUE)
            coordinates(extracted)?.let { add(R.string.metadata_coordinates, it, opensMap = true) }
        }
    }

    /**
     * When the shutter was pressed. EXIF's own original-date is the only field that means exactly
     * that, so the container's creation time is a fallback rather than a first choice - remuxing a
     * video rewrites it, while the original date survives.
     */
    private fun dateTaken(extracted: ExtractedMetadata?, mediaTags: Map<String, String>): Long? {
        val candidates = listOf<() -> Long?>(
            { extracted?.firstOf(ExifSubIFDDirectory::class.java)?.dateOriginal?.validMillis() },
            {
                extracted?.firstOf(ExifIFD0Directory::class.java)
                    ?.getDate(ExifDirectoryBase.TAG_DATETIME)?.validMillis()
            },
            {
                extracted?.firstOf(QuickTimeDirectory::class.java)
                    ?.getDate(QuickTimeDirectory.TAG_CREATION_TIME)?.validMillis()
            },
            {
                extracted?.firstOf(Mp4Directory::class.java)
                    ?.getDate(Mp4Directory.TAG_CREATION_TIME)?.validMillis()
            },
            // last, because the retriever reports whatever the container happens to hold rather
            // than distinguishing a capture time from a remux
            { mediaTags[MetadataReader.MEDIA_KEY_DATE]?.let { parseMediaDate(it) } },
        )

        return candidates.firstNotNullOfOrNull { it() }
    }

    /**
     * When the file itself came into being. EXIF's digitized date is what a camera writes when it
     * saves the file, so it beats the filesystem's own stamp, which a copy or a restore resets.
     */
    private fun dateCreated(file: File, extracted: ExtractedMetadata?): Long? {
        extracted?.firstOf(ExifSubIFDDirectory::class.java)?.dateDigitized?.validMillis()?.let { return it }
        return try {
            Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                .creationTime().toMillis().takeIf { it > 0 }
        } catch (ignored: Exception) {
            null
        }
    }

    /** Resolution as displayed, so with the EXIF rotation already applied, plus its aspect ratio. */
    private fun resolution(context: Context, path: String): String? {
        val point = if (path.endsWith(JXL_EXTENSION, ignoreCase = true)) {
            jxlResolution(path)
        } else {
            context.getResolution(path)
        } ?: return null

        val ratio = aspectRatio(point.x, point.y)
        return point.formatAsResolution() + if (ratio.isEmpty()) "" else "$SEPARATOR$ratio"
    }

    /**
     * What the file actually is, sniffed from its contents rather than trusted from its extension -
     * a .jpg holding a PNG is exactly the sort of thing worth being told about.
     */
    private fun format(file: File, extracted: ExtractedMetadata?): String? {
        val fileType = extracted?.firstOf(FileTypeDirectory::class.java)
        val name = fileType?.getString(FileTypeDirectory.TAG_DETECTED_FILE_TYPE_NAME)
        val mimeType = fileType?.getString(FileTypeDirectory.TAG_DETECTED_FILE_MIME_TYPE)
            ?: file.getMimeType().takeIf { it.isNotEmpty() }

        return when {
            name != null && mimeType != null -> "$name$SEPARATOR$mimeType"
            name != null -> name
            else -> mimeType
        }
    }

    private fun coordinates(extracted: ExtractedMetadata?): String? {
        val gps = extracted?.firstOf(GpsDirectory::class.java) ?: return null
        val location = gps.geoLocation?.takeIf { !it.isZero } ?: return null
        var result = String.format(Locale.US, "%.6f, %.6f", location.latitude, location.longitude)

        // Rational extends java.lang.Number, which Kotlin surfaces as toDouble()
        val altitude = gps.getRational(GpsDirectory.TAG_ALTITUDE)?.toDouble()
        if (altitude != null && altitude != 0.0) {
            // an altitude ref of 1 means the value is metres *below* sea level
            val belowSeaLevel = gps.getInteger(GpsDirectory.TAG_ALTITUDE_REF) == ALTITUDE_REF_BELOW_SEA_LEVEL
            result += String.format(Locale.US, "$SEPARATOR%.0f m", if (belowSeaLevel) -altitude else altitude)
        }

        return result
    }

    /**
     * The XMP rating, read out of whichever container the file keeps its XMP in. Falls back to
     * ExifInterface so that a rating this app wrote is still found in a file metadata-extractor
     * could not parse.
     */
    private fun rating(extracted: ExtractedMetadata?, path: String): Int? {
        xmpRating(extracted)?.let { return it }

        return try {
            XmpRating.read(AndroidExif(path).getAttributeBytes(AndroidExif.TAG_XMP)?.toString(Charsets.UTF_8))
                .takeIf { it > 0 }
        } catch (ignored: Exception) {
            null
        } catch (ignored: OutOfMemoryError) {
            null
        }
    }

    private fun xmpRating(extracted: ExtractedMetadata?): Int? {
        val directories = extracted?.getDirectoriesOfType(XmpDirectory::class.java) ?: return null
        return directories.firstNotNullOfOrNull { dir ->
            val properties = try {
                dir.xmpProperties
            } catch (ignored: Exception) {
                null
            }

            properties?.entries
                ?.firstOrNull { it.key.equals(XMP_RATING_PROPERTY, ignoreCase = true) }
                ?.value?.trim()?.toFloatOrNull()
                ?.toInt()?.coerceIn(0, MAX_RATING)
        }
    }

    /** metadata-extractor hands back epoch 0 for a date field that was present but empty. */
    private fun Date.validMillis(): Long? = time.takeIf { it > 0 }

    private fun <T : Directory> ExtractedMetadata.firstOf(type: Class<T>): T? = getFirstDirectoryOfType(type)
}
