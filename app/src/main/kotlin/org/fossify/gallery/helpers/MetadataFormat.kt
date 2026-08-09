package org.fossify.gallery.helpers

import android.graphics.Point
import com.awxkee.jxlcoder.JxlCoder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The value formatting behind the metadata sheet's pinned summary: turning raw numbers into the
 * strings a person reads. Nothing here touches metadata directories - it is all arithmetic and
 * text, kept apart from [MetadataSummary], which decides which fields get a row at all.
 */

/** What a composed value puts between its parts, matching the viewer's extended details header. */
internal const val SEPARATOR = "  ·  "

internal const val TRACK_VIDEO = "Video codec"
internal const val TRACK_AUDIO = "Audio codec"
internal val SUMMARY_TRACK_LABELS = setOf(TRACK_VIDEO, TRACK_AUDIO)

/** Beyond this the reduced fraction has stopped being a ratio anyone recognises. */
private const val MAX_RATIO_TERM = 40

private const val MILLIS_ROUNDING = 500L
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_MINUTE = 60L
private const val BITS_PER_MEGABIT = 1_000_000f

/** The label [MetadataReader] gives a track, so the summary can pick out the two that belong in it. */
internal fun trackLabel(mimeType: String, index: Int): String = when {
    mimeType.startsWith("video/") -> TRACK_VIDEO
    mimeType.startsWith("audio/") -> TRACK_AUDIO
    else -> "Track ${index + 1}"
}

internal fun aspectRatio(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return ""

    val divisor = gcd(width, height)
    val reducedWidth = width / divisor
    val reducedHeight = height / divisor
    if (reducedWidth <= MAX_RATIO_TERM && reducedHeight <= MAX_RATIO_TERM) {
        return "$reducedWidth:$reducedHeight"
    }

    // an odd crop reduces to something like 3811:2143, which tells nobody anything - a decimal
    // against 1 at least places it next to the ratios people do know
    val ratio = width.toFloat() / height
    return if (ratio >= 1f) {
        String.format(Locale.US, "%.2f:1", ratio)
    } else {
        String.format(Locale.US, "1:%.2f", 1f / ratio)
    }
}

private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

internal fun formatDuration(millis: Long): String {
    // rounded, and never down to nothing: a clip under a second is short, not absent, and 0:00
    // reads as a file that failed to open
    val totalSeconds = if (millis in 1 until MILLIS_PER_SECOND) {
        1L
    } else {
        (millis + MILLIS_ROUNDING) / MILLIS_PER_SECOND
    }

    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

internal fun formatBitrate(bitsPerSecond: Long) =
    String.format(Locale.US, "%.1f Mbps", bitsPerSecond / BITS_PER_MEGABIT)

/** JPEG XL is the one format neither BitmapFactory nor the framework can size. */
internal fun jxlResolution(path: String): Point? {
    return try {
        JxlCoder.getSize(File(path).readBytes())?.let { Point(it.width, it.height) }
    } catch (ignored: Exception) {
        null
    } catch (ignored: OutOfMemoryError) {
        null
    }
}

/** MediaMetadataRetriever answers in ISO 8601 basic format, e.g. 20240112T140512.000Z. */
internal fun parseMediaDate(value: String): Long? {
    return try {
        SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(value.substringBefore('.').removeSuffix("Z"))
            ?.time?.takeIf { it > 0 }
    } catch (ignored: Exception) {
        null
    }
}
