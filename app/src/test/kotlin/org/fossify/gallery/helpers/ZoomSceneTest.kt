package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToLong

/** What a pinch draws between two column counts, and where it leaves the grid. */
class ZoomSceneTest {

    private val ladder = listOf(1, 2, 3, 4, 5, 6, 7, 10, 14, 20)
    private val viewport = ZoomScene.Viewport(alongLength = 2400f, paddingStart = 200f, paddingEnd = 150f)
    private val shape = GridShape(1080, 0, 1, false, horizontal = false, rtl = false, headerLength = 120)

    private fun media(count: Int) = List(count) { Medium().apply { path = "/m$it" } }

    private fun scene(items: List<ThumbnailItem>, startRung: Int, startOrigin: Double): ZoomScene {
        val sections = GridSections(items)
        return ZoomScene(ladder.size, viewport, { GridZoomLayout(ladder[it], shape, sections) }, startRung, startOrigin)
    }

    @Test
    fun `a whole level is its count drawn as it lies`() {
        val scene = scene(media(300), startRung = 3, startOrigin = -1000.0)
        assertEquals(3, scene.under.rung)
        assertNull(scene.over)
        assertEquals(-1000.0, scene.under.originAlong, 0.0)
        assertEquals(1f, scene.under.scaleAlong, 0f)
        assertEquals(0f, scene.under.originAcross, 0f)
    }

    @Test
    fun `between two counts the fewer columns are drawn under the more`() {
        val scene = scene(media(300), startRung = 3, startOrigin = -1000.0)
        scene.focusOn(1200f, 540f)
        scene.update(3.5f)
        assertEquals(3, scene.under.rung)
        assertEquals(4, scene.over?.rung)
        scene.update(2.5f)
        assertEquals(2, scene.under.rung)
        assertEquals(3, scene.over?.rung)
    }

    @Test
    fun `both counts draw their tiles on the same cells`() {
        val scene = scene(media(300), startRung = 3, startOrigin = -1000.0)
        scene.focusOn(1200f, 540f)
        scene.update(3.4f)
        val under = scene.under
        val over = requireNotNull(scene.over)

        // one tile's pitch, the same in both once scaled
        val pitch = under.scaleAlong * under.layout.rowPitch
        assertEquals(pitch, over.scaleAlong * over.layout.rowPitch, 0.01f)
        assertEquals(under.scaleAcross * under.layout.pitch, over.scaleAcross * over.layout.pitch, 0.01f)
        // rows and columns of one fall a whole number of pitches from the other's
        assertTrue(onLattice(over.rowStart(0, 0) - under.rowStart(0, 0), pitch))
        assertTrue(onLattice(over.tileAcross(0) - under.tileAcross(0), under.scaleAcross * under.layout.pitch))
    }

    @Test
    fun `a step is drawn continuously through the count at its end`() {
        val scene = scene(media(300), startRung = 3, startOrigin = -1000.0)
        scene.focusOn(1200f, 300f)
        scene.update(3.999f)
        val nearly = requireNotNull(scene.over).originAlong
        scene.update(4f)
        assertEquals(4, scene.under.rung)
        assertEquals(nearly, scene.under.originAlong, 2.0)
        assertEquals(0f, scene.under.originAcross, 0.5f)
    }

    @Test
    fun `pinching back to where it began puts the grid back`() {
        val scene = scene(media(300), startRung = 5, startOrigin = -2345.0)
        scene.focusOn(900f, 700f)
        scene.update(5.6f)
        scene.update(5.2f)
        scene.update(5f)
        assertEquals(5, scene.under.rung)
        assertEquals(-2345.0, scene.under.originAlong, 0.01)
    }

    @Test
    fun `fingers coming down elsewhere mid stretch leave it where it is drawn`() {
        val scene = scene(media(300), startRung = 0, startOrigin = -1000.0)
        scene.focusOn(1200f, 540f)
        scene.update(-0.5f)
        val stretched = scene.under.originAlong
        scene.focusOn(300f, 100f)
        scene.update(-0.5f)
        assertEquals(stretched, scene.under.originAlong, 0.0)
    }

    @Test
    fun `zooming out at the top of the list keeps it at the top`() {
        val scene = scene(media(300), startRung = 2, startOrigin = viewport.paddingStart.toDouble())
        scene.focusOn(1500f, 540f)
        for (level in 1..20) {
            scene.update(2 + level / 10f)
            assertTrue(scene.under.originAlong <= viewport.paddingStart + 0.01)
        }

        assertEquals(4, scene.under.rung)
        assertEquals(viewport.paddingStart.toDouble(), scene.under.originAlong, 0.01)
    }

    @Test
    fun `a short list stays at the top whatever the count`() {
        val scene = scene(media(5), startRung = 3, startOrigin = viewport.paddingStart.toDouble())
        scene.focusOn(400f, 540f)
        scene.update(1f)
        assertEquals(1, scene.under.rung)
        assertEquals(viewport.paddingStart.toDouble(), scene.under.originAlong, 0.01)
    }

    @Test
    fun `headers keep their length while the rows around them scale`() {
        val items = listOf<ThumbnailItem>(ThumbnailSection("a")) + media(9) + ThumbnailSection("b") + media(40)
        val scene = scene(items, startRung = 3, startOrigin = viewport.paddingStart.toDouble())
        scene.focusOn(1800f, 540f)
        scene.update(3.5f)
        val layer = scene.under
        val headerEnd = layer.headerStart(1) + shape.headerLength
        assertEquals(headerEnd, layer.rowStart(1, 0), 0.01f)
        // the rows ahead of the second header scaled, so it lies nearer the first than it did
        assertTrue(layer.headerStart(1) - layer.headerStart(0) < shape.headerLength + 3 * 270f)
    }

    @Test
    fun `a list losing its headers moves into place across the whole step`() {
        // a hundred days of one photo each, under a header each, which the simplified counts drop
        val items = (0 until 100).flatMap { day ->
            listOf(ThumbnailSection("day $day"), Medium().apply { path = "/m$day" })
        }

        val grouped = GridSections(items)
        val flat = GridSections(items.filterIsInstance<Medium>())
        val scene = ZoomScene(ladder.size, viewport, {
            GridZoomLayout(ladder[it], shape, if (ladder[it] > 7) flat else grouped)
        }, 6, -8000.0)
        scene.focusOn(1200f, 540f)

        // the photo under the fingers is far down with its headers and near the top without them,
        // so the grid has a long way to move - a little in every frame, never all at the end
        var previous = Double.NaN
        for (percent in 1..100) {
            scene.update(6 + percent / 100f)
            val origin = scene.layerOf(7).originAlong
            if (!previous.isNaN()) {
                assertTrue("jumped ${origin - previous} at $percent%", abs(origin - previous) < 60)
            }

            previous = origin
        }

        assertEquals(viewport.paddingStart.toDouble(), scene.under.originAlong, 0.01)
    }

    private fun onLattice(distance: Float, pitch: Float): Boolean {
        val pitches = distance / pitch
        return abs(pitches - pitches.roundToLong()) < 0.01f
    }
}
