package org.fossify.gallery.helpers

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
 * This is deliberately surgery on the container rather than a re-encode: not one byte of the
 * compressed picture is touched, so a stripped file is pixel for pixel the file it came from. Going
 * through a decoder instead would re-compress the image and lose quality for nothing.
 *
 * The three formats are the ones the app can write metadata into at all (see [MetadataStripper]),
 * and each is walked in its own terms in a file of its own - [walkJpeg], [walkPng], [walkWebp].
 * Anything else is refused outright: a container this does not understand is one it could corrupt.
 *
 * Every entry point blocks on file IO. Call it off the main thread.
 */
internal object ContainerMetadata {
    /** Every metadata block [file] carries, empty when the format is not one that can be rewritten. */
    @Suppress("TooGenericExceptionCaught") // a truncated or lying file throws from anywhere in the walk
    fun blocksIn(file: File): Set<MetadataBlock> = try {
        when (formatOf(file)) {
            Format.JPEG -> walkJpeg(file, out = null, drop = emptySet())
            Format.PNG -> walkPng(file, out = null, drop = emptySet())
            Format.WEBP -> walkWebp(file, out = null, drop = emptySet())
            null -> emptySet()
        }
    } catch (ignored: Exception) {
        emptySet()
    }

    /**
     * Writes [source] to [destination] leaving out every block in [drop], returning whether it was
     * written. A format that cannot be walked is refused rather than copied, so a caller never ends
     * up with a "stripped" file that is only a copy of the original.
     */
    fun rewrite(source: File, destination: File, drop: Set<MetadataBlock>): Boolean {
        val format = formatOf(source) ?: return false
        BufferedOutputStream(FileOutputStream(destination)).use { out ->
            when (format) {
                Format.JPEG -> walkJpeg(source, out, drop)
                Format.PNG -> walkPng(source, out, drop)
                Format.WEBP -> walkWebp(source, out, drop)
            }
        }

        return destination.length() > 0
    }

    private enum class Format { JPEG, PNG, WEBP }

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
