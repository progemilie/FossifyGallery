package org.fossify.gallery.helpers

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The container walkers against hand built files, byte for byte. The malformed cases are the ones
 * that matter most: an in-place strip copies the result over the original, so a walk that stops
 * short must be refused rather than written.
 */
class ContainerMetadataTest {
    @get:Rule
    val folder = TemporaryFolder()

    // JPEG

    private val jfif = segment(0xE0, ascii("JFIF\u0000") + ByteArray(9))
    private val jpegExif = segment(0xE1, ascii("Exif\u0000\u0000") + ByteArray(20) { 1 })
    private val jpegXmp = segment(0xE1, ascii("http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta/>"))
    private val jpegIcc = segment(0xE2, ascii("ICC_PROFILE\u0000") + ByteArray(10))
    private val jpegComment = segment(0xFE, ascii("a comment"))
    private val quantTable = segment(0xDB, ByteArray(65) { it.toByte() })
    private val scanHeader = segment(0xDA, ByteArray(10))

    // entropy coded data, with a stuffed 0xFF00 in it, then the end marker and a trailer
    private val scan = bytes(0x12, 0x34, 0xFF, 0x00, 0x56, 0xFF, 0xD9) + ascii("trailer")

    private fun jpeg(vararg segments: ByteArray) = bytes(0xFF, 0xD8) + segments.concat()

    private val fullJpeg = jpeg(jfif, jpegExif, jpegXmp, jpegIcc, jpegComment, quantTable, scanHeader, scan)

    @Test
    fun `jpeg blocks are found`() {
        val blocks = ContainerMetadata.blocksIn(write(fullJpeg))
        assertEquals(setOf(MetadataBlock.EXIF, MetadataBlock.XMP, MetadataBlock.ICC, MetadataBlock.OTHER), blocks)
    }

    @Test
    fun `jpeg drops only the asked for segments and keeps the picture`() {
        val out = rewrite(fullJpeg, setOf(MetadataBlock.EXIF))
        assertArrayEquals(jpeg(jfif, jpegXmp, jpegIcc, jpegComment, quantTable, scanHeader, scan), out)
    }

    @Test
    fun `jpeg keeps the segments that say how to decode it`() {
        val out = rewrite(fullJpeg, MetadataBlock.entries.toSet())
        assertArrayEquals(jpeg(jfif, quantTable, scanHeader, scan), out)
    }

    @Test
    fun `a file with nothing to remove is told apart from one that cannot be rewritten`() {
        val bare = jpeg(jfif, quantTable, scanHeader, scan)
        assertEquals(emptySet<MetadataBlock>(), ContainerMetadata.blocksIn(write(bare)))
        assertNull(ContainerMetadata.blocksIn(write(ascii("GIF89a") + ByteArray(20))))
    }

    @Test
    fun `jpeg with a nonsense segment length is refused`() {
        val broken = jpeg(jfif, bytes(0xFF, 0xE1, 0x00, 0x01), quantTable, scanHeader, scan)
        assertRefused(broken)
    }

    @Test
    fun `jpeg with stray bytes between segments is refused`() {
        val broken = jpeg(jfif, bytes(0x00, 0x00), jpegExif, quantTable, scanHeader, scan)
        assertRefused(broken)
    }

    @Test
    fun `jpeg that ends before its picture is refused`() {
        assertRefused(jpeg(jfif, jpegExif, quantTable))
    }

    // PNG

    private val header = pngChunk("IHDR", ByteArray(13))
    private val text = pngChunk("tEXt", ascii("Comment\u0000hello"))
    private val pngXmp = pngChunk("iTXt", ascii("XML:com.adobe.xmp\u0000\u0000\u0000\u0000\u0000<x:xmpmeta/>"))
    private val pngExif = pngChunk("eXIf", ByteArray(12) { 2 })
    private val imageData = pngChunk("IDAT", ByteArray(40) { (it * 7).toByte() })
    private val end = pngChunk("IEND", ByteArray(0))

    private fun png(vararg chunks: ByteArray) = PNG_SIGNATURE + chunks.concat()

    private val fullPng = png(header, text, pngXmp, pngExif, imageData, end)

    @Test
    fun `png blocks are found`() {
        val blocks = ContainerMetadata.blocksIn(write(fullPng))
        assertEquals(setOf(MetadataBlock.OTHER, MetadataBlock.XMP, MetadataBlock.EXIF), blocks)
    }

    @Test
    fun `png drops only the asked for chunks`() {
        val out = rewrite(fullPng, setOf(MetadataBlock.XMP, MetadataBlock.OTHER))
        assertArrayEquals(png(header, pngExif, imageData, end), out)
    }

    @Test
    fun `png without its end chunk is refused`() {
        assertRefused(png(header, pngExif, imageData))
    }

    @Test
    fun `png cut off inside its image data is refused`() {
        assertRefused(png(header, pngExif, imageData).copyOf(PNG_SIGNATURE.size + 60))
    }

    // WebP

    private fun features(flags: Int) = webpChunk("VP8X", bytes(flags, 0, 0, 0, 0, 0, 0, 0, 0, 0))

    private val webpIcc = webpChunk("ICCP", ByteArray(7) { 3 }) // odd length, so padded
    private val bitstream = webpChunk("VP8 ", ByteArray(21) { it.toByte() })
    private val webpExif = webpChunk("EXIF", ByteArray(12) { 4 })
    private val webpXmp = webpChunk("XMP ", ascii("<x/>"))

    private val fullWebp = riff(features(ICC_EXIF_XMP), webpIcc, bitstream, webpExif, webpXmp)

    @Test
    fun `webp blocks are found`() {
        val blocks = ContainerMetadata.blocksIn(write(fullWebp))
        assertEquals(setOf(MetadataBlock.ICC, MetadataBlock.EXIF, MetadataBlock.XMP), blocks)
    }

    @Test
    fun `webp drops the chunk, its flag and recounts the header`() {
        val out = rewrite(fullWebp, setOf(MetadataBlock.EXIF))
        assertArrayEquals(riff(features(ICC_EXIF_XMP and EXIF_FLAG.inv()), webpIcc, bitstream, webpXmp), out)
    }

    @Test
    fun `webp leaves out what lies past the end of the riff`() {
        val out = rewrite(fullWebp + ascii("junk"), emptySet())
        assertArrayEquals(fullWebp, out)
    }

    @Test
    fun `webp claiming more than the file holds is refused`() {
        assertRefused(fullWebp.copyOf(fullWebp.size - 6))
    }

    @Test
    fun `webp chunk overrunning the riff is refused`() {
        val overrun = webpChunk("VP8 ", ByteArray(8)).also { it[4] = 0x7F } // claims 127 bytes
        assertRefused(riff(features(0), overrun))
    }

    // plumbing

    private fun rewrite(source: ByteArray, drop: Set<MetadataBlock>): ByteArray {
        val destination = folder.newFile()
        assertTrue("rewrite refused a well formed file", ContainerMetadata.rewrite(write(source), destination, drop))
        return destination.readBytes()
    }

    private fun assertRefused(source: ByteArray) {
        val file = write(source)
        assertFalse("rewrite accepted a malformed file", ContainerMetadata.rewrite(file, folder.newFile(), emptySet()))
        assertNull("a file rewrite refuses should offer nothing", ContainerMetadata.blocksIn(file))
    }

    private fun write(content: ByteArray): File = folder.newFile().apply { writeBytes(content) }

    private companion object {
        const val EXIF_FLAG = 0x08
        const val ICC_EXIF_XMP = 0x20 or EXIF_FLAG or 0x04

        fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

        fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

        fun Array<out ByteArray>.concat() = fold(ByteArray(0)) { all, next -> all + next }

        fun segment(marker: Int, payload: ByteArray): ByteArray {
            val length = payload.size + 2
            return bytes(0xFF, marker, length shr 8, length and 0xFF) + payload
        }

        // the walker never checks a CRC, so a zeroed one stands in
        fun pngChunk(type: String, data: ByteArray) = bigEndian(data.size) + ascii(type) + data + ByteArray(4)

        fun webpChunk(tag: String, data: ByteArray): ByteArray {
            val pad = ByteArray(data.size % 2)
            return ascii(tag) + littleEndian(data.size) + data + pad
        }

        fun riff(vararg chunks: ByteArray): ByteArray {
            val body = ascii("WEBP") + chunks.concat()
            return ascii("RIFF") + littleEndian(body.size) + body
        }

        fun bigEndian(value: Int) = bytes(value ushr 24, value ushr 16, value ushr 8, value)

        fun littleEndian(value: Int) = bytes(value, value ushr 8, value ushr 16, value ushr 24)
    }
}
