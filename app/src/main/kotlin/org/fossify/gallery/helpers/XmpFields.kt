package org.fossify.gallery.helpers

import org.w3c.dom.Element

private const val EXIF_NS = "http://ns.adobe.com/exif/1.0/"
private const val TIFF_NS = "http://ns.adobe.com/tiff/1.0/"
private const val GPS_PREFIX = "GPS"
private const val ORIENTATION = "Orientation"

/**
 * The two Exif fields an XMP packet keeps its own copy of, as far as removing metadata is concerned:
 * where the file was made and which way up it is. See [editXmp] for the plumbing.
 *
 * Only removal is offered - this app never writes either of them, it only takes them off - and both
 * have to go from the packet as well as from the Exif, or a file would still say what it was told
 * not to.
 */
internal object XmpLocation {
    /** Whether [xmp] says anything about where the file was made. */
    fun isPresent(xmp: String?) = isPresentIn(xmp, EXIF_NS, ::isGps)

    /** [xmp] with every GPS property gone, or null when that leaves nothing worth keeping. */
    fun remove(xmp: String?) = removeFrom(xmp, EXIF_NS, ::isGps)

    // matched by prefix rather than listed, because the set keeps growing (bearing, speed, track,
    // horizontal error) and one missed name would leave the coordinates in a file someone asked to
    // have them out of
    private fun isGps(name: String) = name.startsWith(GPS_PREFIX)
}

internal object XmpOrientation {
    fun isPresent(xmp: String?) = isPresentIn(xmp, TIFF_NS) { it == ORIENTATION }

    fun remove(xmp: String?) = removeFrom(xmp, TIFF_NS) { it == ORIENTATION }
}

private fun isPresentIn(xmp: String?, namespace: String, matches: (String) -> Boolean): Boolean {
    val document = parseXmp(xmpBody(xmp) ?: return false) ?: return false
    return document.rdfDescriptions().any { it.propertyNames(namespace, matches).isNotEmpty() }
}

private fun removeFrom(xmp: String?, namespace: String, matches: (String) -> Boolean): String? =
    editXmp(xmp, writesAValue = false) { document ->
        document.rdfDescriptions().forEach { description ->
            description.propertyNames(namespace, matches).forEach {
                description.removeProperty(namespace, it)
            }
        }
    }

/**
 * The local names of the matching properties on this description, in either of the forms XMP allows
 * them to be written in - an attribute of the description, or a child element of it.
 */
private fun Element.propertyNames(namespace: String, matches: (String) -> Boolean): Set<String> {
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
