package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import org.fossify.gallery.R
import org.fossify.gallery.helpers.MAX_FOLDER_GROUP_COVERS

// how many members each layout draws, named so the cell arithmetic below reads as a shape
private const val ONE_FULL = 1
private const val TWO_COLUMNS = 2
private const val SPLIT_LEFT_WHOLE_RIGHT = 3
private const val HALVES = 2

/**
 * A folder group's cover: the group's leading folders drawn as one tile.
 *
 * One member fills it, two split it down the middle, three split the left half and leave the right
 * whole, four make a 2x2. Members past the fourth are not drawn - the tile says how many there are
 * in its label instead.
 *
 * The cells are plain ImageViews the caller loads into, so a cell costs exactly what the folder's
 * own thumbnail would have; the rounded corners are cut once here, by clipping the whole tile,
 * rather than per cell.
 */
class FolderGroupThumbnail @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val gap = resources.getDimensionPixelSize(R.dimen.folder_group_cell_gap)
    private val borderWidth = resources.getDimension(R.dimen.folder_group_border_width)
    private val bounds = List(MAX_FOLDER_GROUP_COVERS) { Rect() }
    private val borderRect = RectF()

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
    }

    private val cells = List(MAX_FOLDER_GROUP_COVERS) {
        ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    }

    private var shownCells = 0
    private var cornerRadius = 0f

    init {
        cells.forEach { addView(it) }
        setWillNotDraw(false)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
    }

    /**
     * Readies the tile for [count] members and hands back the cells to load them into, in the
     * order they should appear. Cells past [count] are hidden and left holding nothing.
     */
    fun prepareCells(count: Int, cellBackground: Int): List<ImageView> {
        shownCells = count.coerceIn(0, MAX_FOLDER_GROUP_COVERS)
        cells.forEachIndexed { index, cell ->
            if (index < shownCells) {
                cell.visibility = VISIBLE
                cell.setImageDrawable(null)
                cell.setBackgroundResource(cellBackground)
            } else {
                cell.visibility = GONE
                cell.setImageDrawable(null)
            }
        }

        requestLayout()
        return cells.take(shownCells)
    }

    /** Hands every cell to [clear] so a recycled tile drops the image requests it had going. */
    fun clearCells(clear: (ImageView) -> Unit) {
        cells.forEach(clear)
    }

    fun setCornerRadius(radius: Float) {
        if (cornerRadius != radius) {
            cornerRadius = radius
            invalidateOutline()
            invalidate()
        }
    }

    fun setBorderColor(color: Int) {
        if (borderPaint.color != color) {
            borderPaint.color = color
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        layOutCells(width, height)

        cells.forEachIndexed { index, cell ->
            if (index < shownCells) {
                val rect = bounds[index]
                cell.measure(
                    MeasureSpec.makeMeasureSpec(rect.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(rect.height(), MeasureSpec.EXACTLY)
                )
            }
        }

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        cells.forEachIndexed { index, cell ->
            if (index < shownCells) {
                val rect = bounds[index]
                cell.layout(rect.left, rect.top, rect.right, rect.bottom)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (shownCells == 0 || borderPaint.color == 0) {
            return
        }

        // inset by half the stroke so the whole of it lands inside the clip rather than half of it
        // being cut away with the corners
        val inset = borderWidth / HALVES
        borderRect.set(inset, inset, width - inset, height - inset)
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)
    }

    private fun layOutCells(width: Int, height: Int) {
        val midX = (width - gap) / HALVES
        val midY = (height - gap) / HALVES
        val rightStart = midX + gap
        val bottomStart = midY + gap

        when (shownCells) {
            ONE_FULL -> bounds[0].set(0, 0, width, height)

            TWO_COLUMNS -> {
                bounds[0].set(0, 0, midX, height)
                bounds[1].set(rightStart, 0, width, height)
            }

            SPLIT_LEFT_WHOLE_RIGHT -> {
                bounds[0].set(0, 0, midX, midY)
                bounds[1].set(0, bottomStart, midX, height)
                bounds[2].set(rightStart, 0, width, height)
            }

            MAX_FOLDER_GROUP_COVERS -> {
                bounds[0].set(0, 0, midX, midY)
                bounds[1].set(rightStart, 0, width, midY)
                bounds[2].set(0, bottomStart, midX, height)
                bounds.last().set(rightStart, bottomStart, width, height)
            }
        }
    }
}
