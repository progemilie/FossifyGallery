package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.integration.webp.WebpBitmapFactory
import com.bumptech.glide.integration.webp.decoder.WebpDownsampler
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey

/**
 * Loads the thumbnails of a grid zoomed out past the point of reading one - see [GridZoom].
 *
 * Exists rather than another `loadImage` overload because the request is prepared once and reused:
 * a fling across twenty columns binds ~100 items in one frame, where rebuilding options,
 * transformations and listeners each time costs more than the fetch itself.
 */
class SimpleThumbnailLoader(
    context: Context,
    cropThumbnails: Boolean,
    /** What the tile is decoded to - see `GridZoom.simpleThumbnailSize`. */
    size: Int
) {
    private val requests = Glide.with(context.applicationContext)

    // a placeholder rather than an item background, which would stay under the loaded picture for
    // the item's life - a second screenful of drawing at these counts
    private val placeholder = context.getColor(org.fossify.commons.R.color.md_grey_black).toDrawable()

    private val options = RequestOptions()
        .priority(Priority.LOW)
        // measured: re-decoding a screenful costs more than the cache writes, even from an
        // embedded thumbnail
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        .format(DecodeFormat.PREFER_RGB_565)
        .override(size)
        .dontAnimate()
        // forces the first frame of an animated WebP, which is all a tile this size can show
        .decode(Bitmap::class.java)
        .optionalTransform(if (cropThumbnails) CenterCrop() else FitCenter())
        .placeholder(placeholder)
        .set(WebpDownsampler.USE_SYSTEM_DECODER, false) // CVE-2023-4863

    private val noTransition = DrawableTransitionOptions().dontTransition()

    init {
        WebpBitmapFactory.sUseSystemDecoder = false // CVE-2023-4863
    }

    fun load(path: String, target: ImageView, signature: ObjectKey) {
        requests
            // a tile this small comes out of the photo's embedded copy where there is one
            .load(ThumbnailSource(path))
            .apply(options)
            .signature(signature)
            .transition(noTransition)
            .into(target)
    }

    fun clear(target: View) = requests.clear(target)
}
