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
import org.fossify.gallery.models.Medium
import org.fossify.gallery.svg.SvgSoftwareLayerSetter

/**
 * The thumbnails of [org.fossify.gallery.views.ViewerThumbnailStrip]. Loading them is all this does:
 * how big and how shaded each one is drawn depends on where the strip has scrolled to, so the strip
 * sets that on the children itself rather than going through a binding for it.
 */
class ViewerThumbnailAdapter(
    private val thumbnailSize: Int,
    private val onItemClick: (position: Int) -> Unit,
) : RecyclerView.Adapter<ViewerThumbnailAdapter.ThumbnailViewHolder>() {

    companion object {
        /**
         * The fraction of the thumbnail's resolution Glide decodes first. A quarter-size bitmap is
         * roughly a sixteenth of the work, so it lands while the finger is still moving and the
         * full one replaces it in place - a strip being scrolled never has to show an empty cell.
         */
        private const val PREVIEW_QUALITY = 0.25f
    }

    private var media = emptyList<Medium>()

    @Suppress("NotifyDataSetChanged")
    fun setItems(newMedia: List<Medium>) {
        media = newMedia
        notifyDataSetChanged()
    }

    override fun getItemCount() = media.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val binding = ViewerThumbnailStripItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )

        return ThumbnailViewHolder(binding).apply {
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
                .apply(RequestOptions().signature(medium.getKey()).override(thumbnailSize))
                .into(target)
            return
        }

        WebpBitmapFactory.sUseSystemDecoder = false // CVE-2023-4863
        val options = RequestOptions()
            .signature(medium.getKey())
            .override(thumbnailSize)
            .centerCrop()
            .dontAnimate()
            // a thumbnail this small has no use for a third byte per channel, and half the bitmap
            // decodes in half the time
            .format(DecodeFormat.PREFER_RGB_565)
            .priority(Priority.HIGH)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)

        Glide.with(context)
            .asBitmap()
            .load(medium.path)
            .apply(options)
            .set(WebpDownsampler.USE_SYSTEM_DECODER, false) // CVE-2023-4863
            .thumbnail(PREVIEW_QUALITY)
            .into(target)
    }

    class ThumbnailViewHolder(val binding: ViewerThumbnailStripItemBinding) :
        RecyclerView.ViewHolder(binding.root)
}
