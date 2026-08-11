package org.fossify.gallery.adapters

import android.graphics.drawable.PictureDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.integration.webp.WebpBitmapFactory
import com.bumptech.glide.integration.webp.decoder.WebpDownsampler
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import org.fossify.gallery.databinding.ViewerThumbnailStripItemBinding
import org.fossify.gallery.helpers.ThumbnailSource
import org.fossify.gallery.models.Medium
import org.fossify.gallery.svg.SvgSoftwareLayerSetter

/**
 * The thumbnails of [org.fossify.gallery.views.ViewerThumbnailStrip]. Loading them is all this does:
 * how big and how shaded each one is drawn depends on where the strip has scrolled to, so the strip
 * sets that on the children itself rather than going through a binding for it.
 */
class ViewerThumbnailAdapter(
    private val thumbnailWidth: Int,
    private val thumbnailHeight: Int,
    private val onItemClick: (position: Int) -> Unit,
) : RecyclerView.Adapter<ViewerThumbnailAdapter.ThumbnailViewHolder>() {

    private var media = emptyList<Medium>()

    @Suppress("NotifyDataSetChanged")
    fun setItems(newMedia: List<Medium>) {
        media = newMedia
        notifyDataSetChanged()
    }

    /**
     * Draws [path] again, for a file this app has just changed in place. Nothing about the [Medium]
     * moved, so nothing here would otherwise rebind - and a thumbnail already on screen goes on
     * showing the bitmap it was given however thoroughly the caches behind it were emptied.
     */
    fun reload(path: String) {
        media.forEachIndexed { index, medium ->
            if (medium.path == path) {
                notifyItemChanged(index)
            }
        }
    }

    override fun getItemCount() = media.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val binding = ViewerThumbnailStripItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )

        return ThumbnailViewHolder(binding).apply {
            // rounds the thumbnail and the shade over it in one step, against the holder's own
            // background. Rounding the bitmap instead - Glide's RoundedCorners - would cost the
            // RGB_565 below: corners need transparency and 565 has none, so it would come back
            // ARGB_8888 at twice the memory. The XML attribute for this is API 31, hence in code
            binding.viewerThumbnailHolder.clipToOutline = true
            binding.root.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(bindingAdapterPosition)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        loadThumbnail(holder.binding.viewerThumbnailImage, media[position])
    }

    override fun onViewRecycled(holder: ThumbnailViewHolder) {
        super.onViewRecycled(holder)
        // scrolling far enough leaves a queue of decodes for thumbnails nobody can see anymore
        Glide.with(holder.itemView.context.applicationContext)
            .clear(holder.binding.viewerThumbnailImage)
    }

    private fun loadThumbnail(target: ImageView, medium: Medium) {
        val context = target.context.applicationContext
        if (medium.isSVG()) {
            Glide.with(context)
                .`as`(PictureDrawable::class.java)
                .listener(SvgSoftwareLayerSetter())
                .load(medium.path)
                .apply(
                    RequestOptions().signature(medium.getKey())
                        .override(thumbnailWidth, thumbnailHeight)
                )
                .into(target)
            return
        }

        WebpBitmapFactory.sUseSystemDecoder = false // CVE-2023-4863
        val options = RequestOptions()
            .signature(medium.getKey())
            .override(thumbnailWidth, thumbnailHeight)
            .centerCrop()
            .dontAnimate()
            // a thumbnail this small has no use for a third byte per channel, and half the bitmap
            // decodes in half the time
            .format(DecodeFormat.PREFER_RGB_565)
            .priority(Priority.HIGH)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)

        Glide.with(context)
            .asBitmap()
            // a strip thumbnail is small enough that the copy stored inside the photo is nearly
            // always both good enough and far quicker to read. See ThumbnailSource
            .load(ThumbnailSource(medium.path))
            .apply(options)
            .set(WebpDownsampler.USE_SYSTEM_DECODER, false) // CVE-2023-4863
            .into(target)
    }

    class ThumbnailViewHolder(val binding: ViewerThumbnailStripItemBinding) :
        RecyclerView.ViewHolder(binding.root)
}
