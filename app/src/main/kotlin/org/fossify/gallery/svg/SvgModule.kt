package org.fossify.gallery.svg

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.drawable.PictureDrawable
import android.os.ParcelFileDescriptor

import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.caverock.androidsvg.SVG
import org.fossify.gallery.helpers.ExifThumbnailLoader
import org.fossify.gallery.helpers.ThumbnailSource
import org.fossify.gallery.helpers.ThumbnailSourcePassThroughLoader

import java.io.InputStream

@GlideModule
class SvgModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.register(SVG::class.java, PictureDrawable::class.java, SvgDrawableTranscoder()).append(InputStream::class.java, SVG::class.java, SvgDecoder())
        // every kind of data a path resolves to, so a ThumbnailSource can stand in for one anywhere;
        // only the byte stream has an embedded copy to substitute, the rest pass straight through
        registry.append(ThumbnailSource::class.java, InputStream::class.java, ExifThumbnailLoader.Factory())
        registry.append(
            ThumbnailSource::class.java, ParcelFileDescriptor::class.java,
            ThumbnailSourcePassThroughLoader.Factory(ParcelFileDescriptor::class.java)
        )
        registry.append(
            ThumbnailSource::class.java, AssetFileDescriptor::class.java,
            ThumbnailSourcePassThroughLoader.Factory(AssetFileDescriptor::class.java)
        )
    }

    override fun isManifestParsingEnabled() = false
}
