package org.fossify.gallery.extensions

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.bumptech.glide.request.transition.TransitionFactory
import com.bumptech.glide.signature.ObjectKey
import org.fossify.gallery.helpers.ROUNDED_CORNERS_NONE

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
