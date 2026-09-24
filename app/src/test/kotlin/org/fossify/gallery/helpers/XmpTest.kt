package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The XMP edits a rating and a description make to a file, and what they leave alone. */
class XmpTest {
    private fun packet(vararg properties: String, attributes: String = "") =
        "<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>" +
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"$RDF_NS\">" +
            "<rdf:Description rdf:about=\"\" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\" " +
            "xmlns:MicrosoftPhoto=\"http://ns.microsoft.com/photo/1.0/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" $attributes>" +
            properties.joinToString("") +
            "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>"

    private val title = "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">Café</rdf:li></rdf:Alt></dc:title>"

    @Test
    fun `rating is read from an attribute or an element`() {
        assertEquals(4, XmpRating.read(packet(attributes = "xmp:Rating=\"4\"")))
        assertEquals(3, XmpRating.read(packet("<xmp:Rating>3</xmp:Rating>")))
        assertEquals(0, XmpRating.read(packet(title)))
        assertEquals(0, XmpRating.read(null))
    }

    @Test
    fun `a microsoft percentage reads as stars`() {
        assertEquals(1, XmpRating.read(packet(attributes = "MicrosoftPhoto:Rating=\"1\"")))
        assertEquals(4, XmpRating.read(packet(attributes = "MicrosoftPhoto:Rating=\"75\"")))
        assertEquals(5, XmpRating.read(packet(attributes = "MicrosoftPhoto:Rating=\"99\"")))
    }

    @Test
    fun `a rating written into no packet reads back`() {
        val written = XmpRating.apply(null, 3)
        assertNotNull(written)
        assertEquals(3, XmpRating.read(written))
    }

    @Test
    fun `re-applying a rating keeps it`() {
        val xmp = packet(attributes = "xmp:Rating=\"2\"")
        assertEquals(2, XmpRating.read(XmpRating.apply(xmp, 2)))
        assertNull(XmpRating.apply(null, 0))
    }

    @Test
    fun `clearing the only rating drops the packet`() {
        assertNull(XmpRating.apply(packet("<xmp:Rating>5</xmp:Rating>"), 0))
    }

    @Test
    fun `clearing a rating keeps everything else, non ascii included`() {
        val cleared = XmpRating.apply(packet(title, attributes = "xmp:Rating=\"5\""), 0)
        assertNotNull(cleared)
        assertEquals(0, XmpRating.read(cleared))
        assertTrue("the packet should stay ASCII", cleared!!.all { it.code < 128 })
        assertEquals("Café", parseXmp(xmpBody(cleared)!!)!!.getElementsByTagNameNS(DC, "title").item(0).textContent)
    }

    @Test
    fun `the microsoft rating is kept in step but never introduced`() {
        val kept = XmpRating.apply(packet(attributes = "MicrosoftPhoto:Rating=\"1\""), 4)!!
        assertTrue(kept.contains("MicrosoftPhoto:Rating=\"75\""))

        val fresh = XmpRating.apply(packet(title), 4)!!
        assertFalse(fresh.contains("MicrosoftPhoto:Rating"))
    }

    @Test
    fun `a description round trips and clears`() {
        val written = XmpDescription.apply(packet(title), "Line one\nZürich")
        assertEquals("Line one\nZürich", XmpDescription.read(written))

        val cleared = XmpDescription.apply(written, "")
        assertEquals("", XmpDescription.read(cleared))
        assertNotNull("the title should keep the packet alive", cleared)
    }

    @Test
    fun `a description is read from an attribute too`() {
        assertEquals("Hello", XmpDescription.read(packet(attributes = "dc:description=\"Hello\"")))
    }

    private companion object {
        const val DC = "http://purl.org/dc/elements/1.1/"
    }
}
