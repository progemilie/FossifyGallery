package org.fossify.gallery.helpers

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * A WebP as [ContainerMetadata] walks it: a RIFF file, so a header carrying the total size and then
 * chunks of four-letter tag, size and data.
 *
 * Read through a random-access handle rather than a stream because that header has to be written
 * with the size the output ends up being, which is only known once every chunk has been measured.
 */
internal fun walkWebp(file: File, out: OutputStream?, drop: Set<MetadataBlock>): ContainerWalk {
    RandomAccessFile(file, "r").use { handle ->
        val layout = webpChunks(handle)
        val found = layout.chunks.mapNotNull { it.block }.toSet()
        if (out == null || !layout.isComplete) return ContainerWalk(found, layout.isComplete)

        val kept = layout.chunks.filter { it.block == null || it.block !in drop }
        out.write(RIFF_SIGNATURE)
        out.writeIntLittleEndian((WEBP_TAG_LENGTH + kept.sumOf { it.storedLength }).toInt())
        out.write(WEBP_SIGNATURE)

        kept.forEach { chunk ->
            out.write(chunk.tag.toByteArray(Charsets.US_ASCII))
            out.writeIntLittleEndian(chunk.length)
            handle.seek(chunk.offset)
            if (chunk.tag == WEBP_FEATURES) {
                out.write(handle.readFeatures(chunk.length, drop))
            } else if (!out.writeFrom(handle, chunk.length)) {
                return ContainerWalk(found, isComplete = false)
            }

            // chunks are padded to an even length, and the pad byte is not counted in the size
            if (chunk.length % 2 != 0) out.write(0)
        }

        return ContainerWalk(found, isComplete = true)
    }
}

/**
 * The extended-format header, with the flags for the dropped blocks turned off. A reader trusts
 * those flags over the chunks actually present, so leaving them set would advertise metadata that is
 * no longer in the file.
 */
private fun RandomAccessFile.readFeatures(length: Int, drop: Set<MetadataBlock>): ByteArray {
    val features = ByteArray(length).also { readFully(it) }
    if (features.isEmpty()) return features

    var flags = features[0].toInt()
    WEBP_FEATURE_FLAGS.forEach { (block, flag) ->
        if (block in drop) flags = flags and flag.inv()
    }

    features[0] = flags.toByte()
    return features
}

/**
 * The chunks between the header and where the header says the file ends. Complete only when they
 * account for exactly that much, all of it present: anything past the RIFF's end is not part of the
 * picture, and a chunk overrunning it is a file this does not understand.
 */
private fun webpChunks(handle: RandomAccessFile): WebpLayout {
    handle.seek(RIFF_SIGNATURE.size.toLong())
    val riffLength = handle.readIntLittleEndian()
    val end = RIFF_HEADER_LENGTH + riffLength.toLong()
    if (riffLength < 0 || end > handle.length()) return WebpLayout(emptyList(), isComplete = false)

    val chunks = mutableListOf<WebpChunk>()
    var offset = WEBP_FIRST_CHUNK_OFFSET
    while (offset + WEBP_CHUNK_HEADER_LENGTH <= end) {
        handle.seek(offset)
        val tag = ByteArray(WEBP_TAG_LENGTH).also { handle.readFully(it) }.toString(Charsets.US_ASCII)
        val length = handle.readIntLittleEndian()
        if (length < 0) break

        val chunk = WebpChunk(tag, offset + WEBP_CHUNK_HEADER_LENGTH, length, WEBP_BLOCKS[tag])
        chunks.add(chunk)
        offset += chunk.storedLength
    }

    return WebpLayout(chunks, isComplete = offset == end && chunks.isNotEmpty())
}

private class WebpLayout(val chunks: List<WebpChunk>, val isComplete: Boolean)

private class WebpChunk(val tag: String, val offset: Long, val length: Int, val block: MetadataBlock?) {
    /** What the chunk takes up in the file: its header, its data and any pad byte. */
    val storedLength get() = WEBP_CHUNK_HEADER_LENGTH + length.toLong() + length % 2
}

internal const val WEBP_TAG_LENGTH = 4
internal const val WEBP_TAG_OFFSET = 8
internal val RIFF_SIGNATURE = "RIFF".toByteArray(Charsets.US_ASCII)
internal val WEBP_SIGNATURE = "WEBP".toByteArray(Charsets.US_ASCII)

private const val WEBP_CHUNK_HEADER_LENGTH = 8
private const val RIFF_HEADER_LENGTH = 8L
private const val WEBP_FIRST_CHUNK_OFFSET = 12L
private const val WEBP_FEATURES = "VP8X"
private const val WEBP_FLAG_ICC = 0x20
private const val WEBP_FLAG_EXIF = 0x08
private const val WEBP_FLAG_XMP = 0x04

private val WEBP_BLOCKS = mapOf(
    "EXIF" to MetadataBlock.EXIF,
    "XMP " to MetadataBlock.XMP,
    "ICCP" to MetadataBlock.ICC,
)

/** The bits of the extended header's first byte that say which of those chunks the file carries. */
private val WEBP_FEATURE_FLAGS = mapOf(
    MetadataBlock.ICC to WEBP_FLAG_ICC,
    MetadataBlock.EXIF to WEBP_FLAG_EXIF,
    MetadataBlock.XMP to WEBP_FLAG_XMP,
)
