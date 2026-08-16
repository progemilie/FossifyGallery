package org.fossify.gallery.helpers

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * A PNG as [ContainerMetadata] walks it: a signature followed by chunks of length, four-letter type,
 * data and a checksum.
 *
 * Only the handful of types that can hold metadata are read into memory; everything else - the image
 * data above all - is passed straight through, so the size of the picture never becomes the size of
 * the heap this needs.
 */
internal fun walkPng(file: File, out: OutputStream?, drop: Set<MetadataBlock>): Set<MetadataBlock> {
    val found = mutableSetOf<MetadataBlock>()
    DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
        input.skipFully(PNG_SIGNATURE.size)
        out?.write(PNG_SIGNATURE)

        var more = true
        while (more) {
            more = input.copyChunk(out, drop, found)
        }
    }

    return found
}

/**
 * Reads one chunk, noting in [found] what it holds and writing it to [out] unless it is one of
 * [drop]. False once the end chunk is past, or once the file stops making sense.
 */
private fun DataInputStream.copyChunk(
    out: OutputStream?,
    drop: Set<MetadataBlock>,
    found: MutableSet<MetadataBlock>,
): Boolean {
    val length = try {
        readInt()
    } catch (ignored: EOFException) {
        return false
    }

    if (length < 0) return false
    val type = ByteArray(PNG_TYPE_LENGTH).also { readFully(it) }
    val name = type.toString(Charsets.US_ASCII)

    if (name !in PNG_METADATA_CHUNKS) {
        out?.writeInt(length)
        out?.write(type)
        passThrough(out, length + PNG_CRC_LENGTH)
        return name != PNG_END
    }

    val data = ByteArray(length).also { readFully(it) }
    val crc = ByteArray(PNG_CRC_LENGTH).also { readFully(it) }
    val block = classifyPng(name, data)
    found.add(block)
    if (block !in drop) {
        out?.writeInt(length)
        out?.write(type)
        out?.write(data)
        out?.write(crc)
    }

    return true
}

/** An international text chunk is where a PNG keeps its XMP; the rest are plain text or a date. */
private fun classifyPng(type: String, data: ByteArray): MetadataBlock = when {
    type == PNG_EXIF -> MetadataBlock.EXIF
    type == PNG_ICC -> MetadataBlock.ICC
    type == PNG_INTERNATIONAL_TEXT && data.startsWith(PNG_XMP_KEYWORD) -> MetadataBlock.XMP
    else -> MetadataBlock.OTHER
}

private const val PNG_TYPE_LENGTH = 4
private const val PNG_CRC_LENGTH = 4
private const val PNG_EXIF = "eXIf"
private const val PNG_ICC = "iCCP"
private const val PNG_INTERNATIONAL_TEXT = "iTXt"
private const val PNG_END = "IEND"
private val PNG_METADATA_CHUNKS = setOf(PNG_EXIF, PNG_ICC, PNG_INTERNATIONAL_TEXT, "tEXt", "zTXt", "tIME")

/** A text chunk opens with a null-terminated keyword saying what the text is. */
private val PNG_XMP_KEYWORD = "XML:com.adobe.xmp".toByteArray(Charsets.US_ASCII) + ByteArray(1)

/** The eight bytes every PNG opens with. Literally magic numbers - that is what a signature is. */
@Suppress("MagicNumber")
internal val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
