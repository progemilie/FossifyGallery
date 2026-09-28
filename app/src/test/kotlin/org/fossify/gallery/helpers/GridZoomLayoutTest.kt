package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the grid's worked-out layout puts tiles and headers, which has to be where the grid does. */
class GridZoomLayoutTest {

    private fun media(count: Int) = List(count) { Medium().apply { path = "/m$it" } }

    /** Sections of the given sizes, each under a header. */
    private fun grouped(vararg sizes: Int): List<ThumbnailItem> = sizes.withIndex().flatMap { (index, size) ->
        listOf<ThumbnailItem>(ThumbnailSection("section $index")) + media(size)
    }

    private fun shape(
        across: Int = 1080,
        spacing: Int = 1,
        sideSpacing: Boolean = false,
        rtl: Boolean = false,
        header: Int = 100,
    ) = GridShape(across, 0, spacing, sideSpacing, horizontal = false, rtl = rtl, headerLength = header)

    @Test
    fun `span borders share the spare pixels out as the layout manager does`() {
        assertArrayEquals(intArrayOf(0, 270, 540, 809, 1079), GridSpans.spanBorders(4, 1079))
        assertArrayEquals(intArrayOf(0, 154, 308, 463, 617, 771, 925, 1080), GridSpans.spanBorders(7, 1080))
        assertArrayEquals(intArrayOf(0, 360, 720, 1080), GridSpans.spanBorders(3, 1080))
    }

    @Test
    fun `a row is as long as its longest tile`() {
        val layout = GridZoomLayout(7, shape(), GridSections.of(media(10)))
        // spans are 154 or 155 wide, and the tiles as long as they are wide
        assertEquals(155, layout.rowLength(0, 0))
        assertEquals(155, layout.rowPitch)
        // the short second row holds spans 0-2, all 154 but the third
        assertEquals(155, layout.rowLength(0, 1))
        assertEquals(2, layout.rows(0))
        assertEquals(310, layout.rowsLength)
    }

    @Test
    fun `sections start on a fresh row under their header`() {
        val layout = GridZoomLayout(4, shape(), GridSections.of(grouped(5, 3)))
        assertEquals(2, layout.rows(0))
        assertEquals(1, layout.rows(1))
        assertEquals(0, layout.sectionRowStart(0))
        assertEquals(540, layout.sectionRowStart(1))
        assertEquals(810, layout.rowsLength)
        assertEquals(2, layout.headerCount)
        // position 0 is the first header; 6 the second, 7 the first medium under it
        assertEquals(7, layout.positionAt(1, 0, 0))
        assertEquals(-1, layout.positionAt(1, 0, 3))
    }

    @Test
    fun `wide spacing insets the first row less than the rest`() {
        val layout = GridZoomLayout(3, shape(spacing = 12), GridSections.of(grouped(7)))
        // spans of 360: the middle tile loses 4 either side, the outer ones 8 on their inner side
        assertEquals(352, layout.spans.tileLength(0))
        assertEquals(352, layout.spans.tileLength(1))
        assertEquals(352, layout.spans.tileLength(2))
        assertEquals(0, layout.tileInset(0, 0))
        assertEquals(12, layout.tileInset(0, 1))
        assertEquals(352, layout.rowLength(0, 0))
        assertEquals(364, layout.rowPitch)
    }

    @Test
    fun `right to left spans start from the right`() {
        val layout = GridZoomLayout(4, shape(across = 1079, rtl = true), GridSections.of(media(4)))
        assertEquals(809, layout.spans.cellStart(0))
        assertEquals(0, layout.spans.cellStart(3))
        assertEquals(0, layout.spans.spanAt(1000f))
        assertEquals(3, layout.spans.spanAt(10f))
    }

    @Test
    fun `a medium keeps its ordinal whichever list it is found in`() {
        // header, three media, two headers, four media
        val items = grouped(3, 0, 4)
        val sections = GridSections.of(items)
        assertEquals(3, sections.count)
        // the empty middle section shares its start with the one after it
        val position = sections.positionOfOrdinal(3)
        assertEquals(6, position)
        val flat = GridSections.of(items.filterIsInstance<Medium>())
        assertEquals(1, flat.count)
        assertEquals(3, flat.positionOfOrdinal(3))
        assertEquals(3, sections.ordinalOf(sections.sectionOf(position), position))
    }

    @Test
    fun `a list read without its headers is the list filtered`() {
        val items = grouped(3, 0, 4)
        val sections = GridSections.of(items)
        val filtered = items.filterIsInstance<Medium>()
        assertEquals(filtered, MediaWithoutHeaders(items, sections))

        val flat = GridSections.of(filtered)
        val derived = GridSections.headerless(sections.mediaCount)
        assertEquals(flat.isGrouped, derived.isGrouped)
        assertEquals(flat.count, derived.count)
        assertEquals(flat.size(0), derived.size(0))
        assertEquals(flat.firstMedium(0), derived.firstMedium(0))
        assertEquals(flat.headersThrough(0), derived.headersThrough(0))
    }

    @Test
    fun `rows are found by their offset and held to the section`() {
        val layout = GridZoomLayout(4, shape(), GridSections.of(media(10)))
        assertEquals(0, layout.rowAt(0, -50.0))
        assertEquals(1, layout.rowAt(0, 300.0))
        assertEquals(2, layout.rowAt(0, 5000.0))
    }
}
