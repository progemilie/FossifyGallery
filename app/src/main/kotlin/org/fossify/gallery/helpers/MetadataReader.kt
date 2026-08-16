package org.fossify.gallery.helpers

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Directory
import com.drew.metadata.exif.ExifDirectoryBase
import com.drew.metadata.file.FileSystemDirectory
import org.fossify.commons.extensions.isVideoFast
import org.fossify.gallery.R
import org.fossify.gallery.models.FileMetadata
import org.fossify.gallery.models.MetadataSection
import org.fossify.gallery.models.MetadataTag
import java.io.File
import java.util.Locale
import androidx.exifinterface.media.ExifInterface as AndroidExif
import com.drew.metadata.Metadata as ExtractedMetadata

/**
 * Reads everything a file says about itself, straight off the file.
 *
 * Nothing here is taken from the media database, the Room cache or the [org.fossify.gallery.models.Medium]
 * the grid was built from - those all hold what was true at the last scan, and a file edited by
 * another app since then would be described wrongly. Every call re-opens the file.
 *
 * The bulk of the work is done by metadata-extractor, which hands back one directory per group the
 * file actually stores (JPEG, Exif IFD0, Exif SubIFD, GPS, IPTC, XMP, ICC Profile, PNG-*, WebP,
 * QuickTime, MP4, ...). Those become the collapsible sections as-is, so a tag the file carries can
 * never be silently dropped for want of a hand-written mapping. On top of that:
 * - ExifInterface fills in EXIF for the formats metadata-extractor cannot parse,
 * - MediaMetadataRetriever and MediaExtractor describe video, which EXIF says nothing about.
 *
 * [MetadataSummary] turns the same material into the rows pinned open at the top of the sheet.
 *
 * Every entry point blocks on file IO. Call it off the main thread.
 */
object MetadataReader {
    /** Values longer than this are almost always embedded blobs; the row shows less than this anyway. */
    private const val MAX_VALUE_LENGTH = 4096

    private const val BITS_PER_MEGABIT = 1_000_000f

    internal const val MEDIA_KEY_DURATION = "Duration (ms)"
    internal const val MEDIA_KEY_BITRATE = "Bitrate"
    internal const val MEDIA_KEY_DATE = "Date"

    fun read(context: Context, path: String): FileMetadata {
        val file = File(path)
        val extracted = extract(file)
        val isVideo = path.isVideoFast()
        val mediaTags = if (isVideo) readMediaTags(path) else emptyMap()
        val trackTags = if (isVideo) readTrackTags(path) else emptyList()

        return FileMetadata(
            summary = MetadataSummary.build(context, file, extracted, mediaTags, trackTags),
            sections = buildSections(context, file, extracted, mediaTags, trackTags),
            removable = MetadataStripper.removableGroups(path),
        )
    }

    @Suppress("TooGenericExceptionCaught") // a corrupt file throws almost anything; none of it is fatal here
    private fun extract(file: File): ExtractedMetadata? {
        return try {
            ImageMetadataReader.readMetadata(file)
        } catch (ignored: Exception) {
            null
        } catch (ignored: OutOfMemoryError) {
            null
        } catch (ignored: NoClassDefFoundError) {
            null
        } catch (ignored: AssertionError) {
            null
        }
    }

    /**
     * One section per group the file stores, in the order metadata-extractor found them - which
     * runs container first, then EXIF, then the rest, and reads well enough to leave alone.
     */
    private fun buildSections(
        context: Context,
        file: File,
        extracted: ExtractedMetadata?,
        mediaTags: Map<String, String>,
        trackTags: List<MetadataTag>,
    ): List<MetadataSection> {
        val sections = mutableListOf<MetadataSection>()
        val directoryNameCount = mutableMapOf<String, Int>()
        var foundExif = false

        extracted?.directories?.forEach { directory ->
            // its three tags are the file name, size and modified date this class has already read
            // off the File itself, and the summary shows all three
            if (directory is FileSystemDirectory) return@forEach
            if (directory is ExifDirectoryBase && directory.tagCount > 0) foundExif = true

            val tags = directory.readTags()
            if (tags.isNotEmpty()) {
                sections.add(MetadataSection(directory.sectionName(directoryNameCount), tags))
            }
        }

        if (!foundExif) {
            exifInterfaceSection(file)?.let { sections.add(it) }
        }

        if (mediaTags.isNotEmpty()) {
            sections.add(
                MetadataSection(
                    name = context.getString(R.string.metadata_section_media),
                    tags = mediaTags.map { MetadataTag(it.key, it.value) },
                )
            )
        }

        if (trackTags.isNotEmpty()) {
            sections.add(MetadataSection(context.getString(R.string.metadata_section_tracks), trackTags))
        }

        return sections
    }

    /**
     * A name that stays unique. Two directories of the same type in one file (a second EXIF IFD, a
     * second XMP packet) would otherwise collapse into one heading and look like a single group.
     */
    private fun Directory.sectionName(nameCount: MutableMap<String, Int>): String {
        val base = parent?.let { "${it.name}/$name" } ?: name
        val seen = nameCount.getOrDefault(base, 0)
        nameCount[base] = seen + 1
        return if (seen == 0) base else "$base [${seen + 1}]"
    }

    @Suppress("TooGenericExceptionCaught") // describing a malformed tag throws from deep inside the library
    private fun Directory.readTags(): List<MetadataTag> {
        val tags = getTags().mapNotNull { tag ->
            val value = try {
                tag.description
            } catch (ignored: Exception) {
                null
            } catch (ignored: OutOfMemoryError) {
                null
            }

            if (value.isNullOrEmpty()) null else MetadataTag(tag.tagName, value.take(MAX_VALUE_LENGTH))
        }

        // a directory that failed to parse is worth saying so rather than showing up empty
        val errors = errors.mapIndexed { index, error -> MetadataTag("Error [${index + 1}]", error) }
        return tags + errors
    }

    /**
     * EXIF as ExifInterface sees it, used only when metadata-extractor could not parse the file at
     * all - it covers formats the library has no reader for, so a DNG or an HEIC still gets its
     * EXIF listed.
     */
    private fun exifInterfaceSection(file: File): MetadataSection? {
        val exif = try {
            AndroidExif(file.absolutePath)
        } catch (ignored: Exception) {
            return null
        } catch (ignored: OutOfMemoryError) {
            return null
        }

        val tags = EXIF_INTERFACE_TAGS.mapNotNull { tag ->
            exif.getAttribute(tag)?.takeIf { it.isNotEmpty() }?.let { MetadataTag(tag, it.take(MAX_VALUE_LENGTH)) }
        }

        return if (tags.isEmpty()) null else MetadataSection("Exif", tags)
    }

    /**
     * The framework's own view of a video. EXIF says nothing about video, and metadata-extractor
     * only reads the container's boxes, so this is where duration, bitrate and the tags a camera
     * app writes into MediaStore-visible fields come from.
     */
    @Suppress("TooGenericExceptionCaught") // setDataSource throws RuntimeException on anything it dislikes
    private fun readMediaTags(path: String): Map<String, String> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            MEDIA_KEYS.mapNotNull { (key, name) ->
                retriever.extractMetadata(key)?.takeIf { it.isNotEmpty() }?.let { name to it }
            }.toMap()
        } catch (ignored: Exception) {
            emptyMap()
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Codec and frame rate per track, straight off the container's track formats - the one place
     * that knows a file is HEVC rather than just "video".
     */
    @Suppress("TooGenericExceptionCaught") // same: an unreadable container throws rather than returning nothing
    private fun readTrackTags(path: String): List<MetadataTag> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            (0 until extractor.trackCount).mapNotNull { index ->
                val format = extractor.getTrackFormat(index)
                val mimeType = format.getString(MediaFormat.KEY_MIME) ?: return@mapNotNull null
                MetadataTag(trackLabel(mimeType, index), format.describeTrack(mimeType))
            }
        } catch (ignored: Exception) {
            emptyList()
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.describeTrack(mimeType: String): String {
        var value = mimeType.substringAfter('/').uppercase(Locale.US)

        if (containsKey(MediaFormat.KEY_FRAME_RATE)) {
            // the framework writes this as either an int or a float depending on the muxer
            val frameRate = try {
                getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
            } catch (ignored: ClassCastException) {
                getFloat(MediaFormat.KEY_FRAME_RATE)
            }
            value += String.format(Locale.US, "  ·  %.0f fps", frameRate)
        }

        if (containsKey(MediaFormat.KEY_BIT_RATE)) {
            value += String.format(
                Locale.US, "  ·  %.1f Mbps", getInteger(MediaFormat.KEY_BIT_RATE) / BITS_PER_MEGABIT
            )
        }

        return value
    }

    /**
     * The retriever keys worth listing, with the names they are shown under. The later additions
     * are gated by version: the constants are plain ints that inline, but asking an older retriever
     * for a key it has never heard of is not something the platform promises anything about.
     */
    private val MEDIA_KEYS = buildList {
        add(MediaMetadataRetriever.METADATA_KEY_DURATION to MEDIA_KEY_DURATION)
        add(MediaMetadataRetriever.METADATA_KEY_BITRATE to MEDIA_KEY_BITRATE)
        add(MediaMetadataRetriever.METADATA_KEY_DATE to MEDIA_KEY_DATE)
        add(MediaMetadataRetriever.METADATA_KEY_MIMETYPE to "MIME type")
        add(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH to "Video width")
        add(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT to "Video height")
        add(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION to "Video rotation")
        add(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE to "Capture frame rate")
        add(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO to "Has video")
        add(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO to "Has audio")
        add(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS to "Track count")
        add(MediaMetadataRetriever.METADATA_KEY_TITLE to "Title")
        add(MediaMetadataRetriever.METADATA_KEY_ARTIST to "Artist")
        add(MediaMetadataRetriever.METADATA_KEY_ALBUM to "Album")
        add(MediaMetadataRetriever.METADATA_KEY_AUTHOR to "Author")
        add(MediaMetadataRetriever.METADATA_KEY_COMPOSER to "Composer")
        add(MediaMetadataRetriever.METADATA_KEY_GENRE to "Genre")
        add(MediaMetadataRetriever.METADATA_KEY_WRITER to "Writer")
        add(MediaMetadataRetriever.METADATA_KEY_YEAR to "Year")
        add(MediaMetadataRetriever.METADATA_KEY_LOCATION to "Location")
        add(MediaMetadataRetriever.METADATA_KEY_COMPILATION to "Compilation")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT to "Frame count")
            add(MediaMetadataRetriever.METADATA_KEY_HAS_IMAGE to "Has image")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD to "Colour standard")
            add(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER to "Colour transfer")
            add(MediaMetadataRetriever.METADATA_KEY_COLOR_RANGE to "Colour range")
        }
    }

    /**
     * The tags ExifInterface can name. Only reached when metadata-extractor could not read the file
     * at all, which is why the list is spelled out rather than derived - ExifInterface offers no way
     * to enumerate what a file holds.
     */
    private val EXIF_INTERFACE_TAGS = listOf(
        AndroidExif.TAG_MAKE,
        AndroidExif.TAG_MODEL,
        AndroidExif.TAG_LENS_MAKE,
        AndroidExif.TAG_LENS_MODEL,
        AndroidExif.TAG_SOFTWARE,
        AndroidExif.TAG_DATETIME,
        AndroidExif.TAG_DATETIME_ORIGINAL,
        AndroidExif.TAG_DATETIME_DIGITIZED,
        AndroidExif.TAG_OFFSET_TIME,
        AndroidExif.TAG_OFFSET_TIME_ORIGINAL,
        AndroidExif.TAG_SUBSEC_TIME,
        AndroidExif.TAG_EXPOSURE_TIME,
        AndroidExif.TAG_F_NUMBER,
        AndroidExif.TAG_APERTURE_VALUE,
        AndroidExif.TAG_SHUTTER_SPEED_VALUE,
        AndroidExif.TAG_PHOTOGRAPHIC_SENSITIVITY,
        AndroidExif.TAG_FOCAL_LENGTH,
        AndroidExif.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        AndroidExif.TAG_FLASH,
        AndroidExif.TAG_WHITE_BALANCE,
        AndroidExif.TAG_EXPOSURE_MODE,
        AndroidExif.TAG_EXPOSURE_PROGRAM,
        AndroidExif.TAG_EXPOSURE_BIAS_VALUE,
        AndroidExif.TAG_METERING_MODE,
        AndroidExif.TAG_SCENE_CAPTURE_TYPE,
        AndroidExif.TAG_ORIENTATION,
        AndroidExif.TAG_IMAGE_WIDTH,
        AndroidExif.TAG_IMAGE_LENGTH,
        AndroidExif.TAG_PIXEL_X_DIMENSION,
        AndroidExif.TAG_PIXEL_Y_DIMENSION,
        AndroidExif.TAG_BITS_PER_SAMPLE,
        AndroidExif.TAG_COMPRESSION,
        AndroidExif.TAG_COLOR_SPACE,
        AndroidExif.TAG_X_RESOLUTION,
        AndroidExif.TAG_Y_RESOLUTION,
        AndroidExif.TAG_RESOLUTION_UNIT,
        AndroidExif.TAG_DIGITAL_ZOOM_RATIO,
        AndroidExif.TAG_SUBJECT_DISTANCE,
        AndroidExif.TAG_IMAGE_DESCRIPTION,
        AndroidExif.TAG_USER_COMMENT,
        AndroidExif.TAG_ARTIST,
        AndroidExif.TAG_COPYRIGHT,
        AndroidExif.TAG_GPS_LATITUDE,
        AndroidExif.TAG_GPS_LATITUDE_REF,
        AndroidExif.TAG_GPS_LONGITUDE,
        AndroidExif.TAG_GPS_LONGITUDE_REF,
        AndroidExif.TAG_GPS_ALTITUDE,
        AndroidExif.TAG_GPS_ALTITUDE_REF,
        AndroidExif.TAG_GPS_DATESTAMP,
        AndroidExif.TAG_GPS_TIMESTAMP,
        AndroidExif.TAG_GPS_PROCESSING_METHOD,
    )
}
