package org.fossify.gallery.helpers

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * A JPEG as [ContainerMetadata] walks it: a chain of segments, each one a 0xFF marker byte, most of
 * them followed by a two-byte length and a payload. The compressed picture starts at the scan marker
 * and runs to the end of the file, so everything from there on is copied over in one piece.
 */
internal fun walkJpeg(file: File, out: OutputStream?, drop: Set<MetadataBlock>): Set<MetadataBlock> {
    val found = mutableSetOf<MetadataBlock>()
    DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
        input.skipFully(JPEG_SIGNATURE.size)
        out?.write(JPEG_SIGNATURE)

        var marker = input.nextMarker()
        while (marker != null && marker != MARKER_SOS) {
            marker = if (marker in STANDALONE_MARKERS) {
                out?.writeMarker(marker)
                input.nextMarker()
            } else if (input.copySegment(marker, out, drop, found)) {
                input.nextMarker()
            } else {
                null
            }
        }

        if (marker == MARKER_SOS && input.copySegment(marker, out, drop, found)) {
            // the picture itself, plus whatever a phone has appended past the end marker
            out?.let { input.copyTo(it) }
        }
    }

    return found
}

/** The next marker byte, or null at the end of the file. Fill bytes may pad the gap before one. */
private fun DataInputStream.nextMarker(): Int? {
    var marker = read()
    while (marker == MARKER_PREFIX) marker = read()
    return marker.takeIf { it != -1 }
}

/**
 * Reads the segment [marker] introduces, notes in [found] what it holds and writes it to [out]
 * unless it is one of [drop]. False when the segment claims a nonsense length, which is as far as
 * the file can be walked.
 */
private fun DataInputStream.copySegment(
    marker: Int,
    out: OutputStream?,
    drop: Set<MetadataBlock>,
    found: MutableSet<MetadataBlock>,
): Boolean {
    val length = readUnsignedShort()
    if (length < SEGMENT_LENGTH_BYTES) return false

    val payload = ByteArray(length - SEGMENT_LENGTH_BYTES).also { readFully(it) }
    val block = classifyJpeg(marker, payload)
    block?.let { found.add(it) }
    if (block == null || block !in drop) {
        out?.writeMarker(marker)
        out?.writeUnsignedShort(length)
        out?.write(payload)
    }

    return true
}

/**
 * What a segment holds, or null for one that is part of the picture rather than about it. The two
 * application segments left in place carry decoding instructions - the JFIF density and Adobe's
 * colour transform - so dropping them as "other markers" could change how the file reads back.
 */
private fun classifyJpeg(marker: Int, payload: ByteArray): MetadataBlock? = when {
    marker == MARKER_APP0 || marker == MARKER_APP14 -> null
    marker == MARKER_COMMENT -> MetadataBlock.OTHER
    marker == MARKER_APP1 && payload.startsWith(EXIF_PREFIX) -> MetadataBlock.EXIF
    marker == MARKER_APP1 && XMP_PREFIXES.any { payload.startsWith(it) } -> MetadataBlock.XMP
    marker == MARKER_APP2 && payload.startsWith(ICC_PREFIX) -> MetadataBlock.ICC
    marker == MARKER_APP13 && payload.startsWith(PHOTOSHOP_PREFIX) -> MetadataBlock.IPTC
    marker in MARKER_APP0..MARKER_APP15 -> MetadataBlock.OTHER
    else -> null
}

private fun OutputStream.writeMarker(marker: Int) {
    write(MARKER_PREFIX)
    write(marker)
}

private const val MARKER_PREFIX = 0xFF
private const val SEGMENT_LENGTH_BYTES = 2
private const val MARKER_START_OF_IMAGE = 0xD8
private const val MARKER_SOS = 0xDA
private const val MARKER_COMMENT = 0xFE
private const val MARKER_APP0 = 0xE0
private const val MARKER_APP1 = 0xE1
private const val MARKER_APP2 = 0xE2
private const val MARKER_APP13 = 0xED
private const val MARKER_APP14 = 0xEE
private const val MARKER_APP15 = 0xEF
private const val MARKER_RESTART_FIRST = 0xD0
private const val MARKER_END_OF_IMAGE = 0xD9
private const val MARKER_TEMPORARY = 0x01

internal val JPEG_SIGNATURE = byteArrayOf(MARKER_PREFIX.toByte(), MARKER_START_OF_IMAGE.toByte())

/** Markers that are the whole segment: start of image, the restart markers, end of image and TEM. */
private val STANDALONE_MARKERS = (MARKER_RESTART_FIRST..MARKER_END_OF_IMAGE).toSet() + MARKER_TEMPORARY

// an application segment says what it holds in a null-terminated name at the head of its payload
private val EXIF_PREFIX = segmentName("Exif", nulls = 2)
private val ICC_PREFIX = segmentName("ICC_PROFILE")
private val PHOTOSHOP_PREFIX = segmentName("Photoshop 3.0")
private val XMP_PREFIXES = listOf(
    segmentName("http://ns.adobe.com/xap/1.0/"),
    segmentName("http://ns.adobe.com/xmp/extension/"),
)

private fun segmentName(name: String, nulls: Int = 1) =
    name.toByteArray(Charsets.US_ASCII) + ByteArray(nulls)
