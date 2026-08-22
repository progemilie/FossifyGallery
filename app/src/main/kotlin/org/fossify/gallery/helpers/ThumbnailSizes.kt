package org.fossify.gallery.helpers

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The ladder of sizes a thumbnail is decoded to:
 *
 *     36, 51, 71, 100, 140, 196, 274, 384, 538, 753, 1054, 1475, 2065 ...
 *
 * The size asked for is part of Glide's cache key, so a grid drawing 282px tiles and one drawing
 * 268px tiles store the same picture twice over for a difference nobody can see. Every grid rounds
 * its tile to a rung of this instead and lets the ImageView take up what is left over - the sixteen
 * sizes the media grid used to ask for, across its column counts and both scroll directions, come to
 * eight.
 *
 * The rungs are [STEP] apart, the same ratio [GridZoom]'s column counts grow by once they stop
 * counting upwards, so two counts a rung apart on that ladder are at most a rung apart on this one.
 */
object ThumbnailSizes {

    /**
     * The short side of the copy a camera leaves inside a JPEG, and the rung the ladder is built
     * out from. [ThumbnailSource] draws a thumbnail from that copy rather than the whole file, but
     * only up to this size, and the copy is worth better than ten times what it costs. So the cliff
     * is put where a rung is rather than halfway up a step: nothing at or below it can be rounded
     * past it, and a tile a little above it rounds down onto it and picks the copy up.
     */
    private const val EXIF_THUMBNAIL_SHORT_SIDE = 384.0

    /** How much wider each rung is than the one below it. */
    const val STEP = 1.4

    /** No tile is ever small enough to be worth decoding below this. */
    private const val SMALLEST = 32

    /**
     * The rung nearest [px]. Nearest, rather than the rung below it: always rounding down would
     * leave a tile stretched by a whole step at worst, where nearest halves that to the square root
     * of one - 18%, which a photograph has nothing to show for. Rounding up instead would store more
     * pixels than the tile ever draws and save nothing at all.
     */
    fun snap(px: Int): Int {
        if (px <= SMALLEST) {
            return SMALLEST
        }

        val step = (ln(px / EXIF_THUMBNAIL_SHORT_SIDE) / ln(STEP)).roundToInt()
        return (EXIF_THUMBNAIL_SHORT_SIDE * STEP.pow(step)).roundToInt().coerceAtLeast(SMALLEST)
    }
}
