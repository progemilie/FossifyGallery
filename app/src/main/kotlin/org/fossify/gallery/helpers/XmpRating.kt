package org.fossify.gallery.helpers

import org.w3c.dom.Document
import org.w3c.dom.Element
import kotlin.math.roundToInt

private const val XMP_NS = "http://ns.adobe.com/xap/1.0/"
private const val MS_PHOTO_NS = "http://ns.microsoft.com/photo/1.0/"
private const val XMP_PREFIX = "xmp"
private const val MS_PHOTO_PREFIX = "MicrosoftPhoto"
private const val RATING = "Rating"

// 1/25/50/75/99% are what the five stars correspond to in Microsoft's scale
private const val PERCENT_PER_STAR = 25f
private const val ONE_STAR_PERCENT = 1
private const val TWO_STAR_PERCENT = 25
private const val THREE_STAR_PERCENT = 50
private const val FOUR_STAR_PERCENT = 75
private const val FIVE_STAR_PERCENT = 99

private val msPhotoPercents = listOf(
    ONE_STAR_PERCENT, TWO_STAR_PERCENT, THREE_STAR_PERCENT, FOUR_STAR_PERCENT, FIVE_STAR_PERCENT
)

private val xmpAttributeRegex = Regex("\\b$XMP_PREFIX:$RATING\\s*=\\s*\"([^\"]*)\"")
private val xmpElementRegex = Regex("<$XMP_PREFIX:$RATING[^>]*>([^<]*)</$XMP_PREFIX:$RATING>")
private val msAttributeRegex = Regex("\\b$MS_PHOTO_PREFIX:$RATING\\s*=\\s*\"([^\"]*)\"")
private val msElementRegex =
    Regex("<$MS_PHOTO_PREFIX:$RATING[^>]*>([^<]*)</$MS_PHOTO_PREFIX:$RATING>")

/**
 * The star rating of a photo, as it lives in the file's XMP packet. See [editXmp] for the packet
 * plumbing under this.
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
     * [xmp] with its rating set to [rating], or null when the packet should be dropped altogether.
     * Returns [xmp] unchanged when it already says what it should, which is how callers know not to
     * rewrite the file at all.
     */
    fun apply(xmp: String?, rating: Int): String? =
        editXmp(xmp, writesAValue = rating > 0) { it.writeRating(rating) }
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
        it.removeProperty(XMP_NS, RATING)
        it.removeProperty(MS_PHOTO_NS, RATING)
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

private fun Element.setRating(namespace: String, prefix: String, value: String) {
    // a packet whose xmlns:xmp is missing reads as having no rating at all
    bindNamespace(prefix, namespace)
    setAttributeNS(namespace, "$prefix:$RATING", value)
}
