package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import org.fossify.gallery.R
import org.fossify.gallery.helpers.MAX_FOLDER_GROUP_COVERS
import kotlin.math.roundToInt

/** How much of the tray's width is kept clear around the covers, and between them. */
private const val TRAY_PADDING = 0.07f
private const val CELL_GAP = 0.05f

/** How much of the accent is washed into the background for the tray, and of the text into an empty place. */
private const val TRAY_TINT = 0.32f
private const val EMPTY_SLOT_TINT = 0.1f

/** How round a cover inside the tray is against the tray itself; never square, whatever the style. */
private const val CELL_ROUNDING = 0.55f

/** How dark the cover under a "+3" is kept, for the count to read over any picture. */
private const val OVERFLOW_SHADE_ALPHA = 140

/** The "+3" against the height of the cover it is written on. */
private const val OVERFLOW_TEXT_SIZE = 0.34f

/** The group mark ahead of a group's name, against the name's text size, and the gap after it. */
private const val GROUP_MARK_SCALE = 1.05f
private const val GROUP_MARK_GAP = 0.3f

/**
 * A folder group's cover: a tray holding the group's leading folders as covers of their own, two by
 * two, the way a phone's home screen draws a folder of apps. It is the tray, and the gaps it shows
 * through, that has a group read as a group at a glance - edge to edge, the covers made one busy
 * picture that was taken for a folder's own.
 *
 * The tray always has four places. A group of fewer folders leaves the rest as empty places, so every
 * group has the one shape; a group of more has its fourth cover say how many more there are.
 *
 * The cells are plain ImageViews the caller loads into, so a cell costs no more than the folder's own
 * thumbnail would; each is rounded by its outline, and the tray by its own.
 */
class FolderGroupThumbnail @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val borderWidth = resources.getDimension(R.dimen.folder_group_border_width)
    private val minCellRadius = resources.getDimension(R.dimen.folder_group_cell_min_radius)
    private val bounds = List(MAX_FOLDER_GROUP_COVERS) { Rect() }
    private val slotRect = RectF()

    private val trayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emptySlotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
    }

    private val overflowShade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(OVERFLOW_SHADE_ALPHA, 0, 0, 0)
    }

    private val overflowText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val cellOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cellRadius)
        }
    }

    private val cells = List(MAX_FOLDER_GROUP_COVERS) {
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = cellOutline
            clipToOutline = true
        }
    }

    /** How many folders the group holds, of which the first [shownCells] are drawn. */
    private var members = 0
    private var shownCells = 0
    private var cornerRadius = 0f
    private var cellRadius = minCellRadius

    init {
        setWillNotDraw(false)
        // what a screen reader says a group is, as the tray says it to the eye
        contentDescription = context.getString(R.string.folder_group)
        cells.forEach { addView(it) }
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
    }

    /**
     * Readies the tray for a group of [members] folders and hands back the cells to load its leading
     * ones into, in order. Cells past them are hidden and left holding nothing.
     */
    fun prepareCells(members: Int): List<ImageView> {
        val wanted = members.coerceIn(0, MAX_FOLDER_GROUP_COVERS)
        if (wanted != shownCells || members != this.members) {
            shownCells = wanted
            this.members = members
            invalidate()
        }

        cells.forEachIndexed { index, cell ->
            cell.setImageDrawable(null)
            cell.visibility = if (index < shownCells) VISIBLE else GONE
        }

        return cells.take(shownCells)
    }

    /** The cells actually drawing a member, for anything that has to paint the cover as a whole. */
    fun shownCells(): List<ImageView> = cells.take(shownCells)

    /** Hands every cell to [clear] so a recycled tile drops the image requests it had going. */
    fun clearCells(clear: (ImageView) -> Unit) {
        cells.forEach(clear)
    }

    fun setCornerRadius(radius: Float) {
        if (cornerRadius != radius) {
            cornerRadius = radius
            cellRadius = maxOf(minCellRadius, radius * CELL_ROUNDING)
            invalidateOutline()
            cells.forEach { it.invalidateOutline() }
            invalidate()
        }
    }

    /**
     * Takes its colours from the theme: a tray of the accent washed into the grid's background, a
     * colour no photo comes in, with empty places a shade towards the text and an edge of the accent.
     */
    fun setThemeColors(background: Int, accent: Int, text: Int) {
        val tray = ColorUtils.blendARGB(background, accent, TRAY_TINT)
        val emptySlot = ColorUtils.blendARGB(tray, text, EMPTY_SLOT_TINT)
        if (trayPaint.color != tray || emptySlotPaint.color != emptySlot || borderPaint.color != accent) {
            trayPaint.color = tray
            emptySlotPaint.color = emptySlot
            borderPaint.color = accent
            // what shows in a cell until its cover arrives
            cells.forEach { it.setBackgroundColor(emptySlot) }
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        layOutTray(bounds, width, height)
        cells.forEachIndexed { index, cell ->
            val rect = bounds[index]
            cell.measure(
                MeasureSpec.makeMeasureSpec(rect.width(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(rect.height(), MeasureSpec.EXACTLY)
            )
        }

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        cells.forEachIndexed { index, cell ->
            val rect = bounds[index]
            cell.layout(rect.left, rect.top, rect.right, rect.bottom)
        }
    }

    // the tray and its empty places, under the covers
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(trayPaint.color)
        for (index in shownCells until MAX_FOLDER_GROUP_COVERS) {
            slotRect.set(bounds[index])
            canvas.drawRoundRect(slotRect, cellRadius, cellRadius, emptySlotPaint)
        }
    }

    // over the covers: how many more there are, and the edge
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (members > MAX_FOLDER_GROUP_COVERS) {
            drawOverflow(canvas, bounds.last(), members - MAX_FOLDER_GROUP_COVERS + 1)
        }

        if (borderPaint.color != Color.TRANSPARENT) {
            // inset by half the stroke so the whole of it lands inside the clip
            val inset = borderWidth / 2
            slotRect.set(inset, inset, width - inset, height - inset)
            canvas.drawRoundRect(slotRect, cornerRadius, cornerRadius, borderPaint)
        }
    }

    private fun drawOverflow(canvas: Canvas, cell: Rect, more: Int) {
        slotRect.set(cell)
        canvas.drawRoundRect(slotRect, cellRadius, cellRadius, overflowShade)
        overflowText.textSize = cell.height() * OVERFLOW_TEXT_SIZE
        val baseline = slotRect.centerY() - (overflowText.descent() + overflowText.ascent()) / 2
        canvas.drawText("+$more", slotRect.centerX(), baseline, overflowText)
    }
}

/** The tray's four places, two by two inside its padding, into [bounds]. */
private fun layOutTray(bounds: List<Rect>, width: Int, height: Int) {
    val padding = (width * TRAY_PADDING).roundToInt()
    val gap = (width * CELL_GAP).roundToInt()
    val cellWidth = (width - 2 * padding - gap) / 2
    val cellHeight = (height - 2 * padding - gap) / 2
    val rightStart = width - padding - cellWidth
    val bottomStart = height - padding - cellHeight
    bounds[0].set(padding, padding, padding + cellWidth, padding + cellHeight)
    bounds[1].set(rightStart, padding, width - padding, padding + cellHeight)
    bounds[2].set(padding, bottomStart, padding + cellWidth, height - padding)
    bounds.last().set(rightStart, bottomStart, width - padding, height - padding)
}

/**
 * Puts the folder group mark ahead of a group tile's name, in the name's own colour, or takes it off
 * a tile that is no group - so a group reads as one in its label as well as its cover.
 */
fun TextView.showGroupMark(isGroup: Boolean) {
    if (!isGroup) {
        if (compoundDrawablesRelative.first() != null) {
            setCompoundDrawablesRelative(null, null, null, null)
        }

        return
    }

    val size = (textSize * GROUP_MARK_SCALE).roundToInt()
    val mark = AppCompatResources.getDrawable(context, R.drawable.ic_folder_group_vector)?.mutate() ?: return
    mark.setBounds(0, 0, size, size)
    mark.setTint(currentTextColor)
    setCompoundDrawablesRelative(mark, null, null, null)
    compoundDrawablePadding = (size * GROUP_MARK_GAP).roundToInt()
}
