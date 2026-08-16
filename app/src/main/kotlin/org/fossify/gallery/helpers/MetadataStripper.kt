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
 * the file out without it. [MetadataGroup.LOCATION] is the exception - it is a handful of fields
 * inside the Exif block, so it is cleared afterwards with ExifInterface, which rewrites the Exif in
 * place rather than dropping it. The copy in the XMP packet goes with it, or a file would still say
 * where it was taken after being told not to.
 *
 * Nothing here touches the file it is given: everything is written to a destination the caller
 * names, and it is the caller that decides whether that is a new file or the original one - see
 * [org.fossify.gallery.extensions.removeFileMetadata].
 *
 * Every entry point blocks on file IO. Call it off the main thread.
 */
object MetadataStripper {
    /** Which container block each group is. The one that is left out lives inside another's. */
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
        val groups = MetadataGroup.entries.filter { group ->
            val block = GROUP_BLOCKS[group]
            block != null && block in blocks
        }

        return if (hasLocation(path)) listOf(MetadataGroup.LOCATION) + groups else groups
    }

    /**
     * Writes [source] to [destination] without [groups], returning whether it worked. [destination]
     * must not be [source]: the file is read while it is written.
     */
    fun strip(source: File, destination: File, groups: Set<MetadataGroup>): Boolean {
        val drop = groups.mapNotNull { GROUP_BLOCKS[it] }.toSet()
        if (!ContainerMetadata.rewrite(source, destination, drop)) {
            return false
        }

        // pointless when the whole Exif block has just been dropped, and the packet holding the
        // other copy may have gone with the XMP block
        if (MetadataGroup.LOCATION in groups && MetadataGroup.EXIF !in groups) {
            removeLocation(destination)
        }

        return true
    }

    @Suppress("TooGenericExceptionCaught") // an unparseable file is one with no location to find
    private fun hasLocation(path: String): Boolean = try {
        val exif = ExifInterface(path)
        GPS_TAGS.any { exif.hasAttribute(it) } || XmpLocation.isPresent(exif.getXmpPacket())
    } catch (ignored: Exception) {
        false
    } catch (ignored: OutOfMemoryError) {
        false
    }

    /**
     * Clears the location out of a file that keeps the rest of its Exif. ExifInterface rewrites the
     * whole block from what it parsed, so anything in there it does not understand - a maker note,
     * above all - does not survive this either. That is a fair trade for a file someone has asked to
     * have the location taken out of, and the alternative is leaving the coordinates in.
     */
    private fun removeLocation(file: File) {
        val exif = ExifInterface(file.absolutePath)
        var touched = GPS_TAGS.any { exif.hasAttribute(it) }
        GPS_TAGS.forEach { exif.setAttribute(it, null) }

        val xmp = exif.getXmpPacket()
        val cleared = XmpLocation.remove(xmp)
        if (cleared != xmp) {
            // null removes the packet outright, which is what a packet holding nothing else leaves
            exif.setAttribute(ExifInterface.TAG_XMP, cleared)
            touched = true
        }

        if (touched) {
            exif.saveAttributes()
        }
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
