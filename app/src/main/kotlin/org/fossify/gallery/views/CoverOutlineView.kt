package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import org.fossify.gallery.R
import org.fossify.gallery.helpers.OutlineLook
import org.fossify.gallery.helpers.OutlinePainter
import org.fossify.gallery.helpers.PhotoColor

/**
 * TEMPORARY, see OutlineStyle. The outline around a folder's cover, laid over it in the layout. A
 * style that glows or rings outside the cover draws past this view's bounds, which is why the tile and
 * the grid holding it leave their children unclipped.
 */
class CoverOutlineView(context: Context, attrs: AttributeSet?) :
    View(context, attrs),
    ViewTreeObserver.OnPreDrawListener {

    private val cornerRadius = resources.getDimension(org.fossify.commons.R.dimen.rounded_corner_radius_big)
    private val painter = OutlinePainter(resources.displayMetrics.density)
    private val shape = RectF()
    private var cover: ImageView? = null
    private var coverPicture: Drawable? = null
    private var pictureColor: Int? = null

    var look: OutlineLook? = null
        set(value) {
            if (field != value) {
                field = value
                coverPicture = null
                invalidate()
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        cover = (parent as? View)?.findViewById(R.id.dir_thumbnail)
        viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(this)
        cover = null
        super.onDetachedFromWindow()
    }

    // nothing tells a sibling its picture changed, so a cover taking its photo's colour looks each frame
    override fun onPreDraw(): Boolean {
        val look = look
        if (look?.photoColor == true) {
            val picture = cover?.drawable
            if (picture !== coverPicture) {
                coverPicture = picture
                pictureColor = PhotoColor.of(picture, look.isDarkTheme)
                invalidate()
            }
        }

        return true
    }

    override fun onDraw(canvas: Canvas) {
        val look = look ?: return
        val shown = pictureColor?.takeIf { look.photoColor }?.let(look::withSource) ?: look
        shape.set(0f, 0f, width.toFloat(), height.toFloat())
        painter.drawOutside(canvas, shape, cornerRadius, shown)
        painter.drawInside(canvas, shape, cornerRadius, shown)
    }
}
