package org.fossify.gallery.helpers

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * The blocks of metadata an image container stores alongside the picture itself, named the way the
 * file names them rather than the way a person would.
 *
 * [OTHER] is everything else a container carries that is not the picture: JPEG comments, PNG text
 * chunks, the maker-specific application segments a camera writes. They share one entry because none
 * of them is worth a line of its own in a dialog, and all of them go together when someone asks for
 * a clean file.
 */
internal enum class MetadataBlock { EXIF, XMP, IPTC, ICC, OTHER }

/**
 * Reads and rewrites the metadata blocks of an image container - JPEG, PNG and WebP - by copying the
 * file out block by block and leaving the unwanted ones behind.
 *
 * Deliberately surgery on the container rather than a re-encode: not one byte of the compressed
 * picture is touched, so a stripped file is pixel for pixel the file it came from. Each format is
 * walked in its own terms in a file of its own; anything else is refused outright, since a container
 * this does not understand is one it could corrupt.
 *
 * Every entry point blocks on file IO. Call it off the main thread.
 */
internal object ContainerMetadata {
    /** Every metadata block [file] carries, empty when the format is not one that can be rewritten. */
    @Suppress("TooGenericExceptionCaught") // a truncated or lying file throws from anywhere in the walk
    fun blocksIn(file: File): Set<MetadataBlock> = try {
        formatOf(file)?.let { it.walk(file, null, emptySet()) }.orEmpty()
    } catch (ignored: Exception) {
        emptySet()
    } catch (ignored: OutOfMemoryError) {
        // a chunk claiming a length no file could hold asks for an array to match it
        emptySet()
    }

    /**
     * Writes [source] to [destination] leaving out every block in [drop], returning whether it was
     * written. A format that cannot be walked is refused rather than copied, so a caller never ends
     * up with a "stripped" file that is only a copy of the original.
     */
    fun rewrite(source: File, destination: File, drop: Set<MetadataBlock>): Boolean {
        val format = formatOf(source) ?: return false
        BufferedOutputStream(FileOutputStream(destination)).use { format.walk(source, it, drop) }
        return destination.length() > 0
    }

    private enum class Format(val walk: (File, OutputStream?, Set<MetadataBlock>) -> Set<MetadataBlock>) {
        JPEG(::walkJpeg),
        PNG(::walkPng),
        WEBP(::walkWebp),
    }

    /** What the file actually is, read off its first bytes rather than trusted from its name. */
    private fun formatOf(file: File): Format? {
        val head = ByteArray(HEADER_LENGTH)
        FileInputStream(file).use { stream ->
            if (stream.read(head) < HEADER_LENGTH) return null
        }

        return when {
            head.startsWith(JPEG_SIGNATURE) -> Format.JPEG
            head.startsWith(PNG_SIGNATURE) -> Format.PNG
            head.startsWith(RIFF_SIGNATURE) && head.startsWith(WEBP_SIGNATURE, WEBP_TAG_OFFSET) -> Format.WEBP
            else -> null
        }
    }
}

/** Enough of the head of a file for every signature above to be recognised in it. */
private const val HEADER_LENGTH = 16
