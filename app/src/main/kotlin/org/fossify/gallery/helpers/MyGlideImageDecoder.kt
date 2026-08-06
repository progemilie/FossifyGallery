package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.davemorrissey.labs.subscaleview.ImageDecoder

class MyGlideImageDecoder(
    val degrees: Int,
    val signature: ObjectKey,
    private val isFlipped: Boolean = false
) : ImageDecoder {

    override fun decode(context: Context, uri: Uri): Bitmap {
        val options = RequestOptions()
            .format(DecodeFormat.PREFER_ARGB_8888)
            .signature(signature)
            .fitCenter()

        val builder = Glide.with(context)
            .asBitmap()
            .load(uri.toString().substringAfter("file://"))
            .apply(options)
            // Glide has already baked the full Exif transform into the bitmap, but the
            // SubsamplingScaleImageView applies its own orientation (and mirroring) on top, so undo
            // both here to hand it the raw image - matching what the region decoder produces
            .transform(RotateTransformation(-degrees, isFlipped))
            .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)

        return builder.get()
    }
}
