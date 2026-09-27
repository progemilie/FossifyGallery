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

    private fun scene(items: List<ThumbnailItem>, startRung: Int, startOrigin: Float): ZoomScene {
        val sections = GridSections(items)
        return ZoomScene(ladder.size, viewport, { GridZoomLayout(ladder[it], shape, sections) }, startRung, startOrigin)
    }

    @Test
    fun `a whole level is its count drawn as it lies`() {
        val scene = scene(media(300), startRung = 3, startOrigin = -1000f)
        assertEquals(3, scene.under.rung)
        assertNull(scene.over)
        assertEquals(-1000f, scene.under.originAlong, 0f)
        assertEquals(1f, scene.under.scaleAlong, 0f)
        assertEquals(0f, scene.under.originAcross, 0f)
    }

    @Test
    fun `between two counts the fewer columns are drawn under the more`() {
        val scene = scene(media(300), startRung = 3, startOrigin = -1000f)
        scene.focusAlong = 1200f
        scene.focusAcross = 540f
        scene.update(3.5f)
        assertEquals(3, scene.under.rung)
        assertEquals(4, scene.over?.rung)
        scene.update(2.5f)
        assertEquals(2, scene.under.rung)
        assertEquals(3, scene.over?.rung)
    }

    @Test
    fun `both counts draw their tiles on the same cells`() {
        val scene = scene(media(300), startRung = 3, startOrigin = -1000f)
        scene.focusAlong = 1200f
        scene.focusAcross = 540f
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
        val scene = scene(media(300), startRung = 3, startOrigin = -1000f)
        scene.focusAlong = 1200f
        scene.focusAcross = 300f
        scene.update(3.999f)
        val nearly = requireNotNull(scene.over).originAlong
        scene.update(4f)
        assertEquals(4, scene.under.rung)
        assertEquals(nearly, scene.under.originAlong, 2f)
        assertEquals(0f, scene.under.originAcross, 0.5f)
    }

    @Test
    fun `pinching back to where it began puts the grid back`() {
        val scene = scene(media(300), startRung = 5, startOrigin = -2345f)
        scene.focusAlong = 900f
        scene.focusAcross = 700f
        scene.update(5.6f)
        scene.update(5.2f)
        scene.update(5f)
        assertEquals(5, scene.under.rung)
        assertEquals(-2345f, scene.under.originAlong, 0.01f)
    }

    @Test
    fun `zooming out at the top of the list keeps it at the top`() {
        val scene = scene(media(300), startRung = 2, startOrigin = viewport.paddingStart)
        scene.focusAlong = 1500f
        scene.focusAcross = 540f
        for (level in 1..20) {
            scene.update(2 + level / 10f)
            assertTrue(scene.under.originAlong <= viewport.paddingStart + 0.01f)
        }

        assertEquals(4, scene.under.rung)
        assertEquals(viewport.paddingStart, scene.under.originAlong, 0.01f)
    }

    @Test
    fun `a short list stays at the top whatever the count`() {
        val scene = scene(media(5), startRung = 3, startOrigin = viewport.paddingStart)
        scene.focusAlong = 400f
        scene.focusAcross = 540f
        scene.update(1f)
        assertEquals(1, scene.under.rung)
        assertEquals(viewport.paddingStart, scene.under.originAlong, 0.01f)
    }

    @Test
    fun `headers keep their length while the rows around them scale`() {
        val items = listOf<ThumbnailItem>(ThumbnailSection("a")) + media(9) + ThumbnailSection("b") + media(40)
        val scene = scene(items, startRung = 3, startOrigin = viewport.paddingStart)
        scene.focusAlong = 1800f
        scene.focusAcross = 540f
        scene.update(3.5f)
        val layer = scene.under
        val headerEnd = layer.headerStart(1) + shape.headerLength
        assertEquals(headerEnd, layer.rowStart(1, 0), 0.01f)
        // the rows ahead of the second header scaled, so it lies nearer the first than it did
        assertTrue(layer.headerStart(1) - layer.headerStart(0) < shape.headerLength + 3 * 270f)
    }

    private fun onLattice(distance: Float, pitch: Float): Boolean {
        val pitches = distance / pitch
        return abs(pitches - pitches.roundToLong()) < 0.01f
    }
}
