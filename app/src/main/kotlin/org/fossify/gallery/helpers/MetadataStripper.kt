package org.fossify.gallery.helpers

import androidx.exifinterface.media.ExifInterface
import org.fossify.gallery.extensions.canBeStripped
import org.fossify.gallery.extensions.getXmpPacket
import org.fossify.gallery.models.MetadataGroup
import java.io.File

/**
 * Takes metadata back off a file: which of the [MetadataGroup]s a file actually carries, and writing
 * out a copy of it without the ones that are not wanted.
 *
 * Most of a group is a whole block of the container, so [ContainerMetadata] does the work by copying
 * the file out without it. [MetadataGroup.LOCATION] and [MetadataGroup.ORIENTATION] are the
 * exceptions - they are fields inside the Exif block, so they are settled afterwards with
 * ExifInterface, which rewrites the Exif rather than dropping it. Each has a copy in the XMP packet
 * that goes the same way, or a file would still say what it was told not to.
 *
 * The two differ in what dropping the whole Exif means for them. The location goes with it and could
 * not be put back if anyone wanted it. The orientation is put back: a photo whose metadata was
 * stripped should not come out sideways, so unless it was asked for by name it is read off the
 * source and written into the result.
 *
 * Nothing here touches the file it is given: everything is written to a destination the caller
 * names, and it is the caller that decides whether that is a new file or the original one - see
 * [org.fossify.gallery.extensions.removeFileMetadata].
 *
 * Every entry point blocks on file IO. Call it off the main thread.
 */
object MetadataStripper {
    /** Which container block each group is. The ones left out live inside another's. */
    private val GROUP_BLOCKS = mapOf(
        MetadataGroup.EXIF to MetadataBlock.EXIF,
        MetadataGroup.XMP to MetadataBlock.XMP,
        MetadataGroup.IPTC to MetadataBlock.IPTC,
        MetadataGroup.ICC to MetadataBlock.ICC,
        MetadataGroup.OTHER to MetadataBlock.OTHER,
    )

    /**
     * The groups [path] actually carries, in the order they are listed in, or empty when there is
     * nothing removable in it at all - which is what hides the action rather than offering one that
     * would do nothing.
     */
    fun removableGroups(path: String): List<MetadataGroup> {
        if (!path.canBeStripped()) return emptyList()

        val blocks = ContainerMetadata.blocksIn(File(path))
        val fields = exifFields(path)
        return MetadataGroup.entries.filter { group ->
            GROUP_BLOCKS[group]?.let { it in blocks } ?: (group in fields)
        }
    }

    /**
     * Writes [source] to [destination] without [groups], returning whether it worked. [destination]
     * must not be [source]: the file is read while it is written.
     */
    fun strip(source: File, destination: File, groups: Set<MetadataGroup>): Boolean {
        // read before the rewrite, since the block it lives in may be one of the ones going
        val orientation = source.orientation().takeIf { MetadataGroup.ORIENTATION !in groups }

        val drop = groups.mapNotNull { GROUP_BLOCKS[it] }.toSet()
        if (!ContainerMetadata.rewrite(source, destination, drop)) {
            return false
        }

        settleExifFields(destination, groups, orientation)
        return true
    }

    /** The groups that are fields inside the Exif rather than blocks of their own, as [path] holds them. */
    @Suppress("TooGenericExceptionCaught") // a file that cannot be parsed is one with no fields to find
    private fun exifFields(path: String): Set<MetadataGroup> = try {
        val exif = ExifInterface(path)
        val xmp = exif.getXmpPacket()
        buildSet {
            if (GPS_TAGS.any { exif.hasAttribute(it) } || XmpLocation.isPresent(xmp)) {
                add(MetadataGroup.LOCATION)
            }

            if (exif.hasAttribute(ExifInterface.TAG_ORIENTATION) || XmpOrientation.isPresent(xmp)) {
                add(MetadataGroup.ORIENTATION)
            }
        }
    } catch (ignored: Exception) {
        emptySet()
    } catch (ignored: OutOfMemoryError) {
        emptySet()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun File.orientation(): String? = try {
        ExifInterface(absolutePath).getAttribute(ExifInterface.TAG_ORIENTATION)
    } catch (ignored: Exception) {
        null
    } catch (ignored: OutOfMemoryError) {
        null
    }

    /**
     * Puts the two Exif fields right in the file just written: clears whichever of them was asked
     * for and the whole Exif did not already take, and writes [orientation] back if the block it was
     * in has gone.
     *
     * ExifInterface rewrites the whole block from what it parsed, so anything in there it does not
     * understand - a maker note, above all - does not survive this either. That is a fair trade for
     * a file someone has asked to have fields taken out of, and the alternative is leaving them in.
     */
    private fun settleExifFields(file: File, groups: Set<MetadataGroup>, orientation: String?) {
        val exifDropped = MetadataGroup.EXIF in groups
        val clearLocation = MetadataGroup.LOCATION in groups && !exifDropped
        val clearOrientation = MetadataGroup.ORIENTATION in groups && !exifDropped
        val restoreOrientation = exifDropped && orientation != null
        if (!clearLocation && !clearOrientation && !restoreOrientation) {
            return
        }

        val exif = ExifInterface(file.absolutePath)
        if (clearLocation) GPS_TAGS.forEach { exif.setAttribute(it, null) }
        if (clearOrientation) exif.setAttribute(ExifInterface.TAG_ORIENTATION, null)
        if (restoreOrientation) exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation)

        val xmp = exif.getXmpPacket()
        var cleared = if (clearLocation) XmpLocation.remove(xmp) else xmp
        if (clearOrientation) cleared = XmpOrientation.remove(cleared)
        if (cleared != xmp) {
            // null removes the packet outright, which is what a packet holding nothing else leaves
            exif.setAttribute(ExifInterface.TAG_XMP, cleared)
        }

        exif.saveAttributes()
    }
}

/** Every Exif tag that says something about where, when or how fast the file was made. */
private val GPS_TAGS = listOf(
    ExifInterface.TAG_GPS_VERSION_ID,
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_SATELLITES,
    ExifInterface.TAG_GPS_STATUS,
    ExifInterface.TAG_GPS_MEASURE_MODE,
    ExifInterface.TAG_GPS_DOP,
    ExifInterface.TAG_GPS_SPEED,
    ExifInterface.TAG_GPS_SPEED_REF,
    ExifInterface.TAG_GPS_TRACK,
    ExifInterface.TAG_GPS_TRACK_REF,
    ExifInterface.TAG_GPS_IMG_DIRECTION,
    ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
    ExifInterface.TAG_GPS_MAP_DATUM,
    ExifInterface.TAG_GPS_DEST_LATITUDE,
    ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
    ExifInterface.TAG_GPS_DEST_LONGITUDE,
    ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
    ExifInterface.TAG_GPS_DEST_BEARING,
    ExifInterface.TAG_GPS_DEST_BEARING_REF,
    ExifInterface.TAG_GPS_DEST_DISTANCE,
    ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
    ExifInterface.TAG_GPS_PROCESSING_METHOD,
    ExifInterface.TAG_GPS_AREA_INFORMATION,
    ExifInterface.TAG_GPS_DIFFERENTIAL,
    ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
)
