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
 * Prepared once and reused, which is the whole reason it exists rather than another
 * `loadImage` overload: a fling across twenty columns binds a hundred items in a single frame, and
 * a request assembled from scratch each time - options, transformations, transition, listener - is
 * a bigger cost than the picture it goes on to fetch. What is left per item is the model, the
 * signature and the target.
 *
 * Everything the full loader spends on looks is gone with it. No rounded corners, so the cheaper
 * 565 bitmaps survive; no cross fade, which at this size only makes a screenful flicker; no error
 * icon, since there is no room to show one.
 */
class SimpleThumbnailLoader(
    context: Context,
    cropThumbnails: Boolean,
    /** What the tile is decoded to - see `GridZoom.simpleThumbnailSize`. */
    size: Int
) {
    private val requests = Glide.with(context.applicationContext)

    // Glide's placeholder rather than a background on the item: a background stays underneath the
    // loaded picture for as long as the item lives, and at these counts that is a second full
    // screen of drawing behind the one being looked at. One shared drawable, which a flat colour
    // can be - it holds no per-target state
    private val placeholder = context.getColor(org.fossify.commons.R.color.md_grey_black).toDrawable()

    private val options = RequestOptions()
        .priority(Priority.LOW)
        // measurably worth the writes: re-decoding a screenful costs more than caching it does,
        // even from a copy stored inside the file
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
            // not the path itself: a tile this small always comes out of the copy stored inside the
            // photo, where there is one. See ThumbnailSource
            .load(ThumbnailSource(path))
            .apply(options)
            .signature(signature)
            .transition(noTransition)
            .into(target)
    }

    fun clear(target: View) = requests.clear(target)
}
