package org.fossify.gallery.helpers

import android.content.Context
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The column counts a media grid can be pinched through. Every screen takes a prefix of one shared
 * sequence - single steps while one more column is still a visible change, then [RUNG_GROWTH] apart:
 *
 *     1, 2, 3, 4, 5, 6, 7, 10, 14, 20, 28, 39, 55, 77 ...
 *
 * Past `interactiveMax` a tile is too small to carry a badge or be picked out by a finger, so those
 * rungs are drawn simplified. A screen decides only where the sequence is cut - at [interactiveMax],
 * plus [SIMPLIFIED_RUNGS] beyond it - which is what keeps every ladder short: stepping one column at
 * a time from fourteen to twenty is neither useful nor cheap, and on a wide screen there were a
 * dozen such steps.
 *
 * One sequence rather than one per screen also keeps [snap] exact. `Config` stores four counts
 * (orientation x scroll direction) and each is a rung of every other ladder, so rotating or turning
 * the grid sideways never drifts a count onto a neighbouring rung.
 */
class GridZoom private constructor(
    /** The largest count still drawn with everything a thumbnail carries and can be tapped for. */
    val interactiveMax: Int,
    /** Every count the grid can come to rest at, in order. */
    val rungs: List<Int>,
    /**
     * The size every simplified tile is decoded to. One size for all the rungs puts them on a
     * single cache entry, so pinching between them decodes nothing; it is taken from the second of
     * them rather than the first, whose tiles are the largest. Sizing for those stores twice the
     * pixels for the sake of one rung, and leaves it stretched by a rung's worth - 1.4x - which a
     * tile this small has nothing to show for.
     *
     * Rounded up to a power of two rather than to a rung of [ThumbnailSizes], which every other
     * thumbnail in the app takes. There is nothing left here for that ladder to collect - this is
     * already one entry standing in for three rungs - and rounding to the nearest rung would put the
     * size *under* the largest of their tiles instead of over it, which is the one place in the app
     * a thumbnail would be drawn half again its own size.
     */
    val simpleThumbnailSize: Int
) {
    /**
     * The largest rung whose items can still be tapped, for screens that have to name a count
     * rather than test one. Not [interactiveMax], which is a boundary and need not be a rung at all
     * - a screen fitting seventeen readable columns still steps 14 to 20.
     */
    val largestInteractive = rungs.last { it <= interactiveMax }

    fun isSimplified(columnCount: Int) = columnCount > interactiveMax

    /**
     * The rung [columnCount] belongs to - a stored count may predate this ladder, or another screen.
     * A count square between two rungs takes the lower one, leaving the larger tiles.
     */
    fun snap(columnCount: Int) = rungs.minByOrNull { abs(it - columnCount) } ?: columnCount

    /** One rung fewer columns, or the bottom of the ladder. */
    fun zoomIn(columnCount: Int) = rungs.lastOrNull { it < snap(columnCount) } ?: rungs.first()

    /** One rung more columns, or the top of the ladder. */
    fun zoomOut(columnCount: Int) = rungs.firstOrNull { it > snap(columnCount) } ?: rungs.last()

    companion object {
        /** Under this a tile has no room left for anything drawn over the picture. */
        private const val INTERACTIVE_MIN_TILE_DP = 55

        /** Up to this many columns, one column either way is still worth a rung of its own. */
        private const val LINEAR_MAX = 7

        /** How much wider each rung past [LINEAR_MAX] is than the one below it. */
        const val RUNG_GROWTH = 1.4f

        /**
         * A fixed count rather than a smallest-tile rule, so the ladder is the same shape on every
         * screen. Three lands the last tile at about a fifth of an interactive one, past which a
         * photo is no longer recognisable.
         */
        private const val SIMPLIFIED_RUNGS = 3

        /** Even a narrow screen keeps a few tappable counts to choose between. */
        private const val MIN_INTERACTIVE_MAX = 3

        /** However wide the screen, no more rungs than a finger will walk through. */
        private const val MAX_RUNGS = 14

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

            val rungs = ladder(interactiveMax)
            // by value, not by index - the rungs below the boundary are no longer 1..interactiveMax
            val simplified = rungs.filter { it > interactiveMax }
            val sizedFor = simplified.getOrNull(1) ?: simplified.lastOrNull() ?: rungs.last()
            return GridZoom(interactiveMax, rungs, atLeastPowerOfTwo(acrossPx / sizedFor))
        }

        /** The shared sequence, cut [SIMPLIFIED_RUNGS] past [interactiveMax]. */
        private fun ladder(interactiveMax: Int): List<Int> {
            val rungs = mutableListOf<Int>()
            var next = 1
            var simplified = 0
            while (simplified < SIMPLIFIED_RUNGS && rungs.size < MAX_RUNGS) {
                rungs.add(next)
                if (next > interactiveMax) {
                    simplified++
                }

                next = if (next < LINEAR_MAX) next + 1 else (next * RUNG_GROWTH).roundToInt()
            }

            return rungs
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
