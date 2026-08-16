package org.fossify.gallery.helpers

import androidx.exifinterface.media.ExifInterface
import org.fossify.gallery.extensions.AllNonDimensionExifAttributes
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
 * that goes the same way.
 *
 * Nothing here touches the file it is given: everything is written to a destination the caller
 * names - see [org.fossify.gallery.extensions.removeFileMetadata].
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
    private fun exifFields(path: String): Set<MetadataGroup> {
        val exif = exifOf(path) ?: return emptySet()
        val xmp = exif.getXmpPacket()
        return buildSet {
            if (GPS_TAGS.any { exif.hasAttribute(it) } || XmpLocation.isPresent(xmp)) {
                add(MetadataGroup.LOCATION)
            }

            if (exif.hasAttribute(ExifInterface.TAG_ORIENTATION) || XmpOrientation.isPresent(xmp)) {
                add(MetadataGroup.ORIENTATION)
            }
        }
    }

    private fun File.orientation(): String? =
        exifOf(absolutePath)?.getAttribute(ExifInterface.TAG_ORIENTATION)

    /** Null for a file that cannot be parsed, which is one with no fields to find in it either. */
    @Suppress("TooGenericExceptionCaught")
    private fun exifOf(path: String): ExifInterface? = try {
        ExifInterface(path)
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

/**
 * Every GPS tag ExifInterface names, picked out of the list the app already keeps of them rather
 * than written out a second time. Their constants are the tag names, which all begin "GPS".
 */
private val GPS_TAGS = AllNonDimensionExifAttributes.filter { it.startsWith("GPS") }
