package org.fossify.gallery.helpers

import org.fossify.gallery.models.MediaKind
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.SearchFilter
import org.fossify.gallery.models.SizeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val KB = 1024L
private const val MB = 1024L * KB

/** What the search offers to narrow a grid by, and what a filter lets through. */
class SearchOptionsTest {

    private fun medium(path: String, type: Int = TYPE_IMAGES, size: Long = 2 * MB, isFavorite: Boolean = false) =
        Medium(
            null, path.substringAfterLast('/'), path, path.substringBeforeLast('/'),
            0L, 0L, size, type, 0, isFavorite, 0L, 0L
        )

    private fun facts(traits: Map<String, PhotoTraits> = emptyMap(), storeSizes: Map<String, Long> = emptyMap()) =
        MediaFacts({ TraitLookup { traits[it] } }) { storeSizes }

    @Test
    fun `a kind is offered only where it would narrow the grid`() {
        val media = listOf(
            medium("/dcim/a.jpg"),
            medium("/dcim/b.mp4", type = TYPE_VIDEOS),
            medium("/dcim/c.gif", type = TYPE_GIFS),
        )

        assertEquals(listOf(MediaKind.VIDEOS, MediaKind.GIFS), searchOptionsOf(media, facts()).kinds)
    }

    @Test
    fun `a kind everything already is changes nothing and is left out`() {
        val media = listOf(medium("/movies/a.mp4", type = TYPE_VIDEOS), medium("/movies/b.mp4", type = TYPE_VIDEOS))
        assertTrue(searchOptionsOf(media, facts()).kinds.isEmpty())
    }

    @Test
    fun `devices come most photos first, and one that took everything is left out`() {
        val traits = mapOf(
            "/dcim/a.jpg" to PhotoTraits("Pixel 8", isSelfie = false, isPanorama = false),
            "/dcim/b.jpg" to PhotoTraits("Galaxy S25 Ultra", isSelfie = false, isPanorama = false),
            "/dcim/c.jpg" to PhotoTraits("Galaxy S25 Ultra", isSelfie = false, isPanorama = false),
        )

        val media = traits.keys.map { medium(it) } + medium("/dcim/d.png")
        assertEquals(listOf("Galaxy S25 Ultra", "Pixel 8"), searchOptionsOf(media, facts(traits)).devices)

        val oneCamera = traits.keys.drop(1).map { medium(it) }
        assertTrue(searchOptionsOf(oneCamera, facts(traits)).devices.isEmpty())
    }

    @Test
    fun `a size a scan left at 0 is looked up before the file is`() {
        val media = listOf(medium("/dcim/a.jpg", size = 0L), medium("/dcim/b.jpg", size = 50 * MB))
        val options = searchOptionsOf(media, facts(storeSizes = mapOf("/dcim/a.jpg" to 512 * KB)))
        assertEquals(listOf(SizeRange.UNDER_1_MB, SizeRange.FROM_10_TO_100_MB), options.sizes)
    }

    @Test
    fun `size brackets meet without overlapping`() {
        assertTrue(MB - 1 in SizeRange.UNDER_1_MB)
        assertTrue(MB in SizeRange.FROM_1_TO_10_MB)
        assertFalse(MB in SizeRange.UNDER_1_MB)
        assertTrue(100 * MB in SizeRange.OVER_100_MB)
        assertFalse(100 * MB in SizeRange.FROM_10_TO_100_MB)
    }

    @Test
    fun `a filter lets through what it names and nothing else`() {
        val traits = mapOf("/dcim/selfie.jpg" to PhotoTraits("Pixel 8", isSelfie = true, isPanorama = false))
        val selfie = medium("/dcim/selfie.jpg")
        val other = medium("/dcim/other.jpg")

        assertTrue(SearchFilter.Kind(MediaKind.SELFIES).matches(selfie, facts(traits)))
        assertFalse(SearchFilter.Kind(MediaKind.SELFIES).matches(other, facts(traits)))
        assertTrue(SearchFilter.Device("Pixel 8").matches(selfie, facts(traits)))
        assertTrue(SearchFilter.Kind(MediaKind.FAVOURITES).matches(medium("/a.jpg", isFavorite = true), facts()))
        assertTrue(SearchFilter.Size(SizeRange.FROM_1_TO_10_MB).matches(other, facts()))
    }

    @Test
    fun `a panorama is caught by the name the camera gave it as well as by what was read`() {
        assertTrue(SearchFilter.Kind(MediaKind.PANORAMAS).matches(medium("/dcim/PANO_20260101_120000.jpg"), facts()))
        assertTrue("PXL_20260101_120000000.PANO.jpg".isPanoramaName())
        assertTrue("PXL_20260101_120000000.PHOTOSPHERE.jpg".isPanoramaName())
        assertFalse("panorama_of_the_bay.jpg".isPanoramaName())
    }

    @Test
    fun `screenshots and screen recordings are told by their folder or their name`() {
        assertTrue("/storage/emulated/0/Pictures/Screenshots/Screenshot_20260101-120000.png".isScreenshotPath())
        assertTrue("/storage/emulated/0/DCIM/Screen recordings/20260101_120000.mp4".isScreenshotPath())
        assertTrue("/storage/emulated/0/DCIM/ScreenRecorder/Screenrecorder-2026-01-01.mp4".isScreenshotPath())
        assertTrue("/storage/emulated/0/Movies/screen-20260101-120000.mp4".isScreenshotPath())
        assertFalse("/storage/emulated/0/DCIM/Camera/20260101_120000.jpg".isScreenshotPath())
        assertFalse("/storage/emulated/0/Pictures/Screensavers/beach.jpg".isScreenshotPath())
    }

    @Test
    fun `a filter tells a screenshot by its folder or its name`() {
        val screenshots = SearchFilter.Kind(MediaKind.SCREENSHOTS)
        assertTrue(screenshots.matches(medium("/storage/emulated/0/Pictures/Screenshots/a.png"), facts()))
        assertTrue(screenshots.matches(medium("/storage/emulated/0/Movies/screen-20260101-120000.mp4"), facts()))
        assertFalse(screenshots.matches(medium("/storage/emulated/0/Pictures/Screensavers/beach.jpg"), facts()))
    }

    @Test
    fun `facts read nothing until they are asked, so they can be made on the main thread`() {
        var reads = 0
        val facts = MediaFacts({ reads++; TraitLookup.NONE }) { reads++; emptyMap() }
        facts.sizeOf(medium("/dcim/a.jpg"))
        assertEquals(0, reads)

        facts.traitsOf(medium("/dcim/a.jpg"))
        facts.sizeOf(medium("/dcim/b.jpg", size = 0L))
        assertEquals(2, reads)
    }

    @Test
    fun `a camera is named by its model, with the maker put in front only where the model lacks it`() {
        assertEquals("Galaxy S25 Ultra", TraitReader.deviceName("samsung", "Galaxy S25 Ultra"))
        assertEquals("Pixel 8", TraitReader.deviceName("Google", "Pixel 8"))
        assertEquals("Canon EOS R6", TraitReader.deviceName("Canon", "Canon EOS R6"))
        assertEquals("NIKON D750", TraitReader.deviceName("NIKON CORPORATION", "NIKON D750"))
        assertEquals("Sony ILCE-7M3", TraitReader.deviceName("SONY", "ILCE-7M3"))
        assertEquals("OnePlus CPH2581", TraitReader.deviceName("OnePlus", "CPH2581"))
        assertEquals("X100V", TraitReader.deviceName(null, " X100V "))
        assertEquals("", TraitReader.deviceName("Canon", null))
    }
}
