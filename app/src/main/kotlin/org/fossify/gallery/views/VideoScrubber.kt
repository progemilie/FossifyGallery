package org.fossify.gallery.views

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import org.fossify.gallery.R
import kotlin.math.roundToInt

private const val UNPLAYED_SHADE_ALPHA = 110
private const val FRAME_FADE_MS = 180L
private const val OPAQUE = 255

/**
 * A video's progress bar drawn as a strip of its own frames, darkened ahead of the playhead, or as a
 * plain line. A SeekBar underneath, so a drag, a tap, keys and TalkBack work as on any seek bar.
 */
class VideoScrubber @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatSeekBar(context, attrs) {

    private val radius = resources.getDimension(R.dimen.video_scrubber_radius)
    private val playheadWidth = resources.getDimension(R.dimen.video_scrubber_playhead_width)
    private val playheadOverhang = resources.getDimension(R.dimen.video_scrubber_playhead_overhang)
    private val progressLine = ProgressLine(resources)

    private val framePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint().apply { color = withShadeAlpha(Color.WHITE) }
    private val shadePaint = Paint().apply { color = withShadeAlpha(Color.BLACK) }
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    // so the playhead reads over a white frame too
    private val playheadRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shadePaint.color }

    private val track = RectF()
    private val clip = Path()
    private val source = Rect()
    private val cell = RectF()
    private val playhead = RectF()

    // null until read; arrivals time each frame's fade in
    private var frames = emptyArray<Bitmap?>()
    private var arrivals = LongArray(0)
    private var path = ""
    private var video = ""

    // the video and the track size the frames are for
    private var framesKey = ""

    // a load that is no longer the latest stops, and its frames are dropped
    @Volatile
    private var loadId = 0

    /** Only the page on screen reads frames, so the playing video shares the decoders with one load. */
    var isOnScreen = false
        set(value) {
            field = value
            if (value) showFrames() else stopLoading()
        }

    /** False draws a plain line, and reads no frames. */
    var showsFrames = true
        set(value) {
            if (field == value) {
                return
            }

            field = value
            if (value) {
                showFrames()
            } else {
                stopLoading()
                frames = emptyArray()
            }

            invalidate()
        }

    init {
        // the frames are the track, and the playhead is drawn here too
        progressDrawable = null
        thumb = null
        splitTrack = false
        background = null
    }

    /** [signature] tells a changed file from the cached one. */
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
        stopLoading()
    }

    // a new track size reads a new set, the old frames staying up until it comes; cached ones show on any page
    private fun showFrames() {
        if (!showsFrames) {
            return
        }

        val trackWidth = width - paddingLeft - paddingRight
        val trackHeight = height - paddingTop - paddingBottom
        if (video.isEmpty() || trackWidth <= 0 || trackHeight <= 0) {
            return
        }

        val key = "$video|${trackWidth}x$trackHeight"
        val cached = VideoScrubberFrames.cached(key)
        if (key == framesKey || cached == null && !isOnScreen) {
            return
        }

        val sameVideo = framesKey.startsWith("$video|")
        framesKey = key
        loadId++
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

        val played = track.left + track.width() * if (max > 0) progress / max.toFloat() else 0f
        if (!showsFrames) {
            progressLine.draw(canvas, track, played)
            return
        }

        clip.reset()
        clip.addRoundRect(track, radius, radius, Path.Direction.CW)
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

    /** Writes the cell-shaped middle of [frame] into [source]. */
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

    // an unfinished load is dropped, and read again when next wanted
    private fun stopLoading() {
        framesKey = ""
        loadId++
    }

    private fun loadFrames(key: String, trackWidth: Int, trackHeight: Int) {
        val id = loadId
        val isWanted = { loadId == id }
        VideoScrubberFrames.load(context, path, key, trackWidth, trackHeight, isWanted) { index, count, frame ->
            post {
                if (loadId == id) {
                    // the first frame of a new set brings its own cell count
                    if (frames.size != count) {
                        frames = arrayOfNulls(count)
                        arrivals = LongArray(count)
                    }

                    frames[index] = frame
                    arrivals[index] = SystemClock.uptimeMillis()
                    invalidate()
                }
            }
        }
    }
}

/** The progress bar as a plain line with a round handle, across the middle of the frames' track. */
private class ProgressLine(resources: Resources) {
    private val height = resources.getDimension(R.dimen.video_scrubber_line_height)
    private val rim = resources.getDimension(R.dimen.video_scrubber_line_rim)
    private val handleRadius = resources.getDimension(R.dimen.video_scrubber_handle_radius)

    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val unplayedPaint = Paint().apply { color = withShadeAlpha(Color.WHITE) }

    // so the line reads over a white video too
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withShadeAlpha(Color.BLACK) }

    private val line = RectF()
    private val clip = Path()

    fun draw(canvas: Canvas, track: RectF, played: Float) {
        val centreY = track.centerY()
        line.set(track.left, centreY - height / 2, track.right, centreY + height / 2)
        line.inset(-rim, -rim)
        canvas.drawRoundRect(line, line.height() / 2, line.height() / 2, rimPaint)
        line.inset(rim, rim)

        clip.reset()
        clip.addRoundRect(line, height / 2, height / 2, Path.Direction.CW)
        canvas.withClip(clip) {
            drawRect(line, unplayedPaint)
            drawRect(line.left, line.top, played, line.bottom, playedPaint)
        }

        canvas.drawCircle(played, centreY, handleRadius + rim, rimPaint)
        canvas.drawCircle(played, centreY, handleRadius, playedPaint)
    }
}

private fun withShadeAlpha(color: Int) = ColorUtils.setAlphaComponent(color, UNPLAYED_SHADE_ALPHA)
