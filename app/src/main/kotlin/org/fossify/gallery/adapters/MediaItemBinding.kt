package org.fossify.gallery.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.fossify.commons.views.MySquareImageView
import org.fossify.gallery.databinding.PhotoItemGridBinding
import org.fossify.gallery.databinding.PhotoItemListBinding
import org.fossify.gallery.databinding.VideoItemGridBinding
import org.fossify.gallery.databinding.VideoItemListBinding

interface MediaItemBinding {
    val root: ViewGroup
    val mediaItemHolder: ViewGroup
    val favorite: ImageView
    val playPortraitOutline: ImageView?
    val fileType: TextView?
    val mediumName: TextView
    val videoDuration: TextView?
    val mediumCheck: ImageView
    val mediumThumbnail: MySquareImageView

    // the box that opens the peek viewer, bigger than the button drawn inside it. Only the grid
    // layouts carry one - see MediaAdapter.setupThumbnail
    val mediumPeek: View?

    // only the photo layouts carry it - a video cannot hold a rating, so there is never one to show
    val ratingBadge: TextView?
}

class PhotoListMediaItemBinding(val binding: PhotoItemListBinding) : MediaItemBinding {
    override val root: ViewGroup = binding.root
    override val mediaItemHolder: ViewGroup = binding.mediaItemHolder
    override val favorite: ImageView = binding.favorite
    override val playPortraitOutline: ImageView? = null
    override val fileType: TextView = binding.fileType
    override val mediumName: TextView = binding.mediumName
    override val videoDuration: TextView? = null
    override val mediumCheck: ImageView = binding.mediumCheck
    override val mediumThumbnail: MySquareImageView = binding.mediumThumbnail
    override val ratingBadge: TextView = binding.ratingBadge
    override val mediumPeek: View? = null
}

fun PhotoItemListBinding.toMediaItemBinding() = PhotoListMediaItemBinding(this)

class PhotoGridMediaItemBinding(val binding: PhotoItemGridBinding) : MediaItemBinding {
    override val root: ViewGroup = binding.root
    override val mediaItemHolder: ViewGroup = binding.mediaItemHolder
    override val favorite: ImageView = binding.favorite
    override val playPortraitOutline: ImageView? = null
    override val fileType: TextView = binding.fileType
    override val mediumName: TextView = binding.mediumName
    override val videoDuration: TextView? = null
    override val mediumCheck: ImageView = binding.mediumCheck
    override val mediumThumbnail: MySquareImageView = binding.mediumThumbnail
    override val ratingBadge: TextView = binding.ratingBadge
    override val mediumPeek: View = binding.mediumPeek
}

fun PhotoItemGridBinding.toMediaItemBinding() = PhotoGridMediaItemBinding(this)

class VideoListMediaItemBinding(val binding: VideoItemListBinding) : MediaItemBinding {
    override val root: ViewGroup = binding.root
    override val mediaItemHolder: ViewGroup = binding.mediaItemHolder
    override val favorite: ImageView = binding.favorite
    override val playPortraitOutline: ImageView = binding.playPortraitOutline
    override val fileType: TextView? = null
    override val mediumName: TextView = binding.mediumName
    override val videoDuration: TextView = binding.videoDuration
    override val mediumCheck: ImageView = binding.mediumCheck
    override val mediumThumbnail: MySquareImageView = binding.mediumThumbnail
    override val ratingBadge: TextView? = null
    override val mediumPeek: View? = null
}

fun VideoItemListBinding.toMediaItemBinding() = VideoListMediaItemBinding(this)

class VideoGridMediaItemBinding(val binding: VideoItemGridBinding) : MediaItemBinding {
    override val root: ViewGroup = binding.root
    override val mediaItemHolder: ViewGroup = binding.mediaItemHolder
    override val favorite: ImageView = binding.favorite
    override val playPortraitOutline: ImageView = binding.playPortraitOutline
    override val fileType: TextView? = null
    override val mediumName: TextView = binding.mediumName
    override val videoDuration: TextView = binding.videoDuration
    override val mediumCheck: ImageView = binding.mediumCheck
    override val mediumThumbnail: MySquareImageView = binding.mediumThumbnail
    override val ratingBadge: TextView? = null
    override val mediumPeek: View = binding.mediumPeek
}

fun VideoItemGridBinding.toMediaItemBinding() = VideoGridMediaItemBinding(this)
