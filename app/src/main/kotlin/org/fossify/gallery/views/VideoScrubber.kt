package org.fossify.gallery.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
import android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
import android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
import android.media.MediaMetadataRetriever.OPTION_CLOSEST
import android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.util.LruCache
import androidx.annotation.WorkerThread
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import kotlin.math.ceil
import kotlin.math.roundToInt

/** How dark the frames still to be played are kept, against the ones already played. */
private const val UNPLAYED_SHADE_ALPHA = 110

/** A frame's width against its height, held to this range whatever the video's own shape. */
private const val MIN_FRAME_ASPECT = 0.5f
private const val MAX_FRAME_ASPECT = 1.8f
private const val MAX_FRAMES = 24

/** A video turned a quarter either way is shown with its width and height the other way round. */
private const val QUARTER_TURN = 90

private const val US_PER_MS = 1000L
private const val FRAMES_CACHE_BYTES = 6 * 1024 * 1024
private const val FRAME_FADE_MS = 180L
private const val OPAQUE = 255

/**
 * A video's progress bar drawn as a strip of its own frames, which stands where the viewer's
 * thumbnail strip does for a photo - the way a phone's own gallery scrubs a video. A SeekBar
 * underneath, so a drag, a tap, a keyboard and TalkBack move it as they move any other: only its
 * look is its own, the frames darkened ahead of the playhead, and the playhead itself.
 *
 * The frames are read off the file on a background thread, each shown as it comes, and kept for
 * the next time the same video comes round at the same size.
 */
class VideoScrubber @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatSeekBar(context, attrs) {

    private val radius = resources.getDimension(R.dimen.video_scrubber_radius)
    private val playheadWidth = resources.getDimension(R.dimen.video_scrubber_playhead_width)
    private val playheadOverhang = resources.getDimension(R.dimen.video_scrubber_playhead_overhang)

    private val framePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint().apply { color = withShadeAlpha(Color.WHITE) }
    private val shadePaint = Paint().apply { color = withShadeAlpha(Color.BLACK) }
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    // a dark rim round the playhead, so it reads over a white frame as well as a black one
    private val playheadRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shadePaint.color }

    private val track = RectF()
    private val clip = Path()
    private val source = Rect()
    private val cell = RectF()
    private val playhead = RectF()

    // a cell's frame is null until it has been read, and fades in from when it was
    private var frames = emptyArray<Bitmap?>()
    private var arrivals = LongArray(0)
    private var path = ""
    private var video = ""

    // the video and the track size the frames are for
    private var framesKey = ""

    // the latest load; one that is not stops, and what it has read goes unseen
    @Volatile
    private var loadId = 0

    init {
        // the frames are the track, and the playhead is drawn here too
        progressDrawable = null
        thumb = null
        splitTrack = false
        background = null
    }

    /** The video to show frames of; [signature] tells a changed file from the one cached. */
    fun setVideo(path: String, signature: String) {
        this.path = path
        video = "$path|$signature"
        showFrames()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        showFrames()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        showFrames()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // a page gone before its frames came has no use for the rest of them
        framesKey = ""
        loadId++
    }

    // a track of another size wants as many frames as fill it, and the ones up stay until those start coming
    private fun showFrames() {
        val trackWidth = width - paddingLeft - paddingRight
        val trackHeight = height - paddingTop - paddingBottom
        if (video.isEmpty() || trackWidth <= 0 || trackHeight <= 0) {
            return
        }

        val key = "$video|${trackWidth}x$trackHeight"
        if (key == framesKey) {
            return
        }

        val sameVideo = framesKey.startsWith("$video|")
        framesKey = key
        loadId++
        val cached = cache.get(key)
        if (cached != null) {
            frames = cached.copyOf()
            arrivals = LongArray(cached.size)
            invalidate()
        } else {
            if (!sameVideo) {
                frames = emptyArray()
                invalidate()
            }

            loadFrames(key, trackWidth, trackHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        track.set(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            (width - paddingRight).toFloat(),
            (height - paddingBottom).toFloat()
        )

        if (track.width() <= 0f || track.height() <= 0f) {
            return
        }

        clip.reset()
        clip.addRoundRect(track, radius, radius, Path.Direction.CW)
        val played = track.left + track.width() * if (max > 0) progress / max.toFloat() else 0f
        canvas.withClip(clip) {
            drawFrames(this)
            drawRect(played, track.top, track.right, track.bottom, shadePaint)
        }

        playhead.set(
            played - playheadWidth / 2,
            track.top - playheadOverhang,
            played + playheadWidth / 2,
            track.bottom + playheadOverhang
        )

        val rim = playheadWidth / 2
        playhead.inset(-rim, -rim)
        canvas.drawRoundRect(playhead, playheadWidth, playheadWidth, playheadRimPaint)
        playhead.inset(rim, rim)
        canvas.drawRoundRect(playhead, playheadWidth / 2, playheadWidth / 2, playheadPaint)
    }

    private fun drawFrames(canvas: Canvas) {
        if (frames.isEmpty()) {
            canvas.drawRect(track, placeholderPaint)
            return
        }

        val now = SystemClock.uptimeMillis()
        var isFading = false
        val cellWidth = track.width() / frames.size
        frames.forEachIndexed { index, frame ->
            cell.set(track.left + index * cellWidth, track.top, track.left + (index + 1) * cellWidth, track.bottom)
            val shownFor = now - arrivals[index]
            val alpha = if (frame == null) 0 else (shownFor * OPAQUE / FRAME_FADE_MS).toInt().coerceAtMost(OPAQUE)
            if (alpha < OPAQUE) {
                canvas.drawRect(cell, placeholderPaint)
            }

            if (frame != null) {
                isFading = isFading || alpha < OPAQUE
                centreCrop(frame, cell.width() / cell.height())
                framePaint.alpha = alpha
                canvas.drawBitmap(frame, source, cell, framePaint)
            }
        }

        if (isFading) {
            postInvalidateOnAnimation()
        }
    }

    /** The middle of [frame] with the proportions of the cell it is drawn into, into [source]. */
    private fun centreCrop(frame: Bitmap, cellAspect: Float) {
        val frameAspect = frame.width / frame.height.toFloat()
        if (frameAspect > cellAspect) {
            val width = (frame.height * cellAspect).roundToInt()
            val left = (frame.width - width) / 2
            source.set(left, 0, left + width, frame.height)
        } else {
            val height = (frame.width / cellAspect).roundToInt()
            val top = (frame.height - height) / 2
            source.set(0, top, frame.width, top + height)
        }
    }

    private fun loadFrames(key: String, trackWidth: Int, trackHeight: Int) {
        val path = path
        val id = loadId
        ensureBackgroundThread {
            readFrames(path, id, trackWidth, trackHeight)?.let { cache.put(key, it) }
        }
    }

    /**
     * Reads as many frames as fill the track at the video's own proportions, spread over its length,
     * handing each to the main thread as it comes. Blocking, call it off the main thread. Null if load
     * [id] stopped being the latest, or no frame could be read.
     */
    // a file the retriever cannot read leaves the placeholder, whatever the reason
    @Suppress("TooGenericExceptionCaught")
    @WorkerThread
    private fun readFrames(path: String, id: Int, trackWidth: Int, trackHeight: Int): Array<Bitmap?>? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val duration = retriever.extractMetadata(METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return null
            val count = ceil(trackWidth / (trackHeight * retriever.frameAspect())).toInt().coerceIn(1, MAX_FRAMES)
            val frameWidth = (trackWidth / count).coerceAtLeast(1)
            post {
                if (loadId == id) {
                    frames = arrayOfNulls(count)
                    arrivals = LongArray(count)
                    invalidate()
                }
            }

            val read = arrayOfNulls<Bitmap>(count)
            for (index in 0 until count) {
                if (loadId != id) {
                    return null
                }

                // the middle of each stretch of the video its cell stands for, as the nearest keyframe: a phone
                // puts one in every second or so, and decoding on from one to the exact frame is most of the
                // cost. Only keyframes further apart than the cells, which would repeat, are decoded on from.
                val timeUs = duration * US_PER_MS * (2 * index + 1) / (2 * count)
                val previous = read.getOrNull(index - 1)
                var frame = retriever.frameAt(timeUs, OPTION_CLOSEST_SYNC, frameWidth, trackHeight)
                if (frame == null || (previous != null && frame.sameAs(previous))) {
                    frame = retriever.frameAt(timeUs, OPTION_CLOSEST, frameWidth, trackHeight)
                }

                // one that cannot be read at all repeats the last
                val shown = frame ?: previous ?: continue
                read[index] = shown
                post {
                    if (loadId == id) {
                        frames[index] = shown
                        arrivals[index] = SystemClock.uptimeMillis()
                        invalidate()
                    }
                }
            }

            read.takeIf { frames -> frames.any { it != null } }
        } catch (ignored: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private companion object {
        val cache = object : LruCache<String, Array<Bitmap?>>(FRAMES_CACHE_BYTES) {
            // a frame repeated in a cell after it is counted once
            override fun sizeOf(key: String, value: Array<Bitmap?>) =
                value.distinct().sumOf { it?.allocationByteCount ?: 0 }
        }
    }
}

private fun withShadeAlpha(color: Int) = ColorUtils.setAlphaComponent(color, UNPLAYED_SHADE_ALPHA)

/** The video's width over its height as it is shown, turned or not, held to the range a cell can have. */
private fun MediaMetadataRetriever.frameAspect(): Float {
    val width = extractMetadata(METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()?.takeIf { it > 0f } ?: return 1f
    val height = extractMetadata(METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()?.takeIf { it > 0f } ?: return 1f
    val rotation = extractMetadata(METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
    val isSideways = rotation / QUARTER_TURN % 2 == 1
    val aspect = if (isSideways) height / width else width / height
    return aspect.coerceIn(MIN_FRAME_ASPECT, MAX_FRAME_ASPECT)
}

/**
 * A frame no smaller than [width] by [height], and not much bigger: API 27 scales it while decoding,
 * before that it is scaled once decoded.
 */
private fun MediaMetadataRetriever.frameAt(timeUs: Long, option: Int, width: Int, height: Int): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        return getScaledFrameAtTime(timeUs, option, width * 2, height * 2)
    }

    val frame = getFrameAtTime(timeUs, option) ?: return null
    val scale = maxOf(width * 2f / frame.width, height * 2f / frame.height).coerceAtMost(1f)
    return if (scale >= 1f) {
        frame
    } else {
        frame.scale((frame.width * scale).roundToInt(), (frame.height * scale).roundToInt())
            .also { if (it !== frame) frame.recycle() }
    }
}
