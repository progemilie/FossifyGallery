package org.fossify.gallery.helpers

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Priority
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import org.fossify.commons.extensions.isJpg
import org.fossify.commons.extensions.isRawFast
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * A picture to draw small. Loading one of these is loading the file at [path], except that the copy
 * a camera left inside it is used instead whenever there is one big enough for the size being asked
 * for - the same picture for a fraction of the work.
 *
 * Worth it because a JPEG cannot be decoded cheaply by asking for less of it: `inSampleSize` saves
 * the inverse transform but not the pass over the entropy-coded data, which is the bulk of the cost
 * and is the whole file however small the result. Measured on a 4032x3024, 4.4MB photo, decoding it
 * down to a 358px grid cell takes 37ms and down to a 26px one takes 33ms; the 512x384 copy stored
 * inside the same file makes that same 358px cell in 3ms.
 */
data class ThumbnailSource(val path: String)

/**
 * Serves [ThumbnailSource]s, handing everything it cannot answer from the embedded copy to whichever
 * loader would have taken the path anyway - so paths that are not files at all (OTG content uris)
 * and formats that carry nothing inside them go on loading exactly as before.
 */
class ExifThumbnailLoader(
    private val delegate: ModelLoader<String, InputStream>
) : ModelLoader<ThumbnailSource, InputStream> {

    override fun handles(model: ThumbnailSource) = true

    override fun buildLoadData(
        model: ThumbnailSource,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        val forWholeFile = delegate.buildLoadData(model.path, width, height, options) ?: return null
        return ModelLoader.LoadData(
            forWholeFile.sourceKey,
            ExifThumbnailFetcher(model.path, width, height, forWholeFile.fetcher)
        )
    }

    class Factory : ModelLoaderFactory<ThumbnailSource, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory) =
            ExifThumbnailLoader(multiFactory.build(String::class.java, InputStream::class.java))

        override fun teardown() = Unit
    }
}

private class ExifThumbnailFetcher(
    private val path: String,
    private val width: Int,
    private val height: Int,
    private val wholeFile: DataFetcher<InputStream>,
) : DataFetcher<InputStream> {

    private var embedded: InputStream? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        val thumbnail = readUsableThumbnail()
        if (thumbnail == null) {
            wholeFile.loadData(priority, callback)
            return
        }

        embedded = ByteArrayInputStream(thumbnail).also(callback::onDataReady)
    }

    override fun cleanup() {
        embedded?.runCatching { close() }
        embedded = null
        wholeFile.cleanup()
    }

    override fun cancel() = wholeFile.cancel()

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    // both roads lead to the same file, so whatever the whole-file fetcher calls itself holds here
    override fun getDataSource() = wholeFile.dataSource

    /**
     * The embedded copy, ready to be decoded, or null when the file has none, has one too small for
     * what was asked for, or is not the sort of file that has one.
     */
    @Suppress("TooGenericExceptionCaught") // a file that cannot be read this way is simply read the old way
    private fun readUsableThumbnail(): ByteArray? {
        // Target.SIZE_ORIGINAL asked for the picture itself, and there is no arguing with that
        if (width <= 0 || height <= 0) {
            return null
        }

        // only two families store one, and checking the name is far cheaper than finding out
        if (!path.isJpg() && !path.isRawFast()) {
            return null
        }

        // under this the whole file decodes in about the time it takes to find the copy inside it
        if (File(path).length() < SMALLEST_FILE_WORTH_LOOKING_INSIDE) {
            return null
        }

        return try {
            val exif = ExifInterface(path)
            if (!exif.hasThumbnail() || !exif.isThumbnailCompressed) {
                return null
            }

            val thumbnail = exif.thumbnailBytes ?: return null
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )

            if (!coversTarget(thumbnail, orientation)) {
                return null
            }

            withOrientation(thumbnail, orientation)
        } catch (ignored: Exception) {
            null
        } catch (ignored: OutOfMemoryError) {
            null
        }
    }

    /**
     * Whether the embedded copy has at least as many pixels each way as the thumbnail being drawn -
     * below that it would be an upscale, which is the "not good enough" this falls back from.
     */
    private fun coversTarget(thumbnail: ByteArray, orientation: Int): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return false
        }

        // a quarter turn either way is stored one way round and drawn the other
        val turned = orientation in TURNED_ORIENTATIONS
        val shownWidth = if (turned) bounds.outHeight else bounds.outWidth
        val shownHeight = if (turned) bounds.outWidth else bounds.outHeight
        return shownWidth >= width && shownHeight >= height
    }

    /**
     * [thumbnail] given an Exif header of its own saying which way up it is. The copy inside a photo
     * is stored the same way round as the photo, and says nothing about itself - so left alone Glide
     * would read it as upright, and a rotated or mirrored photo would come out of the grid facing a
     * different way from the one the viewer draws it. Handing it the tag lets Glide's own Exif
     * handling turn and flip it exactly as it does the picture.
     */
    private fun withOrientation(thumbnail: ByteArray, orientation: Int): ByteArray {
        val isJpeg = thumbnail.size > 2 &&
            thumbnail[0] == START_OF_IMAGE.first() && thumbnail[1] == START_OF_IMAGE.last()
        if (orientation <= ExifInterface.ORIENTATION_NORMAL || !isJpeg) {
            return thumbnail
        }

        return exifOrientationHeader(orientation) + thumbnail.copyOfRange(2, thumbnail.size)
    }

    /**
     * A start-of-image marker followed by the smallest legal Exif segment: a little-endian TIFF
     * header and one IFD holding nothing but the orientation.
     */
    // every number here is a byte of a file format, and naming them would only put a layer between
    // this and the specification it is a transcription of
    @Suppress("MagicNumber")
    private fun exifOrientationHeader(orientation: Int) = byteArrayOf(
        START_OF_IMAGE[0], START_OF_IMAGE[1],
        0xFF.toByte(), 0xE1.toByte(), 0x00, APP1_LENGTH, // APP1, counting itself
        'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0x00, 0x00,
        'I'.code.toByte(), 'I'.code.toByte(), 0x2A, 0x00, // little endian TIFF
        0x08, 0x00, 0x00, 0x00, // the IFD starts right after this header
        0x01, 0x00, // holding one entry
        0x12, 0x01, // tag 0x0112, Orientation
        0x03, 0x00, // of type SHORT
        0x01, 0x00, 0x00, 0x00, // one of them
        orientation.toByte(), 0x00, 0x00, 0x00, // written in place of the offset, being short enough
        0x00, 0x00, 0x00, 0x00, // and no IFD after it
    )

    private companion object {
        val START_OF_IMAGE = byteArrayOf(0xFF.toByte(), 0xD8.toByte())

        /** "Exif\0\0" + TIFF header + one entry + the terminator, plus the two bytes of the field. */
        const val APP1_LENGTH: Byte = 34

        /** The four Exif orientations that turn the picture a quarter of the way round. */
        val TURNED_ORIENTATIONS = setOf(
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE,
        )

        const val SMALLEST_FILE_WORTH_LOOKING_INSIDE = 128 * 1024L
    }
}
