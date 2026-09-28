package org.fossify.gallery.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.graphics.drawable.TransitionDrawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.withClip
import com.bumptech.glide.integration.webp.decoder.WebpDrawable
import com.bumptech.glide.load.resource.gif.GifDrawable
import org.fossify.gallery.helpers.Perf
import org.fossify.gallery.helpers.ZoomLayer
import org.fossify.gallery.helpers.ZoomScene
import org.fossify.gallery.helpers.forEachVisible
import org.fossify.gallery.helpers.ZoomThumbnails
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import kotlin.math.abs

/** Two tiles closer than this are taken to be in the same cell. */
private const val SAME_CELL_PX = 1.5f

private const val OPAQUE = 255

/**
 * Where the grid sits in the overlay, and which way round: everything a [ZoomScene] works out is in
 * the grid's own coordinates turned so that "along" runs the way it scrolls.
 */
class GridFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val horizontal: Boolean,
    /** A sideways grid laid out right to left, whose content starts at its right edge. */
    val reversed: Boolean,
    /** The grid's padding across, ahead of its spans and after them. */
    val acrossPaddingStart: Int,
    val acrossPaddingEnd: Int,
) {
    val alongLength get() = if (horizontal) width else height

    /** What the spans share out across: the grid's size that way, less its padding. */
    val acrossSpace get() = (if (horizontal) height else width).toInt() - acrossPaddingStart - acrossPaddingEnd

    /**
     * Where the grid can show a tile at all, in the overlay's coordinates: never in its padding
     * across. Tiles a zoom brings in from the sides come in from there, as they come in from off
     * screen in a grid with no such padding, rather than being drawn whole in it.
     */
    val tileBounds = if (horizontal) {
        RectF(left, top + acrossPaddingStart, left + width, top + height - acrossPaddingEnd)
    } else {
        RectF(left + acrossPaddingStart, top, left + width - acrossPaddingEnd, top + height)
    }

    /** Puts a square [size] long at [along], [across] into [out], in the overlay's coordinates. */
    fun square(along: Float, across: Float, size: Float, out: RectF) {
        if (horizontal) {
            val x = if (reversed) width - along - size else along
            out.set(left + x, top + across, left + x + size, top + across + size)
        } else {
            out.set(left + across, top + along, left + across + size, top + along + size)
        }
    }
}

/** How a grouping header's title is drawn, measured off a real header. */
class HeaderStyle(
    val paint: TextPaint,
    /** Where the title starts, or for [rtl] ends, across the header. */
    val textStart: Float,
    val textEnd: Float,
    val baseline: Float,
    val rtl: Boolean,
)

/** How a tile is drawn at the counts where it carries everything. */
class TileStyle(
    val placeholderColor: Int,
    val cornerRadius: Float,
    /** Around the picture in a full tile, whose holder is padded by a thin spacing. */
    val padding: Int,
    /**
     * Whether an SVG fills its tile, cropped, rather than fitting inside it. Its view scales it, where
     * a photo's thumbnail comes cropped or not out of the decoder, and is only ever fitted.
     */
    val cropPictures: Boolean,
)

/** What a zoom knows of each of the ladder's column counts, by rung. */
interface ZoomCounts {
    /** The list the count's layout was worked out from. */
    fun itemsOf(rung: Int): List<ThumbnailItem>

    fun columnsOf(rung: Int): Int

    fun isSimplified(rung: Int): Boolean
}

/** Everything one zoom is drawn from. */
class ZoomDrawing(
    val scene: ZoomScene,
    val thumbnails: ZoomThumbnails,
    val frame: GridFrame,
    val headerStyle: HeaderStyle?,
    val tileStyle: TileStyle,
    val counts: ZoomCounts,
)

/**
 * The media grid while it is being zoomed, drawn from a [ZoomScene] in place of the grid itself,
 * which sits over it invisible until the zoom hands back. Two counts are drawn at once between whole
 * levels: the one with fewer columns underneath, the other over it fading in.
 *
 * Only a tile's picture is drawn, never its badges: at the sizes a pinch passes through there is
 * nothing to read on them, and the grid's own tiles bring them back as it fades in over this.
 */
class GridZoomOverlay(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var drawing: ZoomDrawing? = null
        set(value) {
            field = value
            if (value == null) {
                // each holds on to what it last drew - the whole zoom, and the grid's list with it
                underPass = LayerPass()
                overPass = LayerPass()
            }

            invalidate()
        }

    private val painter = ZoomTilePainter()
    private var underPass = LayerPass()
    private var overPass = LayerPass()
    private val tile = RectF()
    private val clip = Rect()

    init {
        // nothing of its own to draw until a zoom starts
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val drawing = drawing ?: return
        Perf.section("zoom.draw") { draw(canvas, drawing) }
    }

    /**
     * Draws only as much as the canvas can show. The glass panels floating over the grid copy
     * whatever is behind them into a small software canvas of their own every frame - see
     * GlassPanel - and a whole screenful of tiles drawn twice more in software is most of a frame.
     */
    private fun draw(canvas: Canvas, drawing: ZoomDrawing) {
        canvas.withClip(drawing.frame.tileBounds) {
            drawClipped(canvas, drawing)
        }
    }

    private fun drawClipped(canvas: Canvas, drawing: ZoomDrawing) {
        if (!canvas.getClipBounds(clip)) {
            return
        }

        // only the screen's own frame asks for pictures, and it asks once - the canvas's to say, not
        // the view's, which is hardware accelerated however it is being drawn
        val isScreen = canvas.isHardwareAccelerated
        if (isScreen) {
            drawing.thumbnails.startFrame()
        }

        val scene = drawing.scene
        val over = scene.over
        drawLayer(canvas, underPass.set(drawing, scene.under, over, isOver = false, mayAsk = isScreen, clip))
        if (over != null) {
            drawLayer(canvas, overPass.set(drawing, over, scene.under, isOver = true, mayAsk = isScreen, clip))
        }

        if (isScreen && drawing.thumbnails.isShortOfAsks) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawLayer(canvas: Canvas, pass: LayerPass) {
        val layer = pass.layer
        val other = pass.other
        pass.layer.forEachVisible(pass.alongFrom, pass.alongTo, onHeader = { section, start ->
            val matches = other != null && headerMatches(layer, section, start, other)
            val match = if (matches) Match.SAME else Match.NONE
            val title = (pass.items[layer.layout.sections.headerPosition(section)] as? ThumbnailSection)?.title
            val alpha = alphaFor(pass, match, isBeside = false)
            if (alpha > 0f && title != null) {
                drawHeader(canvas, pass.drawing, title, start, alpha)
            }
        }, onRow = { section, row, _ ->
            drawRow(canvas, pass, section, row)
        })
    }

    private fun drawRow(canvas: Canvas, pass: LayerPass, section: Int, row: Int) {
        val layer = pass.layer
        val along = layer.tileAlong(section, row)
        // the other count's row drawn in the same place, found once for the whole row
        val other = pass.other?.takeIf { findRowAt(it, along + layer.tileSize(0) / 2, along) }
        for (span in 0 until layer.layout.tilesInRow(section, row)) {
            val across = layer.tileAcross(span)
            if (across + layer.tileSize(span) > pass.acrossFrom && across < pass.acrossTo) {
                drawTileAt(canvas, pass, other, section, row, span, along)
            }
        }
    }

    @Suppress("LongParameterList")
    private fun drawTileAt(
        canvas: Canvas,
        pass: LayerPass,
        other: ZoomLayer?,
        section: Int,
        row: Int,
        span: Int,
        along: Float,
    ) {
        val layer = pass.layer
        val layout = layer.layout
        val position = layout.positionAt(section, row, span)
        val medium = pass.items.getOrNull(position) as? Medium ?: return
        val across = layer.tileAcross(span)
        val size = layer.tileSize(span)
        val match = if (other != null) {
            tileMatch(other, across, size, layout.sections.ordinalOf(section, position))
        } else {
            Match.NONE
        }

        // the columns the fewer-column count has no room for come in from the sides whole
        val middle = across + size / 2
        val under = pass.other
        val isBeside = pass.isOver && under != null && (middle < under.spansStart || middle > under.spansEnd)
        val alpha = alphaFor(pass, match, isBeside)
        if (alpha > 0f) {
            pass.drawing.frame.square(along, across, size, tile)
            val picture = pass.drawing.thumbnails.pictureFor(medium, pass.columns, pass.mayAsk)
            painter.drawTile(canvas, tile, picture, pass, alpha)
        }
    }

    // where the row found by findRowAt is kept for the tiles of the row being drawn
    private var matchSection = -1
    private var matchRow = -1

    /** Whether [other] draws a row starting at [along] too, remembering which one if so. */
    private fun findRowAt(other: ZoomLayer, probe: Float, along: Float): Boolean {
        val section = other.sectionAt(probe)
        if (other.layout.rows(section) == 0) {
            return false
        }

        val row = other.rowAt(section, probe)
        matchSection = section
        matchRow = row
        return abs(other.tileAlong(section, row) - along) < SAME_CELL_PX
    }

    /** What the other count draws in the same cell as a tile of [ordinal], in the row found for it. */
    private fun tileMatch(other: ZoomLayer, across: Float, size: Float, ordinal: Int): Match {
        val span = other.spanAt(across + size / 2)
        val position = if (span >= 0) other.layout.positionAt(matchSection, matchRow, span) else -1
        return when {
            position < 0 || abs(other.tileAcross(span) - across) >= SAME_CELL_PX -> Match.NONE
            other.layout.sections.ordinalOf(matchSection, position) == ordinal -> Match.SAME
            else -> Match.OTHER
        }
    }

    private fun headerMatches(layer: ZoomLayer, section: Int, start: Float, other: ZoomLayer): Boolean {
        val otherSections = other.layout.sections
        return otherSections.count == layer.layout.sections.count && otherSections.isHeaded(section) &&
            abs(other.headerStart(section) - start) < SAME_CELL_PX
    }

    /**
     * How opaque a count draws something, given what the other count draws in its place. What both
     * draw in one place is drawn once, whole. Under a different tile of the other count a tile stays
     * whole for that one to fade in onto, and is left out once it has; anything with nothing in its
     * place fades.
     */
    private fun alphaFor(pass: LayerPass, match: Match, isBeside: Boolean) = when {
        pass.other == null -> 1f
        pass.isOver -> if (match == Match.SAME || isBeside) 1f else pass.fade
        match == Match.SAME -> 0f
        match == Match.OTHER -> if (pass.fade >= 1f) 0f else 1f
        else -> 1f - pass.fade
    }

    /** A header keeps its size and its place across; a pinch only moves it along. */
    private fun drawHeader(canvas: Canvas, drawing: ZoomDrawing, title: String, start: Float, alpha: Float) {
        val style = drawing.headerStyle ?: return
        val frame = drawing.frame
        val x = if (style.rtl) style.textEnd - style.paint.measureText(title) else style.textStart
        val baseAlpha = style.paint.alpha
        style.paint.alpha = (baseAlpha * alpha).toInt()
        canvas.drawText(title, frame.left + x, frame.top + start + style.baseline, style.paint)
        style.paint.alpha = baseAlpha
    }

    private enum class Match { NONE, OTHER, SAME }
}

/** One count's share of a frame, with what all its tiles have in common looked up once. */
internal class LayerPass {
    lateinit var drawing: ZoomDrawing
    lateinit var layer: ZoomLayer
    lateinit var items: List<ThumbnailItem>
    var other: ZoomLayer? = null
    var isOver = false
    var columns = 0
    var isSimplified = false
    var fade = 0f
    var mayAsk = false

    // the part of the grid the canvas shows, in the scene's own terms
    var alongFrom = 0f
    var alongTo = 0f
    var acrossFrom = 0f
    var acrossTo = 0f

    @Suppress("LongParameterList")
    fun set(
        drawing: ZoomDrawing,
        layer: ZoomLayer,
        other: ZoomLayer?,
        isOver: Boolean,
        mayAsk: Boolean,
        clip: Rect
    ): LayerPass {
        this.drawing = drawing
        this.layer = layer
        this.other = other
        this.isOver = isOver
        this.mayAsk = mayAsk
        items = drawing.counts.itemsOf(layer.rung)
        columns = drawing.counts.columnsOf(layer.rung)
        isSimplified = drawing.counts.isSimplified(layer.rung)
        fade = drawing.scene.fade
        val frame = drawing.frame
        val left = clip.left - frame.left
        val right = clip.right - frame.left
        val top = clip.top - frame.top
        val bottom = clip.bottom - frame.top
        when {
            !frame.horizontal -> setRange(top, bottom, left, right)
            frame.reversed -> setRange(frame.width - right, frame.width - left, top, bottom)
            else -> setRange(left, right, top, bottom)
        }

        return this
    }

    private fun setRange(alongFrom: Float, alongTo: Float, acrossFrom: Float, acrossTo: Float) {
        this.alongFrom = alongFrom
        this.alongTo = alongTo
        this.acrossFrom = acrossFrom
        this.acrossTo = acrossTo
    }
}

/** Draws one tile's picture the way the grid's own tile would show it. */
internal class ZoomTilePainter {
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fitted = RectF()
    private val source = Rect()

    fun drawTile(canvas: Canvas, tile: RectF, found: Drawable?, pass: LayerPass, alpha: Float) {
        val shown = found?.let(::shownOf)
        val bitmap = shown?.let(::bitmapOf)?.takeIf { !it.isRecycled }
        val picture = (shown as? PictureDrawable)?.picture?.takeIf { it.width > 0 && it.height > 0 }
        if (!pass.isSimplified) {
            val padding = pass.drawing.tileStyle.padding * pass.layer.scaleAcross
            tile.inset(padding, padding)
        }

        // a full tile keeps its placeholder under a picture that leaves any of it showing, as the
        // grid's does, and any tile shows one while it has no picture at all
        val hasPicture = bitmap != null || picture != null
        val covered = fit(
            tile,
            width = bitmap?.width ?: picture?.width ?: 0,
            height = bitmap?.height ?: picture?.height ?: 0,
            crop = picture != null && pass.drawing.tileStyle.cropPictures
        )

        if (!hasPicture || (!covered && !pass.isSimplified)) {
            drawPlaceholder(canvas, tile, pass, alpha)
        }

        if (bitmap != null) {
            source.set(0, 0, bitmap.width, bitmap.height)
            tilePaint.alpha = (alpha * OPAQUE).toInt()
            canvas.drawBitmap(bitmap, source, fitted, tilePaint)
        } else if (picture != null) {
            drawPicture(canvas, tile, picture, alpha)
        }
    }

    // held to the tile, which a cropped picture overhangs; and a picture takes no paint to fade it
    // by, so one part way through a fade goes through a layer - only an SVG's, for a fade's few frames
    private fun drawPicture(canvas: Canvas, tile: RectF, picture: Picture, alpha: Float) {
        val saved = if (alpha < 1f) canvas.saveLayerAlpha(tile, (alpha * OPAQUE).toInt()) else canvas.save()
        canvas.clipRect(tile)
        canvas.drawPicture(picture, fitted)
        canvas.restoreToCount(saved)
    }

    private fun drawPlaceholder(canvas: Canvas, tile: RectF, pass: LayerPass, alpha: Float) {
        val style = pass.drawing.tileStyle
        val radius = if (pass.isSimplified) 0f else style.cornerRadius * pass.layer.scaleAcross
        placeholderPaint.color = style.placeholderColor
        placeholderPaint.alpha = (alpha * OPAQUE).toInt()
        canvas.drawRoundRect(tile, radius, radius, placeholderPaint)
    }

    /**
     * Fits [width] by [height] into [tile] the way the grid's ImageView does, centred - or where
     * [crop], over it - and says whether that covers the whole tile. Nothing to fit covers nothing.
     */
    private fun fit(tile: RectF, width: Int, height: Int, crop: Boolean): Boolean {
        if (width <= 0 || height <= 0) {
            fitted.set(tile)
            return false
        }

        val widthScale = tile.width() / width
        val heightScale = tile.height() / height
        val scale = if (crop) maxOf(widthScale, heightScale) else minOf(widthScale, heightScale)
        val halfWidth = width * scale / 2
        val halfHeight = height * scale / 2
        fitted.set(
            tile.centerX() - halfWidth,
            tile.centerY() - halfHeight,
            tile.centerX() + halfWidth,
            tile.centerY() + halfHeight
        )
        return fitted.width() >= tile.width() - 1 && fitted.height() >= tile.height() - 1
    }

    /** What a tile shows once any cross-fade Glide wrapped it in is over. */
    private fun shownOf(drawable: Drawable): Drawable =
        if (drawable is TransitionDrawable && drawable.numberOfLayers > 0) {
            shownOf(drawable.getDrawable(drawable.numberOfLayers - 1))
        } else {
            drawable
        }

    /** The bitmap a tile shows, or null where it is not one. */
    private fun bitmapOf(drawable: Drawable): Bitmap? = when (drawable) {
        is BitmapDrawable -> drawable.bitmap
        is GifDrawable -> drawable.firstFrame
        is WebpDrawable -> drawable.firstFrame
        else -> null
    }
}
