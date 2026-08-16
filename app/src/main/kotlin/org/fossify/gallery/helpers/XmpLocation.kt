package org.fossify.gallery.helpers

import org.w3c.dom.Element

private const val EXIF_NS = "http://ns.adobe.com/exif/1.0/"
private const val GPS_PREFIX = "GPS"

/**
 * Where a photo was taken, as the `exif:GPS*` properties of an XMP packet - the copy of the Exif
 * location that editors write alongside the original. See [editXmp] for the plumbing.
 *
 * Only removal is offered: this app never writes a location, it only takes one off. The properties
 * are matched by prefix rather than listed, because the set keeps growing (bearing, speed, track,
 * horizontal error) and one missed name would leave the coordinates in a file someone asked to have
 * them out of.
 */
internal object XmpLocation {
    /** Whether [xmp] says anything about where the file was made. */
    fun isPresent(xmp: String?): Boolean {
        val document = parseXmp(xmpBody(xmp) ?: return false) ?: return false
        return document.rdfDescriptions().any { it.gpsPropertyNames().isNotEmpty() }
    }

    /** [xmp] with every GPS property gone, or null when that leaves nothing worth keeping. */
    fun remove(xmp: String?): String? = editXmp(xmp, writesAValue = false) { document ->
        document.rdfDescriptions().forEach { description ->
            description.gpsPropertyNames().forEach { description.removeProperty(EXIF_NS, it) }
        }
    }
}

/**
 * The local names of the GPS properties on this description, in either of the forms XMP allows them
 * to be written in - an attribute of the description, or a child element of it.
 */
private fun Element.gpsPropertyNames(): Set<String> {
    val names = mutableSetOf<String>()
    for (i in 0 until attributes.length) {
        val attribute = attributes.item(i)
        if (attribute.namespaceURI == EXIF_NS && attribute.localName.orEmpty().startsWith(GPS_PREFIX)) {
            names.add(attribute.localName)
        }
    }

    elementsOf(getElementsByTagNameNS(EXIF_NS, "*")).forEach { child ->
        if (child.localName.orEmpty().startsWith(GPS_PREFIX)) {
            names.add(child.localName)
        }
    }

    return names
}
