package org.fossify.gallery.views

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
import android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
import android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
import android.media.MediaMetadataRetriever.OPTION_CLOSEST
import android.media.MediaMetadataRetriever.OPTION_PREVIOUS_SYNC
import android.os.Build
import android.util.LruCache
import androidx.annotation.WorkerThread
import androidx.core.graphics.scale
import androidx.core.net.toUri
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** A cell's width against its height, held to this range whatever the video's own shape. */
private const val MIN_CELL_ASPECT = 0.5f
private const val MAX_CELL_ASPECT = 1.8f
private const val MAX_FRAMES = 24

private const val QUARTER_TURN = 90
private const val US_PER_MS = 1000L
private const val FRAMES_CACHE_BYTES = 6 * 1024 * 1024

/**
 * The frames a [VideoScrubber] draws its track with: as many as fill it at the video's own proportions,
 * spread over its length. Loads run one at a time - each holds a decoder, which the video playing
 * meanwhile needs too - and a finished one is kept for the next time the same video comes round at the
 * same size.
 */
object VideoScrubberFrames {
    private val loader = Executors.newSingleThreadExecutor()

    private val cache = object : LruCache<String, Array<Bitmap?>>(FRAMES_CACHE_BYTES) {
        // a frame repeated in a cell after it is counted once
        override fun sizeOf(key: String, value: Array<Bitmap?>) =
            value.distinct().sumOf { it?.allocationByteCount ?: 0 }
    }

    fun cached(key: String): Array<Bitmap?>? = cache.get(key)

    /**
     * Reads the frames of [path] for a track of [trackWidth] by [trackHeight] and keeps them as [key],
     * handing [onFrame] each as it comes along with how many cells there are, on the loader thread. A
     * load gives up as soon as [isWanted] says so, before it has opened the file if it can.
     */
    fun load(
        context: Context,
        path: String,
        key: String,
        trackWidth: Int,
        trackHeight: Int,
        isWanted: () -> Boolean,
        onFrame: (index: Int, count: Int, frame: Bitmap) -> Unit,
    ) {
        val appContext = context.applicationContext
        loader.execute {
            if (isWanted()) {
                read(appContext, path, trackWidth, trackHeight, isWanted, onFrame)?.let { cache.put(key, it) }
            }
        }
    }

    /**
     * Blocking, call it off the main thread. Null if the frames stopped being wanted, or none could be
     * read.
     */
    // a file the retriever cannot read leaves the placeholder, whatever the reason
    @Suppress("TooGenericExceptionCaught")
    @WorkerThread
    private fun read(
        context: Context,
        path: String,
        trackWidth: Int,
        trackHeight: Int,
        isWanted: () -> Boolean,
        onFrame: (index: Int, count: Int, frame: Bitmap) -> Unit,
    ): Array<Bitmap?>? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setSource(context, path)
            val duration = retriever.extractMetadata(METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            val aspect = retriever.displayAspect()
            val count = ceil(trackWidth / (trackHeight * aspect.coerceIn(MIN_CELL_ASPECT, MAX_CELL_ASPECT)))
                .toInt().coerceIn(1, MAX_FRAMES)
            val cellWidth = (trackWidth / count).coerceAtLeast(1)
            // the smallest frame that still covers a cell once cropped to its proportions
            val width = max(cellWidth, ceil(trackHeight * aspect).toInt())
            val height = max(trackHeight, ceil(cellWidth / aspect).toInt())

            val read = arrayOfNulls<Bitmap>(count)
            var keyframe: Bitmap? = null
            for (index in 0 until count) {
                if (!isWanted()) {
                    return null
                }

                // the keyframe before the middle of the stretch each cell stands for: decoding on from one to
                // the exact frame is most of the cost, and a phone puts one in every second or so. Where they
                // lie further apart than the cells, as in a screen recording, the last keyframe comes round
                // again for a cell well past it, and that cell is decoded on to its own frame
                val timeUs = duration * US_PER_MS * (2 * index + 1) / (2 * count)
                var frame = retriever.frameAt(timeUs, OPTION_PREVIOUS_SYNC, width, height)
                if (frame == null || keyframe?.sameAs(frame) == true) {
                    frame = retriever.frameAt(timeUs, OPTION_CLOSEST, width, height)
                } else {
                    keyframe = frame
                }

                // one that cannot be read at all repeats the last
                val shown = frame ?: read.getOrNull(index - 1) ?: continue
                read[index] = shown
                onFrame(index, count, shown)
            }

            read.takeIf { frames -> frames.any { it != null } }
        } catch (ignored: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}

/** A video handed over by another app can come as a content:// uri rather than a path. */
private fun MediaMetadataRetriever.setSource(context: Context, path: String) {
    if (path.startsWith("content://")) {
        setDataSource(context, path.toUri())
    } else {
        setDataSource(path)
    }
}

/** The video's width over its height as it is shown, turned or not. */
private fun MediaMetadataRetriever.displayAspect(): Float {
    val width = extractMetadata(METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()?.takeIf { it > 0f } ?: return 1f
    val height = extractMetadata(METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()?.takeIf { it > 0f } ?: return 1f
    val rotation = extractMetadata(METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
    return if (rotation / QUARTER_TURN % 2 == 1) height / width else width / height
}

/** The frame at [timeUs] scaled to fit [width] by [height], which the retriever does itself from API 27. */
private fun MediaMetadataRetriever.frameAt(timeUs: Long, option: Int, width: Int, height: Int): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        return getScaledFrameAtTime(timeUs, option, width, height)
    }

    val frame = getFrameAtTime(timeUs, option) ?: return null
    val scale = minOf(width / frame.width.toFloat(), height / frame.height.toFloat())
    return if (scale >= 1f) {
        frame
    } else {
        frame.scale((frame.width * scale).roundToInt(), (frame.height * scale).roundToInt())
            .also { if (it !== frame) frame.recycle() }
    }
}
