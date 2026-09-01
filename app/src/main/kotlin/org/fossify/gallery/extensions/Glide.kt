package org.fossify.gallery.extensions

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.Rotate
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.bumptech.glide.request.transition.TransitionFactory
import com.bumptech.glide.signature.ObjectKey
import org.fossify.gallery.helpers.LOW_RES_IMAGE_SIZE
import org.fossify.gallery.helpers.ROUNDED_CORNERS_NONE
import org.fossify.gallery.helpers.ThumbnailSource

/**
 * Cross fade transition option that disabled fading when loading from cache.
 */
fun getOptionalCrossFadeTransition(duration: Int): DrawableTransitionOptions {
    return DrawableTransitionOptions.with(
        TransitionFactory { dataSource, isFirstResource ->
            if (dataSource == DataSource.RESOURCE_DISK_CACHE) return@TransitionFactory null
            DrawableCrossFadeFactory.Builder(duration).build().build(dataSource, isFirstResource)
        }
    )
}

/**
 * Loads one cell of a folder group's collage. The collage cuts its own corners, so none are asked
 * for here - which is also what keeps the cheaper 565 bitmaps, since a corner mask would convert
 * them straight back.
 */
fun Context.loadFolderGroupCell(path: String, target: ImageView, signature: ObjectKey) {
    loadImageBase(
        path = path,
        target = target,
        cropThumbnails = true,
        roundCorners = ROUNDED_CORNERS_NONE,
        signature = signature,
        decodeFormat = DecodeFormat.PREFER_RGB_565
    )
}

/**
 * The small first pass at a fullscreen photo: the copy the camera left inside the file wherever
 * there is one big enough, which makes it in a few milliseconds against the tens a whole photo
 * costs. See [org.fossify.gallery.helpers.ThumbnailSource].
 *
 * Shared rather than restated because two things ask for this same picture - the viewer paints it
 * while the full photo decodes, and the flight into the viewer is drawn with it - and every part of
 * the request below is cache key. Described differently in the two places, the picture would be
 * decoded twice and the hand-off between them would be a change of picture rather than none at all.
 */
fun Context.lowResPhotoRequest(
    path: String,
    signature: ObjectKey,
    rotationDegrees: Int = 0,
    bypassCache: Boolean = false
): RequestBuilder<Drawable> {
    val options = RequestOptions()
        .signature(signature)
        .override(LOW_RES_IMAGE_SIZE)
        .format(DecodeFormat.PREFER_RGB_565)
        .priority(Priority.IMMEDIATE)
        .fitCenter()
        .run {
            when {
                bypassCache -> diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true)
                rotationDegrees != 0 -> transform(Rotate(rotationDegrees))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)

                else -> diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            }
        }

    return Glide.with(this).load(ThumbnailSource(path)).apply(options)
}
