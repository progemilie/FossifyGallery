package org.fossify.gallery.helpers

import android.graphics.Bitmap
import android.graphics.Matrix
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

class RotateTransformation(var degrees: Int, private val flipHorizontally: Boolean = false) : BitmapTransformation() {
    override fun updateDiskCacheKey(messageDigest: MessageDigest) {}

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val matrix = Matrix()
        // Exif composes its mirrored orientations as "rotate, then mirror", so undoing them
        // requires mirroring first and rotating afterwards - the two do not commute
        if (flipHorizontally) {
            matrix.postScale(-1f, 1f)
        }

        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(toTransform, 0, 0, toTransform.width, toTransform.height, matrix, true)
    }
}
