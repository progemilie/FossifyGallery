package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.annotation.RequiresApi
import org.fossify.commons.views.MySquareImageView
import org.fossify.gallery.R
import kotlin.math.roundToInt

/**
 * A folder cover that can be taller than it is wide, rounded by its own outline, and frosted where a
 * label is laid over it - the sibling frostBehind names, followed as it is laid out.
 *
 * The frost is the cover's own drawing replayed through a blur, so it costs no second image request,
 * and it fades in over a short feather above the label rather than starting at an edge. Below
 * Android 12 there is no cheap blur, and the label is left to the scrim laid under it.
 */
class FolderCoverView : MySquareImageView {
    private var aspectRatio = 1f
    private var cornerRadius = 0f
    private var frostAnchorId = NO_ID
    private var frostAnchor: View? = null
    private var frost: FrostPainter? = null

    // a label wrapping onto another line moves its top without this view being drawn again
    private val anchorListener = OnLayoutChangeListener { _, _, top, _, _, _, oldTop, _, _ ->
        if (top != oldTop) {
            invalidate()
        }
    }

    constructor(context: Context) : super(context) {
        readAttributes(null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        readAttributes(attrs)
    }

    private fun readAttributes(attrs: AttributeSet?) {
        val styled = context.obtainStyledAttributes(attrs, R.styleable.FolderCoverView)
        try {
            aspectRatio = styled.getFloat(R.styleable.FolderCoverView_coverAspectRatio, 1f)
            cornerRadius = styled.getDimension(R.styleable.FolderCoverView_coverCornerRadius, 0f)
            frostAnchorId = styled.getResourceId(R.styleable.FolderCoverView_frostBehind, NO_ID)
        } finally {
            styled.recycle()
        }

        if (cornerRadius > 0f) {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }

            clipToOutline = true
        }

        if (frostAnchorId != NO_ID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            frost = FrostPainter(
                blurRadius = resources.getDimension(R.dimen.folder_card_frost_radius),
                featherHeight = resources.getDimension(R.dimen.folder_card_frost_feather)
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (aspectRatio == 1f) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } else if (isHorizontalScrolling) {
            val height = MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension((height / aspectRatio).roundToInt(), height)
        } else {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width, (width * aspectRatio).roundToInt())
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (frost != null) {
            frostAnchor = (parent as? View)?.findViewById<View>(frostAnchorId)?.apply {
                addOnLayoutChangeListener(anchorListener)
            }
        }
    }

    override fun onDetachedFromWindow() {
        frostAnchor?.removeOnLayoutChangeListener(anchorListener)
        frostAnchor = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }

        val painter = frost ?: return
        val anchor = frostAnchor ?: return
        // a software canvas, as when a tile is drawn into a bitmap, cannot replay a render node
        if (drawable == null || anchor.visibility != VISIBLE || !canvas.isHardwareAccelerated) {
            return
        }

        val bandTop = (anchor.top - top - painter.featherHeight).roundToInt().coerceAtLeast(0)
        if (bandTop < height) {
            painter.draw(canvas, bandTop, width, height) { super.onDraw(it) }
        }
    }
}

/** The frosted band of a [FolderCoverView]: its picture blurred, and faded in from the band's top. */
@RequiresApi(Build.VERSION_CODES.S)
private class FrostPainter(blurRadius: Float, val featherHeight: Float) {
    private val picture = RenderNode("folderCoverPicture").apply {
        setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP))
    }

    // a layer of its own: that is what lets the fade mask the blur alone, and it keeps both from
    // being worked out again on the frames the cover does not change in
    private val band = RenderNode("folderCoverFrost").apply {
        setUseCompositingLayer(true, null)
    }

    private val fadePaint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, featherHeight, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    fun draw(canvas: Canvas, bandTop: Int, width: Int, height: Int, drawPicture: (Canvas) -> Unit) {
        picture.setPosition(0, 0, width, height)
        drawPicture(picture.beginRecording(width, height))
        picture.endRecording()

        val bandHeight = height - bandTop
        band.setPosition(0, bandTop, width, height)
        val frost = band.beginRecording(width, bandHeight)
        frost.translate(0f, -bandTop.toFloat())
        frost.drawRenderNode(picture)
        frost.translate(0f, bandTop.toFloat())
        frost.drawRect(0f, 0f, width.toFloat(), bandHeight.toFloat(), fadePaint)
        band.endRecording()

        canvas.drawRenderNode(band)
    }
}
