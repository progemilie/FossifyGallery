package org.fossify.gallery.helpers

import org.w3c.dom.Element

private const val EXIF_NS = "http://ns.adobe.com/exif/1.0/"
private const val TIFF_NS = "http://ns.adobe.com/tiff/1.0/"

/**
 * A group of XMP properties that can be taken off a packet, matched by name within one namespace.
 * See [editXmp] for the plumbing.
 *
 * Only removal is offered - this app never writes either of the two below, it only takes them off.
 */
internal class XmpField(private val namespace: String, private val matches: (String) -> Boolean) {
    /** Whether [xmp] carries any property of this group. */
    fun isPresent(xmp: String?): Boolean {
        val document = parseXmp(xmpBody(xmp) ?: return false) ?: return false
        return document.rdfDescriptions().any { it.propertyNames().isNotEmpty() }
    }

    /** [xmp] with every property of this group gone, or null when that leaves nothing worth keeping. */
    fun remove(xmp: String?): String? = editXmp(xmp, writesAValue = false) { document ->
        document.rdfDescriptions().forEach { description ->
            description.propertyNames().forEach { description.removeProperty(namespace, it) }
        }
    }

    /**
     * The local names of the matching properties on this description, in either of the forms XMP
     * allows them to be written in - an attribute of the description, or a child element of it.
     */
    private fun Element.propertyNames(): Set<String> {
        val names = mutableSetOf<String>()
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i)
            val name = attribute.localName.orEmpty()
            if (attribute.namespaceURI == namespace && matches(name)) {
                names.add(name)
            }
        }

        elementsOf(getElementsByTagNameNS(namespace, "*")).forEach { child ->
            val name = child.localName.orEmpty()
            if (matches(name)) {
                names.add(name)
            }
        }

        return names
    }
}

/**
 * The two Exif fields an XMP packet keeps its own copy of, as far as removing metadata is concerned:
 * where the file was made and which way up it is. Both have to go from the packet as well as from the
 * Exif, or a file would still say what it was told not to.
 *
 * The GPS properties are matched by prefix rather than listed, because the set keeps growing
 * (bearing, speed, track, horizontal error) and one missed name would leave the coordinates in a file
 * someone asked to have them out of.
 */
internal val XmpLocation = XmpField(EXIF_NS) { it.startsWith("GPS") }

internal val XmpOrientation = XmpField(TIFF_NS) { it == "Orientation" }
