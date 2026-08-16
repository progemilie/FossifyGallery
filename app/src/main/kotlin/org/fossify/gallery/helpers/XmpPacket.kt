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

/**
 * The XMP packet itself: parsing one, editing the `rdf:Description` elements everything is written
 * into, and serializing it back. What goes *in* a packet is [XmpRating] and [XmpDescription]; this
 * is only the paper it is written on.
 *
 * Everything produced here is pure ASCII - any non-ASCII content comes back out as numeric
 * character references. ExifInterface hands XMP over as a String, and going through a String is
 * only lossless if the bytes are ASCII to begin with.
 *
 * All of it is internal on purpose: nothing outside this package has any business editing a packet
 * by hand.
 */

internal const val RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
internal const val XMLNS_NS = "http://www.w3.org/2000/xmlns/"
internal const val XML_NS = "http://www.w3.org/XML/1998/namespace"

private const val DESCRIPTION = "Description"
private const val ABOUT = "about"

private const val PACKET_HEADER = "<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
private const val PACKET_TRAILER = "<?xpacket end=\"w\"?>"

private const val EMPTY_PACKET =
    "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">" +
        "<rdf:RDF xmlns:rdf=\"$RDF_NS\">" +
        "<rdf:Description rdf:about=\"\"/>" +
        "</rdf:RDF>" +
        "</x:xmpmeta>"

private val xpacketRegex = Regex("<\\?xpacket.*?\\?>", RegexOption.DOT_MATCHES_ALL)

/**
 * [xmp] with [write] applied to it, or null when the packet should be dropped altogether - either
 * because what was written left nothing else in it, or because there was no packet to begin with
 * and [writesAValue] says there is nothing worth creating one for.
 *
 * Returns [xmp] unchanged when it could not be parsed at all, which is how callers know not to
 * rewrite the file over an edit that went nowhere.
 */
internal fun editXmp(xmp: String?, writesAValue: Boolean, write: (Document) -> Unit): String? {
    val body = xmp?.let { xpacketRegex.replace(it, "") }?.trim()?.takeIf { it.isNotEmpty() }
    if (body == null && !writesAValue) {
        return null
    }

    val document = parseXmp(body ?: EMPTY_PACKET) ?: return xmp
    write(document)
    if (document.rdfDescriptions().all { it.saysNothing() }) {
        return null
    }

    return serializeXmp(document)?.let { "$PACKET_HEADER$it$PACKET_TRAILER" } ?: xmp
}

/** [xmp] with its packet wrappers off, or null when there is nothing between them. */
internal fun xmpBody(xmp: String?): String? =
    xmp?.let { xpacketRegex.replace(it, "") }?.trim()?.takeIf { it.isNotEmpty() }

internal fun parseXmp(xml: String): Document? {
    return try {
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    } catch (ignored: Exception) {
        null
    }
}

private fun serializeXmp(document: Document): String? {
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

internal fun Document.rdfDescriptions() = elementsOf(getElementsByTagNameNS(RDF_NS, DESCRIPTION))

internal fun Document.appendRdfDescription(): Element? {
    val rdf = elementsOf(getElementsByTagNameNS(RDF_NS, "RDF")).firstOrNull() ?: return null
    val description = createElementNS(RDF_NS, "rdf:$DESCRIPTION")
    description.setAttributeNS(RDF_NS, "rdf:$ABOUT", "")
    rdf.appendChild(description)
    return description
}

// NodeLists are live, so anything that goes on to detach nodes has to hold a copy, not the list
internal fun elementsOf(nodes: NodeList): List<Element> {
    val elements = ArrayList<Element>(nodes.length)
    for (i in 0 until nodes.length) {
        (nodes.item(i) as? Element)?.let { elements.add(it) }
    }
    return elements
}

/** Binds [prefix] to [namespace] on this element unless it already is; the serializer may not. */
internal fun Element.bindNamespace(prefix: String, namespace: String) {
    if (getAttributeNS(XMLNS_NS, prefix) != namespace) {
        setAttributeNS(XMLNS_NS, "xmlns:$prefix", namespace)
    }
}

/** Detaches every form of [property], attribute and child element alike, in [namespace]. */
internal fun Element.removeProperty(namespace: String, property: String) {
    if (hasAttributeNS(namespace, property)) {
        removeAttributeNS(namespace, property)
    }

    elementsOf(getElementsByTagNameNS(namespace, property)).forEach {
        it.parentNode?.removeChild(it)
    }
}

/**
 * Whether the description says nothing beyond "this is a description of the file" - only namespace
 * bindings and an empty rdf:about. Such a leftover is what clearing the last real property leaves
 * behind, and it is not worth keeping a packet alive for.
 */
internal fun Element.saysNothing(): Boolean {
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
