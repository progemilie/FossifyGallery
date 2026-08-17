package org.fossify.gallery.helpers

import android.content.Context
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The column counts a media grid can be pinched through, and which of them stop being drawn in
 * full.
 *
 * Past a certain tile size a thumbnail is nothing but its picture: no badge fits on one, no finger
 * can pick one out of its neighbours, and a screenful is several hundred of them. Those counts get
 * the stripped item `MediaAdapter` draws for them, and they are spaced well apart - stepping one
 * column at a time from fourteen to twenty is neither useful nor cheap.
 *
 * Rungs are worked out from the tile size rather than written down, so landscape, tablets and the
 * sideways-scrolling grid - whose span count is rows, dividing the height instead of the width -
 * all reach the same tiles, just more of them per screen.
 */
class GridZoom private constructor(
    /** The largest count still drawn with everything a thumbnail carries and can be tapped for. */
    val interactiveMax: Int,
    /** Every count the grid can come to rest at, in order. */
    val rungs: List<Int>,
    /**
     * The size a simplified thumbnail is decoded to, asked for instead of the view's own so that
     * every tile lands on one cache entry however big it happens to be drawn. Taken from the
     * largest tile any simplified rung draws, so none of them is ever scaled up, and shared by all
     * of them, so pinching between rungs is answered from memory rather than by decoding a
     * screenful over again at a slightly different size.
     */
    val simpleThumbnailSize: Int
) {
    fun isSimplified(columnCount: Int) = columnCount > interactiveMax

    /** The rung [columnCount] belongs to - a stored count predates this ladder, or another screen size. */
    fun snap(columnCount: Int) = rungs.minByOrNull { abs(it - columnCount) } ?: columnCount

    /** One rung fewer columns, or the bottom of the ladder. */
    fun zoomIn(columnCount: Int) = rungs.lastOrNull { it < snap(columnCount) } ?: rungs.first()

    /** One rung more columns, or the top of the ladder. */
    fun zoomOut(columnCount: Int) = rungs.firstOrNull { it > snap(columnCount) } ?: rungs.last()

    companion object {
        /** Under this a tile has no room left for anything drawn over the picture. */
        private const val INTERACTIVE_MIN_TILE_DP = 55

        /** Under this a photo is no longer recognisable, so there is nothing past it worth reaching. */
        private const val SIMPLE_MIN_TILE_DP = 20

        /** How much wider each simplified rung is than the one below it. */
        private const val RUNG_GROWTH = 1.4f

        /** Even a narrow screen keeps a few tappable counts to choose between. */
        private const val MIN_INTERACTIVE_MAX = 3

        /** No tile is ever small enough to be worth decoding below this. */
        private const val MIN_THUMBNAIL_SIZE_PX = 32

        fun forMediaGrid(context: Context, scrollHorizontally: Boolean): GridZoom {
            val metrics = context.resources.displayMetrics
            // the span count divides the axis the grid does *not* scroll along
            val acrossPx = if (scrollHorizontally) metrics.heightPixels else metrics.widthPixels
            val acrossDp = (acrossPx / metrics.density).roundToInt()

            val interactiveMax = (acrossDp / INTERACTIVE_MIN_TILE_DP).coerceAtLeast(MIN_INTERACTIVE_MAX)
            val rungs = (1..interactiveMax).toMutableList()
            var next = (interactiveMax * RUNG_GROWTH).roundToInt()
            while (acrossDp / next >= SIMPLE_MIN_TILE_DP) {
                rungs.add(next)
                next = (next * RUNG_GROWTH).roundToInt()
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
