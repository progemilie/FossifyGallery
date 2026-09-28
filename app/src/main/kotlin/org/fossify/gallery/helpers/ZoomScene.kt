package org.fossify.gallery.helpers

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** How far through a step the pictures start changing over, and how far they are done by. */
private const val FADE_FROM = 0.3f
private const val FADE_TO = 0.7f

/**
 * Choosing the anchor from a row next to the fingers' instead costs this much of a column, so a
 * neighbouring row is only taken when it spares the grid real sideways travel.
 */
private const val NEIGHBOUR_ROW_COST = 0.35f

/** How much bigger or smaller the grid can be pulled past either end of the ladder. */
private const val STRETCH_MAX = 0.08f

/** How quickly the stretch gives out, per step of pinching past the end. */
private const val STRETCH_RATE = 2.5f

/** The smoothstep curve's own constant: 3t² - 2t³, flat at either end. */
private const val SMOOTHSTEP = 3f

private fun lerp(from: Double, to: Double, progress: Float) = from + (to - from) * progress

/**
 * One column count's layout as it is drawn at a moment of a zoom: scaled, and moved to line up with
 * the other count drawn beside it. Rows scale while headers keep their length, as they do in either
 * grid, so a header rides along with the rows around it without ever growing.
 *
 * Coordinates are the grid's own, turned so that "along" runs the way it scrolls - see [ZoomScene].
 * Where the content starts along is kept to double precision: well down a large library it lies
 * millions of pixels above the screen, where a float only holds whole pixels or worse, and every
 * place on screen is worked out from it.
 */
class ZoomLayer(val rung: Int, val layout: GridZoomLayout) {
    /** Where the content's start is drawn along. */
    var originAlong = 0.0
    var scaleAlong = 1f

    /** Where the layout's zero across is drawn, the grid's padding being part of the layout. */
    var originAcross = 0f
    var scaleAcross = 1f

    private val headerLength = layout.shape.headerLength

    fun place(originAlong: Double, scaleAlong: Float, originAcross: Float, scaleAcross: Float) {
        this.originAlong = originAlong
        this.scaleAlong = scaleAlong
        this.originAcross = originAcross
        this.scaleAcross = scaleAcross
    }

    fun headerStart(section: Int) =
        alongOf(layout.sectionRowStart(section), layout.sections.headersBefore(section)).toFloat()

    fun rowStart(section: Int, row: Int) = alongOf(
        layout.sectionRowStart(section) + layout.rowOffset(section, row),
        layout.sections.headersThrough(section)
    ).toFloat()

    fun tileAlong(section: Int, row: Int) = rowStart(section, row) + scaleAlong * layout.tileInset(section, row)

    fun tileAcross(span: Int) = originAcross + scaleAcross * layout.spans.tileStart(span)

    fun tileSize(span: Int) = scaleAcross * layout.spans.tileLength(span)

    /** Where the first and last spans' cells start and end across. */
    val spansStart get() = originAcross + scaleAcross * layout.shape.acrossPadding
    val spansEnd get() = spansStart + scaleAcross * layout.shape.acrossSpace

    /** The section drawn at [along], or the first when that is ahead of them all. */
    fun sectionAt(along: Float): Int {
        var low = 0
        var high = layout.sections.count - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (alongOf(layout.sectionRowStart(middle), layout.sections.headersBefore(middle)) <= along) {
                low = middle
            } else {
                high = middle - 1
            }
        }

        return low
    }

    fun rowAt(section: Int, along: Float) = layout.rowAt(
        section,
        (along - alongOf(layout.sectionRowStart(section), layout.sections.headersThrough(section))) / scaleAlong
    )

    /** Where a place [rows] into the rows and [headers] headers on is drawn along. */
    private fun alongOf(rows: Int, headers: Int) =
        originAlong + scaleAlong.toDouble() * rows + headerLength.toDouble() * headers

    fun spanAt(across: Float) = layout.spans.spanAt((across - originAcross) / scaleAcross)
}

/**
 * Every header and row drawn between [from] and [to] along, in order. Rows are handed over
 * rather than tiles, so that anything asked once a row - where the other layer's is - is asked
 * once a row.
 */
inline fun ZoomLayer.forEachVisible(
    from: Float,
    to: Float,
    onHeader: (section: Int, start: Float) -> Unit,
    onRow: (section: Int, row: Int, start: Float) -> Unit
) {
    val sections = layout.sections
    var section = sectionAt(from)
    while (section < sections.count) {
        val headerStart = headerStart(section)
        if (headerStart >= to) {
            break
        }

        if (sections.isHeaded(section) && headerStart + layout.shape.headerLength > from) {
            onHeader(section, headerStart)
        }

        val rows = layout.rows(section)
        var row = if (rows == 0) 0 else rowAt(section, from)
        while (row < rows) {
            val start = rowStart(section, row)
            if (start >= to) {
                return
            }

            if (start + scaleAlong * layout.rowLength(section, row) > from) {
                onRow(section, row, start)
            }

            row++
        }

        section++
    }
}

/**
 * Where the grid is drawn at any zoom level between its column counts: what a pinch shows while the
 * fingers are down, and what it settles through once they lift. See MediaGridZoom.
 *
 * A level is a rung of the ladder and a fraction - 3.4 is four tenths of the way from the fourth
 * count to the fifth. In between, both counts are drawn, each scaled so that their tiles are one
 * size, and lined up so that their tiles land on the same cells. A tile then never travels from one
 * row to another: the row the anchor sits in keeps its tiles, every other row only changes pictures,
 * and the columns one count has and the other has not come in from, or go out to, the sides.
 *
 * All of it is worked in the grid's own coordinates turned so that "along" is the way it scrolls,
 * which a grid laid out right to left and sideways runs from its right edge.
 */
class ZoomScene(
    private val rungCount: Int,
    private val viewport: Viewport,
    private val layoutOf: (rung: Int) -> GridZoomLayout,
    startRung: Int,
    startOrigin: Double,
) {
    /** The grid's size along and across, and its padding along - what a scroll position is held to. */
    class Viewport(val alongLength: Float, val paddingStart: Float, val paddingEnd: Float)

    /** The last whole count reached, and where its content started along. */
    var restRung = startRung
        private set
    var restOrigin = startOrigin
        private set

    /** Whatever the fingers are around, along and across - see [focusOn]. */
    var focusAlong = 0f
        private set
    var focusAcross = 0f
        private set

    /** How far past either end of the ladder the count at rest is being pulled. */
    private var overshoot = 0f

    /** The count with fewer columns, always drawn; the other only between two counts. */
    lateinit var under: ZoomLayer
        private set
    var over: ZoomLayer? = null
        private set

    /** How far [over]'s pictures have come in over [under]'s. */
    var fade = 0f
        private set

    private val layers = HashMap<Int, ZoomLayer>()
    private var step: Step? = null

    init {
        update(startRung.toFloat())
    }

    fun layerOf(rung: Int) = layers.getOrPut(rung) { ZoomLayer(rung, layoutOf(rung)) }

    /**
     * Zooms about [along], [across] from here on. A step under way keeps to the point it began with,
     * and so does a stretch past either end, which is drawn about it and would jump.
     */
    fun focusOn(along: Float, across: Float) {
        if (overshoot == 0f) {
            focusAlong = along
            focusAcross = across
        }
    }

    fun update(level: Float) {
        val clamped = level.coerceIn(0f, (rungCount - 1).toFloat())
        overshoot = level - clamped
        while (true) {
            if (clamped == restRung.toFloat()) {
                step = null
                showRest()
                return
            }

            val target = if (clamped > restRung) restRung + 1 else restRung - 1
            val current = step?.takeIf { it.to == target } ?: Step(restRung, target).also { step = it }
            val progress = abs(clamped - restRung)
            if (progress < 1f) {
                current.apply(progress)
                val toIsOver = target > restRung
                under = if (toIsOver) current.fromLayer else current.toLayer
                over = if (toIsOver) current.toLayer else current.fromLayer
                fade = fadeAt(if (toIsOver) progress else 1 - progress)
                return
            }

            restOrigin = current.endOrigin()
            restRung = target
            step = null
        }
    }

    /** One count drawn as it lies, pulled a little bigger or smaller by any pinching past the ends. */
    private fun showRest() {
        val layer = layerOf(restRung)
        val stretch = STRETCH_MAX * (1 - exp(-abs(overshoot) * STRETCH_RATE))
        val scale = if (overshoot < 0) 1 + stretch else 1 / (1 + stretch)
        layer.place(
            originAlong = focusAlong + (restOrigin - focusAlong) * scale.toDouble(),
            scaleAlong = scale,
            originAcross = focusAcross * (1 - scale),
            scaleAcross = scale
        )

        under = layer
        over = null
        fade = 0f
    }

    private fun fadeAt(progress: Float): Float {
        val t = ((progress - FADE_FROM) / (FADE_TO - FADE_FROM)).coerceIn(0f, 1f)
        return t * t * (SMOOTHSTEP - 2 * t)
    }

    /**
     * The zoom between two neighbouring counts, set out from the one it was entered at. Everything
     * turns on the anchor, a tile the two layouts are lined up by: drawn in the same cell in both,
     * it holds still but for the zoom itself, and so do the tiles beside it in its row.
     */
    private inner class Step(val from: Int, val to: Int) {
        val fromLayer = layerOf(from)
        val toLayer = layerOf(to)
        private val fromLayout = fromLayer.layout
        private val toLayout = toLayer.layout
        private val headerLength = fromLayout.shape.headerLength

        // the anchor's row start (rows alone, plus the headers ahead of it) and cell start across
        private var fromRow = 0.0
        private var fromHeaders = 0
        private var fromAcross = 0f
        private var toRow = 0.0
        private var toHeaders = 0
        private var toAcross = 0f

        /** How far across the anchor has to drift beyond the zoom to land on its column in [to]... */
        private var acrossDrift = 0f

        /**
         * ...and how far along, to land where [to] can lie: nowhere near the fingers, when a list that
         * lost its headers is much shorter above the anchor than it was. Both are spread evenly over
         * the step, rather than left until the list's end forces them in the step's last few frames.
         */
        private var alongDrift = 0.0

        // held from the moment the step began, so that it is drawn the same way back and forth
        private val focusAlong = this@ZoomScene.focusAlong
        private val focusAcross = this@ZoomScene.focusAcross
        private val restOrigin = this@ZoomScene.restOrigin

        init {
            // it may be part way through another step, and the anchor is looked for as it lies
            fromLayer.place(restOrigin, 1f, 0f, 1f)
            chooseAnchor()
            val restAnchor = restOrigin + fromRow + headerLength.toDouble() * fromHeaders
            val endScale = toLayout.rowPitch.toDouble() / fromLayout.rowPitch
            val zoomedEnd = focusAlong + (restAnchor - focusAlong) * endScale
            val highEnd = highest(1f, toRow, toHeaders)
            val lowEnd = minOf(lowest(toLayout, 1f, toRow, toHeaders), highEnd)
            alongDrift = zoomedEnd.coerceIn(lowEnd, highEnd) - zoomedEnd
        }

        fun apply(progress: Float) {
            val across = fromLayout.pitch * (toLayout.pitch / fromLayout.pitch).pow(progress)
            val along = fromLayout.rowPitch * (toLayout.rowPitch.toFloat() / fromLayout.rowPitch).pow(progress)
            val fromScaleAcross = across / fromLayout.pitch
            val fromScaleAlong = along / fromLayout.rowPitch
            val toScaleAcross = across / toLayout.pitch
            val toScaleAlong = along / toLayout.rowPitch

            val anchorAcross = focusAcross + (fromAcross - focusAcross) * fromScaleAcross + acrossDrift * progress
            val restAnchor = restOrigin + fromRow + headerLength.toDouble() * fromHeaders
            val zoomed = focusAlong + (restAnchor - focusAlong) * fromScaleAlong + alongDrift * progress
            // held to where each count could lie, weighed by how far the step has come
            val fromHigh = highest(fromScaleAlong, fromRow, fromHeaders)
            val toHigh = highest(toScaleAlong, toRow, toHeaders)
            val fromLow = minOf(lowest(fromLayout, fromScaleAlong, fromRow, fromHeaders), fromHigh)
            val toLow = minOf(lowest(toLayout, toScaleAlong, toRow, toHeaders), toHigh)
            val anchorAlong = zoomed.coerceIn(lerp(fromLow, toLow, progress), lerp(fromHigh, toHigh, progress))

            fromLayer.place(
                originAlong = anchorAlong - fromScaleAlong * fromRow - headerLength.toDouble() * fromHeaders,
                scaleAlong = fromScaleAlong,
                originAcross = anchorAcross - fromScaleAcross * fromAcross,
                scaleAcross = fromScaleAcross
            )

            toLayer.place(
                originAlong = anchorAlong - toScaleAlong * toRow - headerLength.toDouble() * toHeaders,
                scaleAlong = toScaleAlong,
                originAcross = anchorAcross - toScaleAcross * toAcross,
                scaleAcross = toScaleAcross
            )
        }

        /** Where [to]'s content starts once the step is complete, which is where it comes to rest. */
        fun endOrigin(): Double {
            apply(1f)
            return toLayer.originAlong
        }

        /** The anchor as close to the fingers as it can be while being led least far astray across. */
        private fun chooseAnchor() {
            val sections = fromLayout.sections
            var section = fromLayer.sectionAt(focusAlong)
            // a header or an empty section has no tile to anchor to: take the next that has one
            while (section < sections.count - 1 && sections.size(section) == 0) {
                section++
            }

            while (section > 0 && sections.size(section) == 0) {
                section--
            }

            val focusRow = fromLayer.rowAt(section, focusAlong)
            var bestCost = Float.MAX_VALUE
            for (row in focusRow - 1..focusRow + 1) {
                if (row < 0 || row >= fromLayout.rows(section)) {
                    continue
                }

                val span = nearestSpan(section, row)
                val position = fromLayout.positionAt(section, row, span)
                val cost = driftFor(section, span, position) +
                    NEIGHBOUR_ROW_COST * toLayout.pitch * abs(row - focusRow)
                if (cost < bestCost) {
                    bestCost = cost
                    adopt(section, row, span, position)
                }
            }
        }

        /** The span nearest the fingers across that has a tile in this row. */
        private fun nearestSpan(section: Int, row: Int): Int {
            val tiles = fromLayout.tilesInRow(section, row)
            val hit = fromLayer.spanAt(focusAcross)
            if (hit in 0 until tiles) {
                return hit
            }

            // off the end of a short row, or off the grid altogether: whichever end of the row is nearer
            val first = fromLayer.tileAcross(0)
            val last = fromLayer.tileAcross(tiles - 1)
            return if (abs(focusAcross - first) <= abs(focusAcross - last)) 0 else tiles - 1
        }

        /** How far across the anchor at [position] would have to drift. */
        private fun driftFor(section: Int, span: Int, position: Int): Float {
            val (_, _, toSpan) = locateInTo(fromLayout.sections.ordinalOf(section, position))
            return abs(toLayout.spans.cellStart(toSpan) - zoomedAcross(fromLayout.spans.cellStart(span).toFloat()))
        }

        private fun adopt(section: Int, row: Int, span: Int, position: Int) {
            val (toSection, toRowIndex, toSpan) = locateInTo(fromLayout.sections.ordinalOf(section, position))
            fromRow = (fromLayout.sectionRowStart(section) + fromLayout.rowOffset(section, row)).toDouble()
            fromHeaders = fromLayout.sections.headersThrough(section)
            fromAcross = fromLayout.spans.cellStart(span).toFloat()
            toRow = (toLayout.sectionRowStart(toSection) + toLayout.rowOffset(toSection, toRowIndex)).toDouble()
            toHeaders = toLayout.sections.headersThrough(toSection)
            toAcross = toLayout.spans.cellStart(toSpan).toFloat()
            acrossDrift = toAcross - zoomedAcross(fromAcross)
        }

        /** Where a point across ends up once zoomed from [from]'s pitch to [to]'s about the fingers. */
        private fun zoomedAcross(across: Float) =
            focusAcross + (across - focusAcross) * (toLayout.pitch / fromLayout.pitch)

        /** Section, row and span of the medium at [ordinal] in [to]'s layout. */
        private fun locateInTo(ordinal: Int): Triple<Int, Int, Int> {
            val sections = toLayout.sections
            val position = sections.positionOfOrdinal(ordinal)
            val section = sections.sectionOf(position)
            val index = position - sections.firstMedium(section)
            return Triple(section, index / toLayout.columns, index % toLayout.columns)
        }

        /** Where the anchor may be drawn along at the latest, for the content to reach the far end. */
        private fun lowest(layout: GridZoomLayout, scale: Float, row: Double, headers: Int): Double {
            val content = scale.toDouble() * layout.rowsLength + headerLength.toDouble() * layout.headerCount
            val anchor = scale * row + headerLength.toDouble() * headers
            return viewport.alongLength - viewport.paddingEnd - content + anchor
        }

        /** ...and at the earliest, for the content not to come away from the near end. */
        private fun highest(scale: Float, row: Double, headers: Int) =
            viewport.paddingStart + scale * row + headerLength.toDouble() * headers
    }
}
