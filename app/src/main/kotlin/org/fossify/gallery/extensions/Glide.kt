package org.fossify.gallery.extensions

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.bumptech.glide.request.transition.TransitionFactory
import com.bumptech.glide.signature.ObjectKey
import org.fossify.gallery.helpers.THUMBNAIL_FADE_DURATION_MS
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
 * Loads one cell of a folder group's collage. Corners are cut by the collage clipping itself, so
 * no rounding transform is asked for here, and the cell is never bigger than a quarter tile - so
 * it takes the same embedded copy the folder's own thumbnail would have. See [ThumbnailSource].
 */
fun Context.loadFolderGroupCell(path: String, target: ImageView, signature: ObjectKey) {
    val options = RequestOptions()
        .signature(signature)
        .priority(Priority.LOW)
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        .format(DecodeFormat.PREFER_RGB_565)
        .transform(CenterCrop())
        .dontAnimate()
        .decode(Bitmap::class.java)

    Glide.with(applicationContext)
        .load(ThumbnailSource(path))
        .apply(options)
        .transition(getOptionalCrossFadeTransition(THUMBNAIL_FADE_DURATION_MS))
        .into(target)
}

