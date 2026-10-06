package org.fossify.gallery.helpers

import androidx.exifinterface.media.ExifInterface
import org.fossify.gallery.extensions.getXmpPacket
import java.util.Locale

/** How much wider than tall a photo has to be to count as a panorama. Tall ones never do: screenshots are tall. */
private const val PANORAMA_ASPECT = 2.5f

private const val QUARTER_TURN = 90
private const val THREE_QUARTER_TURN = 270

// product names that already say who made them, which the maker would only be repeated in front of
private val SELF_NAMED_MODELS = listOf("Pixel", "iPhone", "iPad", "Galaxy", "Nexus")

/**
 * Reads what [TraitIndex] keeps of one photo out of its metadata, in the one open of the file:
 * - the device, from the EXIF make and model;
 * - a selfie, where the EXIF lens name says it was the front camera - which Pixels and iPhones
 *   write, and some phones, Samsung's among them, do not;
 * - a panorama, for a photo at least [PANORAMA_ASPECT] times wider than tall, or one whose XMP says
 *   it is a photo sphere. One named as a panorama is caught by its name where the filter is matched.
 */
internal object TraitReader {
    private val nothing = PhotoTraits(device = "", isSelfie = false, isPanorama = false)

    /** Blocking, call it off the main thread. A file that cannot be read is a photo that says nothing. */
    fun read(path: String): PhotoTraits = try {
        val exif = ExifInterface(path)
        PhotoTraits(
            device = deviceName(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL)),
            isSelfie = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.contains("front", ignoreCase = true) == true,
            isPanorama = exif.isWide() || exif.getXmpPacket()?.contains("GPano:") == true,
        )
    } catch (ignored: Exception) {
        nothing
    } catch (ignored: OutOfMemoryError) {
        nothing
    }

    /**
     * What a camera is called on its pill: the model as the camera writes it, with the maker put in
     * front where the model does not say it already - "Galaxy S25 Ultra", "Canon EOS R6",
     * "Sony ILCE-7M3". A maker written in capitals or in lower case is evened out.
     */
    fun deviceName(make: String?, model: String?): String {
        val product = model?.trim().orEmpty()
        if (product.isEmpty()) {
            return ""
        }

        val maker = make?.trim()?.substringBefore(' ').orEmpty()
        val saysWhoMadeIt = maker.isEmpty() ||
            product.contains(maker, ignoreCase = true) ||
            SELF_NAMED_MODELS.any { product.startsWith(it, ignoreCase = true) }

        return if (saysWhoMadeIt) product else "${maker.evenedOut()} $product"
    }

    private fun String.evenedOut() = if (this == uppercase() || this == lowercase()) {
        lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }
    } else {
        this
    }

    private fun ExifInterface.isWide(): Boolean {
        val width = getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
        val height = getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
        val isTurned = rotationDegrees == QUARTER_TURN || rotationDegrees == THREE_QUARTER_TURN
        val across = if (isTurned) height else width
        val down = if (isTurned) width else height
        return down > 0 && across >= down * PANORAMA_ASPECT
    }
}
