package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withSave
import org.fossify.gallery.R
import kotlin.math.roundToInt

// the cover's picture is recorded this many times smaller: the blur leaves no detail to lose, and the
// layer the blur is worked out in shrinks with the square of it
private const val GLASS_DOWNSAMPLE = 4f

// how much wider than a card its picture is drawn, so the cover's cut corners and the blur's soft
// edges fall outside the card
private const val GLASS_OVERFILL = 1.2f

// how far a flat card is carried from the page towards the text colour
private const val BACK_CARD_FLAT_SHADE = 0.15f
private const val MIDDLE_CARD_FLAT_SHADE = 0.3f

// how much of the page is laid over a glass card's picture, so the stack recedes into it
private const val BACK_CARD_GLASS_FADE = 0x8C
private const val MIDDLE_CARD_GLASS_FADE = 0x59

/**
 * The two cards a stack tile's cover sits on, peeking out above it - the view stackCover names.
 *
 * Where the cover shows a picture of its own the cards are glass: the picture is recorded once, small
 * and blurred, and that one recording is drawn into both cards and faded towards the page. Nothing is
 * requested or decoded again. Behind a group's collage or a padlock, and below Android 12, they are
 * flat cards shaded from the theme.
 */
class FolderStackCards(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val coverId = context.obtainStyledAttributes(attrs, R.styleable.FolderStackCards).let {
        try {
            it.getResourceId(R.styleable.FolderStackCards_stackCover, NO_ID)
        } finally {
            it.recycle()
        }
    }

    private val peek = resources.getDimensionPixelSize(R.dimen.folder_stack_peek)
    private val cornerRadius = resources.getDimension(org.fossify.commons.R.dimen.rounded_corner_radius_big)

    // back to front, so the middle card covers the back one where they overlap. Both are opaque, or
    // the back card would show through
    private val cards = listOf(
        Card(
            inset = resources.getDimensionPixelSize(R.dimen.folder_stack_back_inset),
            top = 0,
            flatShade = BACK_CARD_FLAT_SHADE,
            glassFade = BACK_CARD_GLASS_FADE
        ),
        Card(
            inset = resources.getDimensionPixelSize(R.dimen.folder_stack_middle_inset),
            top = peek,
            flatShade = MIDDLE_CARD_FLAT_SHADE,
            glassFade = MIDDLE_CARD_GLASS_FADE
        )
    )

    private val glass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlassPainter(
            blurRadius = resources.getDimension(R.dimen.folder_stack_glass_blur) / GLASS_DOWNSAMPLE,
            cornerRadius = cornerRadius,
            cardCount = cards.size
        )
    } else {
        null
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.folder_cover_border_width)
    }

    private val bounds = Rect()
    private val shape = RectF()
    private var cover: FolderCoverView? = null
    private var pageColor = Color.TRANSPARENT
    private var textColor = Color.TRANSPARENT

    /** Whether the cover shows a picture of its own to take after - a group's collage or a padlock does not. */
    var takesAfterCover = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** The page the cards fade towards, the text colour flat ones are shaded with, and their edge. */
    fun setColors(page: Int, text: Int, edge: Int) {
        pageColor = page
        textColor = text
        edgePaint.color = edge
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        cover = (parent as? View)?.findViewById<FolderCoverView>(coverId)?.apply {
            onPictureChanged = { this@FolderStackCards.invalidate() }
        }
    }

    override fun onDetachedFromWindow() {
        cover?.onPictureChanged = null
        cover = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val painter = glass
            val cover = cover?.takeIf { takesAfterCover && it.drawable != null && it.width > 0 }
            // a software canvas, as when a tile is drawn into a bitmap, cannot replay a render node
            if (painter != null && cover != null && canvas.isHardwareAccelerated) {
                painter.record(cover)
                cards.forEachIndexed { index, card ->
                    val fade = ColorUtils.setAlphaComponent(pageColor, card.glassFade)
                    painter.drawCard(canvas, index, boundsOf(card), pageColor, fade)
                    drawEdge(canvas)
                }
                return
            }
        }

        cards.forEach { card ->
            fillPaint.color = ColorUtils.blendARGB(pageColor, textColor, card.flatShade)
            shape.set(boundsOf(card))
            canvas.drawRoundRect(shape, cornerRadius, cornerRadius, fillPaint)
            drawEdge(canvas)
        }
    }

    // every card is as tall as the view less the peek, and set in from the cover's sides by its inset
    private fun boundsOf(card: Card): Rect {
        bounds.set(card.inset, card.top, width - card.inset, card.top + height - peek)
        return bounds
    }

    // traced just inside the card last laid out by boundsOf
    private fun drawEdge(canvas: Canvas) {
        val halfEdge = edgePaint.strokeWidth / 2
        shape.set(bounds)
        shape.inset(halfEdge, halfEdge)
        canvas.drawRoundRect(shape, cornerRadius, cornerRadius, edgePaint)
    }

    private class Card(val inset: Int, val top: Int, val flatShade: Float, val glassFade: Int)
}

/** The cards' glass: the cover's picture recorded small and blurred once, then drawn into each card. */
@RequiresApi(Build.VERSION_CODES.S)
private class GlassPainter(blurRadius: Float, private val cornerRadius: Float, cardCount: Int) {
    private val picture = RenderNode("folderStackPicture").apply {
        setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP))
    }

    private val cardNodes = List(cardCount) { RenderNode("folderStackCard") }
    private val outline = Outline()

    fun record(cover: ImageView) {
        val width = (cover.width / GLASS_DOWNSAMPLE).roundToInt().coerceAtLeast(1)
        val height = (cover.height / GLASS_DOWNSAMPLE).roundToInt().coerceAtLeast(1)
        picture.setPosition(0, 0, width, height)
        val canvas = picture.beginRecording(width, height)
        canvas.scale(1 / GLASS_DOWNSAMPLE, 1 / GLASS_DOWNSAMPLE)
        // placed the way the cover places it, so the cards show what the cover shows
        canvas.translate(cover.paddingLeft.toFloat(), cover.paddingTop.toFloat())
        canvas.concat(cover.imageMatrix)
        cover.drawable?.draw(canvas)
        picture.endRecording()
    }

    /** Draws card [index] at [bounds]: [base], the picture over it, and [fade] over that. */
    fun drawCard(canvas: Canvas, index: Int, bounds: Rect, base: Int, fade: Int) {
        val node = cardNodes[index]
        val width = bounds.width()
        node.setPosition(bounds)
        outline.setRoundRect(0, 0, width, bounds.height(), cornerRadius)
        node.setOutline(outline)
        node.setClipToOutline(true)

        val card = node.beginRecording(width, bounds.height())
        card.drawColor(base)
        card.withSave {
            val offset = width * (1 - GLASS_OVERFILL) / 2
            translate(offset, offset)
            val zoom = width * GLASS_OVERFILL / picture.width
            scale(zoom, zoom)
            drawRenderNode(picture)
        }
        card.drawColor(fade)
        node.endRecording()

        canvas.drawRenderNode(node)
    }
}
