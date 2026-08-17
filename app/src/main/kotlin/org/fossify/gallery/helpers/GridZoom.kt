package org.fossify.gallery.helpers

import android.content.Context
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The column counts a media grid can be pinched through. Past `interactiveMax` a tile is too small
 * to carry a badge or be picked out by a finger, so those rungs are drawn simplified and spaced
 * well apart - stepping one column at a time from fourteen to twenty is neither useful nor cheap.
 *
 * Rungs are derived from the tile size rather than written down, so landscape, tablets and the
 * sideways-scrolling grid all reach the same tiles.
 */
class GridZoom private constructor(
    /** The largest count still drawn with everything a thumbnail carries and can be tapped for. */
    val interactiveMax: Int,
    /** Every count the grid can come to rest at, in order. */
    val rungs: List<Int>,
    /**
     * The size every simplified tile is decoded to, taken from the largest of them. One size for
     * all the rungs puts them on a single cache entry, so pinching between them decodes nothing.
     */
    val simpleThumbnailSize: Int
) {
    fun isSimplified(columnCount: Int) = columnCount > interactiveMax

    /** The rung [columnCount] belongs to - a stored count may predate this ladder, or another screen. */
    fun snap(columnCount: Int) = rungs.minByOrNull { abs(it - columnCount) } ?: columnCount

    /** One rung fewer columns, or the bottom of the ladder. */
    fun zoomIn(columnCount: Int) = rungs.lastOrNull { it < snap(columnCount) } ?: rungs.first()

    /** One rung more columns, or the top of the ladder. */
    fun zoomOut(columnCount: Int) = rungs.firstOrNull { it > snap(columnCount) } ?: rungs.last()

    companion object {
        /** Under this a tile has no room left for anything drawn over the picture. */
        private const val INTERACTIVE_MIN_TILE_DP = 55

        /** How much wider each simplified rung is than the one below it. */
        private const val RUNG_GROWTH = 1.4f

        /**
         * A fixed count rather than a smallest-tile rule, so the ladder is the same shape on every
         * screen. Three lands the last tile at about a fifth of an interactive one, past which a
         * photo is no longer recognisable.
         */
        private const val SIMPLIFIED_RUNGS = 3

        /** Even a narrow screen keeps a few tappable counts to choose between. */
        private const val MIN_INTERACTIVE_MAX = 3

        /** No tile is ever small enough to be worth decoding below this. */
        private const val MIN_THUMBNAIL_SIZE_PX = 32

        fun forMediaGrid(context: Context, scrollHorizontally: Boolean): GridZoom {
            val metrics = context.resources.displayMetrics
            // the span count divides the axis the grid does *not* scroll along
            val acrossPx = if (scrollHorizontally) metrics.heightPixels else metrics.widthPixels
            val acrossDp = (acrossPx / metrics.density).roundToInt()

            // rounded, not floored: flooring costs a screen a fraction of a dp short a whole column,
            // so a 384dp phone and a 411dp one would disagree over seven
            val interactiveMax = (acrossDp / INTERACTIVE_MIN_TILE_DP.toFloat())
                .roundToInt()
                .coerceAtLeast(MIN_INTERACTIVE_MAX)

            val rungs = (1..interactiveMax).toMutableList()
            var next = interactiveMax
            repeat(SIMPLIFIED_RUNGS) {
                next = (next * RUNG_GROWTH).roundToInt()
                rungs.add(next)
            }

            val firstSimplified = rungs.getOrNull(interactiveMax) ?: interactiveMax
            return GridZoom(interactiveMax, rungs, atLeastPowerOfTwo(acrossPx / firstSimplified))
        }

        // a power of two is also what the decoder samples down to most cheaply
        private fun atLeastPowerOfTwo(size: Int): Int {
            var result = MIN_THUMBNAIL_SIZE_PX
            while (result < size) {
                result *= 2
            }

            return result
        }
    }
}
