package org.fossify.gallery.helpers

import org.w3c.dom.Document
import org.w3c.dom.Element

private const val DC_NS = "http://purl.org/dc/elements/1.1/"
private const val DC_PREFIX = "dc"
private const val DESCRIPTION = "description"

/** What XMP calls the entry of a language alternative that no particular language asked for. */
private const val X_DEFAULT = "x-default"

/**
 * The caption a photo carries about itself, as `dc:description` in the file's XMP packet - the same
 * property Aves, Lightroom, digiKam and Windows read and write. See [editXmp] for the plumbing.
 *
 * The property is a *language alternative*: a set of translations of one caption, of which
 * `x-default` is the one to show when no language was asked for. That is the form written here.
 * Simpler writers put the text straight into the element, or into an attribute, so all three forms
 * are read - only the one is produced.
 */
object XmpDescription {
    /** The description [xmp] carries, empty when it carries none. */
    fun read(xmp: String?): String {
        val document = parseXmp(xmpBody(xmp) ?: return "") ?: return ""
        return document.rdfDescriptions().firstNotNullOfOrNull { it.readDescription() }.orEmpty()
    }

    /**
     * [xmp] with its description set to [description], an empty one clearing it, or null when the
     * packet should be dropped altogether.
     */
    fun apply(xmp: String?, description: String): String? =
        editXmp(xmp, writesAValue = description.isNotEmpty()) { it.writeDescription(description) }
}

private fun Element.readDescription(): String? {
    getAttributeNS(DC_NS, DESCRIPTION).trim().takeIf { it.isNotEmpty() }?.let { return it }

    val property = elementsOf(getElementsByTagNameNS(DC_NS, DESCRIPTION)).firstOrNull() ?: return null
    val alternatives = elementsOf(property.getElementsByTagNameNS(RDF_NS, "li"))
    val preferred = alternatives.firstOrNull { it.getAttributeNS(XML_NS, "lang") == X_DEFAULT }
        ?: alternatives.firstOrNull()

    return (preferred ?: property).textContent?.trim()?.takeIf { it.isNotEmpty() }
}

private fun Document.writeDescription(description: String) {
    val descriptions = rdfDescriptions()
    // it may sit in any of the descriptions and in any of the three forms, so every one of them
    // goes before the new value is written
    descriptions.forEach { it.removeProperty(DC_NS, DESCRIPTION) }
    if (description.isEmpty()) {
        return
    }

    // a packet with no description at all has nowhere to hold a property, so give it one
    val target = descriptions.firstOrNull() ?: appendRdfDescription() ?: return
    target.bindNamespace(DC_PREFIX, DC_NS)

    val alternative = createElementNS(RDF_NS, "rdf:li").apply {
        setAttributeNS(XML_NS, "xml:lang", X_DEFAULT)
        textContent = description
    }

    val alternatives = createElementNS(RDF_NS, "rdf:Alt").apply { appendChild(alternative) }
    val property = createElementNS(DC_NS, "$DC_PREFIX:$DESCRIPTION").apply { appendChild(alternatives) }
    target.appendChild(property)
}
