package org.fossify.gallery.helpers

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.roundToInt

private const val XMP_NS = "http://ns.adobe.com/xap/1.0/"
private const val MS_PHOTO_NS = "http://ns.microsoft.com/photo/1.0/"
private const val RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
private const val XMLNS_NS = "http://www.w3.org/2000/xmlns/"
private const val XMP_PREFIX = "xmp"
private const val MS_PHOTO_PREFIX = "MicrosoftPhoto"
private const val RATING = "Rating"
private const val DESCRIPTION = "Description"
private const val ABOUT = "about"

// 1/25/50/75/99% are what the five stars correspond to in Microsoft's scale
private const val PERCENT_PER_STAR = 25f
private const val ONE_STAR_PERCENT = 1
private const val TWO_STAR_PERCENT = 25
private const val THREE_STAR_PERCENT = 50
private const val FOUR_STAR_PERCENT = 75
private const val FIVE_STAR_PERCENT = 99

private const val PACKET_HEADER = "<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
private const val PACKET_TRAILER = "<?xpacket end=\"w\"?>"

private const val EMPTY_PACKET =
    "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">" +
        "<rdf:RDF xmlns:rdf=\"$RDF_NS\">" +
        "<rdf:Description rdf:about=\"\"/>" +
        "</rdf:RDF>" +
        "</x:xmpmeta>"

private val msPhotoPercents = listOf(
    ONE_STAR_PERCENT, TWO_STAR_PERCENT, THREE_STAR_PERCENT, FOUR_STAR_PERCENT, FIVE_STAR_PERCENT
)

private val xpacketRegex = Regex("<\\?xpacket.*?\\?>", RegexOption.DOT_MATCHES_ALL)
private val xmpAttributeRegex = Regex("\\b$XMP_PREFIX:$RATING\\s*=\\s*\"([^\"]*)\"")
private val xmpElementRegex = Regex("<$XMP_PREFIX:$RATING[^>]*>([^<]*)</$XMP_PREFIX:$RATING>")
private val msAttributeRegex = Regex("\\b$MS_PHOTO_PREFIX:$RATING\\s*=\\s*\"([^\"]*)\"")
private val msElementRegex =
    Regex("<$MS_PHOTO_PREFIX:$RATING[^>]*>([^<]*)</$MS_PHOTO_PREFIX:$RATING>")

/**
 * The star rating of a photo, as it lives in the file's XMP packet.
 *
 * XMP rather than an Exif tag because that is where every other tool looks: Aves, Lightroom,
 * digiKam and Windows all read `xmp:Rating`, and androidx's ExifInterface has no constant for the
 * Windows-only Exif Rating tag (0x4746) at all, so it could not write one even if we wanted it to.
 *
 * Two properties are involved:
 * - `xmp:Rating` (0-5) is the one we own - written when a rating is set, removed when it is cleared.
 * - `MicrosoftPhoto:Rating` is a percentage kept only in step: updated when the file already
 *   carries it, never introduced. Writing it into files that never had it would be adding a second,
 *   redundant source of truth for other apps to disagree with.
 *
 * Everything this produces is pure ASCII - any non-ASCII content of the original packet comes back
 * out as numeric character references. ExifInterface hands XMP over as a String, and going through
 * a String is only lossless if the bytes are ASCII to begin with.
 *
 * The XML plumbing below the object is file-private on purpose: nothing outside this file has any
 * business editing an XMP packet by hand.
 */
object XmpRating {
    const val MAX_RATING = 5

    /**
     * The rating [xmp] carries, 0 when it carries none. Deliberately a handful of regexes rather
     * than an XML parse: this runs once per file on a media scan, where building a DOM per photo
     * would be the expensive part of the scan.
     */
    fun read(xmp: String?): Int {
        if (xmp.isNullOrBlank()) {
            return 0
        }

        firstNumber(xmp, xmpAttributeRegex, xmpElementRegex)?.let {
            return it.coerceIn(0, MAX_RATING)
        }

        firstNumber(xmp, msAttributeRegex, msElementRegex)?.let {
            // 0 is not part of the percentage scale, it is the absence of a rating
            return if (it <= 0) {
                0
            } else {
                ((it / PERCENT_PER_STAR).roundToInt() + 1).coerceIn(0, MAX_RATING)
            }
        }

        return 0
    }

    /**
     * [xmp] with its rating set to [rating], or null when the packet should be dropped altogether -
     * either because clearing the rating left nothing else in it, or because there was no packet to
     * begin with and a rating of 0 is not worth creating one for.
     *
     * Returns [xmp] unchanged when it already says what it should, which is how callers know not to
     * rewrite the file at all.
     */
    fun apply(xmp: String?, rating: Int): String? {
        val body = xmp?.let { xpacketRegex.replace(it, "") }?.trim()?.takeIf { it.isNotEmpty() }
        if (body == null && rating <= 0) {
            return null
        }

        val document = parse(body ?: EMPTY_PACKET) ?: return xmp
        document.writeRating(rating)
        if (document.rdfDescriptions().all { it.saysNothing() }) {
            return null
        }

        return serialize(document)?.let { "$PACKET_HEADER$it$PACKET_TRAILER" } ?: xmp
    }
}

private fun firstNumber(xmp: String, vararg patterns: Regex): Int? {
    patterns.forEach { pattern ->
        pattern.find(xmp)?.groupValues?.get(1)?.trim()?.toIntOrNull()?.let { return it }
    }
    return null
}

private fun Document.writeRating(rating: Int) {
    val descriptions = rdfDescriptions()
    // an existing rating may sit in any of the descriptions, and may be written either as an
    // attribute or as a child element - clear every form of it before writing the new one
    val hadMsRating = descriptions.any {
        it.hasAttributeNS(MS_PHOTO_NS, RATING) ||
            it.getElementsByTagNameNS(MS_PHOTO_NS, RATING).length > 0
    }

    descriptions.forEach {
        it.removeRating(XMP_NS)
        it.removeRating(MS_PHOTO_NS)
    }

    if (rating <= 0) {
        return
    }

    // a packet with no description at all has nowhere to hold a property, so give it one
    val target = descriptions.firstOrNull() ?: appendRdfDescription() ?: return
    target.setRating(XMP_NS, XMP_PREFIX, rating.toString())
    if (hadMsRating) {
        val percent = msPhotoPercents[(rating - 1).coerceIn(0, msPhotoPercents.lastIndex)]
        target.setRating(MS_PHOTO_NS, MS_PHOTO_PREFIX, percent.toString())
    }
}

private fun Document.rdfDescriptions() = elementsOf(getElementsByTagNameNS(RDF_NS, DESCRIPTION))

private fun Document.appendRdfDescription(): Element? {
    val rdf = elementsOf(getElementsByTagNameNS(RDF_NS, "RDF")).firstOrNull() ?: return null
    val description = createElementNS(RDF_NS, "rdf:$DESCRIPTION")
    description.setAttributeNS(RDF_NS, "rdf:$ABOUT", "")
    rdf.appendChild(description)
    return description
}

// NodeLists are live, so anything that goes on to detach nodes has to hold a copy, not the list
private fun elementsOf(nodes: NodeList): List<Element> {
    val elements = ArrayList<Element>(nodes.length)
    for (i in 0 until nodes.length) {
        (nodes.item(i) as? Element)?.let { elements.add(it) }
    }
    return elements
}

private fun Element.removeRating(namespace: String) {
    if (hasAttributeNS(namespace, RATING)) {
        removeAttributeNS(namespace, RATING)
    }

    elementsOf(getElementsByTagNameNS(namespace, RATING)).forEach {
        it.parentNode?.removeChild(it)
    }
}

private fun Element.setRating(namespace: String, prefix: String, value: String) {
    // the serializer is not guaranteed to invent the prefix binding for us, and a packet whose
    // xmlns:xmp is missing reads as having no rating at all
    if (getAttributeNS(XMLNS_NS, prefix) != namespace) {
        setAttributeNS(XMLNS_NS, "xmlns:$prefix", namespace)
    }
    setAttributeNS(namespace, "$prefix:$RATING", value)
}

/**
 * Whether the description says nothing beyond "this is a description of the file" - only namespace
 * bindings and an empty rdf:about. Such a leftover is what clearing the last real property leaves
 * behind, and it is not worth keeping a packet alive for.
 */
private fun Element.saysNothing(): Boolean {
    if (elementsOf(childNodes).isNotEmpty()) {
        return false
    }

    for (i in 0 until attributes.length) {
        val attribute = attributes.item(i)
        val isNamespace = attribute.namespaceURI == XMLNS_NS || attribute.nodeName == "xmlns"
        val isAbout = attribute.namespaceURI == RDF_NS && attribute.localName == ABOUT
        if (!isNamespace && !isAbout) {
            return false
        }
    }

    return true
}

private fun parse(xml: String): Document? {
    return try {
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    } catch (ignored: Exception) {
        null
    }
}

private fun serialize(document: Document): String? {
    return try {
        val output = ByteArrayOutputStream()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            // writing to a stream in ASCII is what turns anything outside it into a numeric
            // character reference, which a Writer would not do
            setOutputProperty(OutputKeys.ENCODING, "US-ASCII")
        }.transform(DOMSource(document), StreamResult(output))
        output.toString(Charsets.US_ASCII.name())
    } catch (ignored: Exception) {
        null
    }
}
