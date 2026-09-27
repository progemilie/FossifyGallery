package org.fossify.gallery.helpers

import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection

/**
 * What every column count of one grid has in common: how wide it is, how its tiles are spaced, and
 * how long a grouping header is. See [GridZoomLayout].
 */
class GridShape(
    /** The grid's size across the way it scrolls, less its padding: what the spans share out. */
    val acrossSpace: Int,
    /** The padding ahead of the spans - left, or top for a grid scrolling sideways. */
    val acrossPadding: Int,
    val spacing: Int,
    val sideSpacing: Boolean,
    val horizontal: Boolean,
    /** Spans laid out from the right, as a vertical grid is in a right-to-left locale. */
    val rtl: Boolean,
    val headerLength: Int,
)

/**
 * A grid's list cut into its grouping sections: a header where there is one, and the run of media
 * under it. A list with no headers is one section.
 */
class GridSections(items: List<ThumbnailItem>) {
    /** Whether a tile's column counts from its own section - see [GridSpacingItemDecoration]. */
    val isGrouped = items.firstOrNull() is ThumbnailSection

    val count: Int

    /** Where each section starts in the list: at its header, or at its first medium. */
    private val starts: IntArray
    private val headed: BooleanArray
    private val sizes: IntArray
    private val headersAhead: IntArray

    init {
        var sections = 0
        items.forEachIndexed { position, item ->
            if (item is ThumbnailSection || position == 0) {
                sections++
            }
        }

        count = sections
        starts = IntArray(sections)
        headed = BooleanArray(sections)
        sizes = IntArray(sections)
        headersAhead = IntArray(sections)
        var section = -1
        var headers = 0
        items.forEachIndexed { position, item ->
            if (item is ThumbnailSection || position == 0) {
                section++
                starts[section] = position
                headed[section] = item is ThumbnailSection
                headersAhead[section] = headers
                if (item is ThumbnailSection) {
                    headers++
                }
            }

            if (item !is ThumbnailSection) {
                sizes[section]++
            }
        }
    }

    fun isHeaded(section: Int) = headed[section]

    /** How many media the section holds. */
    fun size(section: Int) = sizes[section]

    fun headerPosition(section: Int) = starts[section]

    fun firstMedium(section: Int) = starts[section] + if (headed[section]) 1 else 0

    fun headersBefore(section: Int) = headersAhead[section]

    /** The headers ahead of the section's media, its own included. */
    fun headersThrough(section: Int) = headersAhead[section] + if (headed[section]) 1 else 0

    /** The section holding [position], a header counting as its section's. */
    fun sectionOf(position: Int) = lastAtOrBefore(position) { starts[it] }

    /** A medium's place among the media alone, which is the same in any list built from them. */
    fun ordinalOf(section: Int, position: Int) = position - headersThrough(section)

    fun positionOfOrdinal(ordinal: Int): Int {
        // an empty section starts at the same ordinal as the one after it, and the search takes the later
        val section = lastAtOrBefore(ordinal) { starts[it] - headersAhead[it] }
        return ordinal + headersThrough(section)
    }

    private inline fun lastAtOrBefore(value: Int, keyOf: (Int) -> Int): Int {
        var low = 0
        var high = count - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (keyOf(middle) <= value) {
                low = middle
            } else {
                high = middle - 1
            }
        }

        return low
    }
}

/**
 * A grid's spans at one column count: where each one's cell starts across, and where in it the tile
 * sits, the way GridLayoutManager lays them out and [GridSpacingItemDecoration] spaces them.
 */
class GridSpans(val columns: Int, private val shape: GridShape, grouped: Boolean) {
    private val borders = spanBorders(columns, shape.acrossSpace)
    private val tileOffsets = IntArray(columns)
    private val tileLengths = IntArray(columns)

    /** The longest tile among the first n spans, which is how long a row of n tiles is. */
    private val longest = IntArray(columns + 1)

    init {
        val insets = TileInsets(columns, shape.spacing, shape.horizontal, shape.sideSpacing)
        for (span in 0 until columns) {
            insets.compute(span, span, grouped)
            tileOffsets[span] = insets.acrossBefore
            tileLengths[span] = spanLength(span) - insets.acrossBefore - insets.acrossAfter
            longest[span + 1] = maxOf(longest[span], tileLengths[span])
        }
    }

    /** How long a row is along whose tiles fill its first [tiles] spans, spacing aside. */
    fun longestOf(tiles: Int) = longest[tiles]

    /** Where a span's cell starts across, the grid's padding included. */
    fun cellStart(span: Int) = shape.acrossPadding + if (shape.rtl) {
        borders[columns - span - 1]
    } else {
        borders[span]
    }

    fun tileStart(span: Int) = cellStart(span) + tileOffsets[span]

    /** A tile's size, which it is both ways. */
    fun tileLength(span: Int) = tileLengths[span]

    /** The span whose cell holds [across], or -1 outside the grid. */
    fun spanAt(across: Float): Int {
        val offset = across - shape.acrossPadding
        if (offset < 0 || offset >= shape.acrossSpace) {
            return -1
        }

        // the last border at or before the offset
        var low = 0
        var high = columns - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (borders[middle] <= offset) {
                low = middle
            } else {
                high = middle - 1
            }
        }

        return if (shape.rtl) columns - low - 1 else low
    }

    private fun spanLength(span: Int) = if (shape.rtl) {
        borders[columns - span] - borders[columns - span - 1]
    } else {
        borders[span + 1] - borders[span]
    }

    companion object {
        /**
         * Where GridLayoutManager puts the borders between [spans] spans sharing [space] pixels: every
         * span gets the same whole share, and the pixels left over go one each to spans spread evenly
         * through the row. A copy of its `calculateItemBorders`, which is not open to call.
         */
        fun spanBorders(spans: Int, space: Int): IntArray {
            val borders = IntArray(spans + 1)
            val perSpan = space / spans
            val remainder = space % spans
            var consumed = 0
            var additional = 0
            for (i in 1..spans) {
                var size = perSpan
                additional += remainder
                if (additional > 0 && spans - additional < remainder) {
                    size++
                    additional -= spans
                }

                consumed += size
                borders[i] = consumed
            }

            return borders
        }
    }
}

/**
 * The media grid at one column count, worked out rather than laid out - where every tile and header
 * would be at a count the grid is not showing. A pinch draws the grid between two counts out of
 * this; see [ZoomScene].
 *
 * It has to agree with GridLayoutManager and [GridSpacingItemDecoration] to the pixel, or the drawn
 * grid jumps where the real one takes over: the spans share their spare pixels out the way the
 * layout manager does, a row is as long as its longest tile, and a header has a row to itself.
 *
 * "Along" is the way the grid scrolls and "across" the way its spans divide. A place along is kept
 * as two parts, the rows ahead of it and the headers ahead of it, because a pinch scales rows and
 * leaves headers the length they are.
 */
class GridZoomLayout(val columns: Int, val shape: GridShape, val sections: GridSections) {
    val spans = GridSpans(columns, shape, sections.isGrouped)

    // a section's first row is spaced apart from the rest (see GridSpacingItemDecoration), so it
    // keeps insets of its own; every later row of every section shares one pair
    private val firstRowBefore = IntArray(sections.count)
    private val firstRowAfter = IntArray(sections.count)
    private val restBefore: Int
    private val restAfter: Int
    private val rowStarts = IntArray(sections.count)

    /** A full row past the first of its section, from one row's start to the next. */
    val rowPitch: Int

    /** The average span, from one column's start to the next. */
    val pitch = shape.acrossSpace.toFloat() / columns

    /** Every row of every section end to end, the headers left out. */
    val rowsLength: Int

    val headerCount = if (sections.count == 0) 0 else sections.headersThrough(sections.count - 1)

    init {
        val insets = TileInsets(columns, shape.spacing, shape.horizontal, shape.sideSpacing)
        // any tile past a section's first row sits past the first span count of the list as well
        insets.compute(columns, columns, sections.isGrouped)
        restBefore = insets.alongBefore
        restAfter = insets.alongAfter
        rowPitch = spans.longestOf(columns) + restBefore + restAfter

        var start = 0
        for (section in 0 until sections.count) {
            val first = sections.firstMedium(section)
            insets.compute(if (sections.isGrouped) 0 else first, first, sections.isGrouped)
            firstRowBefore[section] = insets.alongBefore
            firstRowAfter[section] = insets.alongAfter
            rowStarts[section] = start
            start += sectionRowsLength(section)
        }

        rowsLength = start
    }

    fun rows(section: Int) = (sections.size(section) + columns - 1) / columns

    fun tilesInRow(section: Int, row: Int) = minOf(columns, sections.size(section) - row * columns)

    /** Where the section's first row starts, in rows alone. */
    fun sectionRowStart(section: Int) = rowStarts[section]

    /** Where a row starts, in rows alone, from its section's first row. */
    fun rowOffset(section: Int, row: Int) = if (row == 0) 0 else rowLength(section, 0) + (row - 1) * rowPitch

    fun rowLength(section: Int, row: Int) = spans.longestOf(tilesInRow(section, row)) + if (row == 0) {
        firstRowBefore[section] + firstRowAfter[section]
    } else {
        restBefore + restAfter
    }

    /** How far into its row a tile starts - the spacing the decoration puts ahead of it. */
    fun tileInset(section: Int, row: Int) = if (row == 0) firstRowBefore[section] else restBefore

    fun sectionRowsLength(section: Int): Int {
        val rows = rows(section)
        return if (rows == 0) 0 else rowOffset(section, rows - 1) + rowLength(section, rows - 1)
    }

    /** The row holding [offset], counted in rows from the section's first row, and held to the section. */
    fun rowAt(section: Int, offset: Double): Int {
        val rows = rows(section)
        val first = rowLength(section, 0)
        val row = if (offset < first) 0 else 1 + ((offset - first) / rowPitch).toInt()
        return row.coerceIn(0, maxOf(0, rows - 1))
    }

    /** The list position at a row and span, or -1 past the last tile of a short row. */
    fun positionAt(section: Int, row: Int, span: Int): Int {
        val index = row * columns + span
        return if (span in 0 until columns && index < sections.size(section)) {
            sections.firstMedium(section) + index
        } else {
            -1
        }
    }
}
