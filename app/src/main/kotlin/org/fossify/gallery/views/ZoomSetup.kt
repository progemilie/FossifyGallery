package org.fossify.gallery.views

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.core.view.children
import androidx.core.view.isNotEmpty
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.signature.ObjectKey
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.databinding.ThumbnailSectionBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.GridSections
import org.fossify.gallery.helpers.GridShape
import org.fossify.gallery.helpers.GridZoom
import org.fossify.gallery.helpers.GridZoomLayout
import org.fossify.gallery.helpers.MediaWithoutHeaders
import org.fossify.gallery.helpers.ZoomScene
import org.fossify.gallery.helpers.forEachVisible
import org.fossify.gallery.helpers.ZoomThumbnails
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import kotlin.math.roundToInt

/** One zoom, from the fingers coming down to the grid taking over again. */
class ZoomSession(
    val drawing: ZoomDrawing,
    val rungs: List<Int>,
    val startRung: Int,
    /** Where the grid's content started along when the zoom began. */
    val startOrigin: Double,
    val viewport: ZoomScene.Viewport,
) {
    val scene get() = drawing.scene
    val frame get() = drawing.frame

    /** Zooms about [x], [y] in the grid's own coordinates - see [ZoomScene.focusOn]. */
    fun focusOn(x: Float, y: Float) = scene.focusOn(
        along = when {
            !frame.horizontal -> y
            frame.reversed -> frame.width - x
            else -> x
        },
        across = if (frame.horizontal) y else x
    )

    /**
     * The list position the grid scrolls to, and how far past its padding that item starts, for the
     * grid to lie exactly as the zoom has come to rest - the first header or row at the near padding.
     */
    fun restPosition(): Pair<Int, Int>? {
        val layer = scene.under
        val sections = layer.layout.sections
        val start = viewport.paddingStart
        val section = layer.sectionAt(start)
        if (sections.isHeaded(section) && (start < layer.rowStart(section, 0) || sections.size(section) == 0)) {
            return sections.headerPosition(section) to (layer.headerStart(section) - start).roundToInt()
        }

        val row = layer.rowAt(section, start)
        val position = layer.layout.positionAt(section, row, 0)
        return if (position < 0) null else position to (layer.rowStart(section, row) - start).roundToInt()
    }

    /** The media on screen once the zoom is at rest. */
    fun restingMedia(): List<Medium> {
        val layer = scene.under
        val items = drawing.counts.itemsOf(layer.rung)
        val media = ArrayList<Medium>()
        layer.forEachVisible(0f, frame.alongLength, onHeader = { _, _ -> }, onRow = { section, row, _ ->
            for (span in 0 until layer.layout.tilesInRow(section, row)) {
                (items.getOrNull(layer.layout.positionAt(section, row, span)) as? Medium)?.let(media::add)
            }
        })

        return media
    }
}

/**
 * Everything a zoom is drawn from, read off the grid in the moment its fingers come down: its size
 * and spacing, the lists and layouts of its counts, where its content lies, and the pictures its
 * tiles are showing. See MediaGridZoom.
 */
internal class ZoomSetup(
    private val grid: MyRecyclerView,
    private val overlay: View,
    private val host: MediaGridZoom.Host,
) {
    private val location = IntArray(2)
    private val overlayLocation = IntArray(2)

    /** A zoom around [focusX], [focusY], or null while the grid is in no state to be zoomed. */
    fun start(focusX: Float, focusY: Float, onPicture: () -> Unit): ZoomSession? {
        val adapter = host.adapter ?: return null
        val layoutManager = grid.layoutManager as? GridLayoutManager ?: return null
        val startRung = host.ladder.rungs.indexOf(layoutManager.spanCount)
        if (!isSettled() || startRung < 0) {
            return null
        }

        val frame = frameOf(layoutManager)
        val items = host.items
        val sections = GridSections.of(items)
        val header = if (!frame.horizontal && sections.isGrouped) measureHeader(adapter) else null
        val shape = shapeOf(frame, header?.first ?: 0)
        val counts = LadderCounts(host.ladder, items, sections, shape)
        val startOrigin = calibrate(counts.layoutOf(startRung), frame) ?: return null
        val viewport = viewportOf(frame)
        val scene = ZoomScene(host.ladder.rungs.size, viewport, counts::layoutOf, startRung, startOrigin)
        val thumbnails = ZoomThumbnails(grid.context, loaderOf(adapter, host.ladder), onPicture)
        thumbnails.borrow(layoutManager.spanCount, borrowPictures(adapter))
        val drawing = ZoomDrawing(scene, thumbnails, frame, header?.second, tileStyleOf(adapter), counts)
        return ZoomSession(drawing, host.ladder.rungs, startRung, startOrigin, viewport).also {
            it.focusOn(focusX, focusY)
        }
    }

    /** Whether the grid lies as its adapter says: laid out, and with no change waiting to be. */
    private fun isSettled() =
        grid.width > 0 && grid.isNotEmpty() && !grid.isComputingLayout && !grid.hasPendingAdapterUpdates()

    private fun tileStyleOf(adapter: MediaAdapter) = TileStyle(
        placeholderColor = grid.context.getColor(org.fossify.commons.R.color.md_grey_black),
        cornerRadius = adapter.thumbnailCornerRadius,
        padding = grid.context.config.thumbnailSpacing.takeIf { it <= 1 } ?: 0
    )

    /** Where the grid sits in the overlay, and which way round it runs. */
    private fun frameOf(layoutManager: GridLayoutManager): GridFrame {
        grid.getLocationInWindow(location)
        overlay.getLocationInWindow(overlayLocation)
        val horizontal = layoutManager.orientation == RecyclerView.HORIZONTAL
        return GridFrame(
            left = (location[0] - overlayLocation[0]).toFloat(),
            top = (location[1] - overlayLocation[1]).toFloat(),
            width = grid.width.toFloat(),
            height = grid.height.toFloat(),
            horizontal = horizontal,
            reversed = horizontal && grid.layoutDirection == View.LAYOUT_DIRECTION_RTL
        )
    }

    private fun shapeOf(frame: GridFrame, headerLength: Int) = GridShape(
        acrossSpace = if (frame.horizontal) {
            grid.height - grid.paddingTop - grid.paddingBottom
        } else {
            grid.width - grid.paddingLeft - grid.paddingRight
        },
        acrossPadding = if (frame.horizontal) grid.paddingTop else grid.paddingLeft,
        spacing = grid.context.config.thumbnailSpacing,
        sideSpacing = grid.context.config.fileRoundedCorners,
        horizontal = frame.horizontal,
        rtl = !frame.horizontal && grid.layoutDirection == View.LAYOUT_DIRECTION_RTL,
        headerLength = headerLength
    )

    private fun viewportOf(frame: GridFrame): ZoomScene.Viewport {
        val (start, end) = when {
            !frame.horizontal -> grid.paddingTop to grid.paddingBottom
            frame.reversed -> grid.paddingRight to grid.paddingLeft
            else -> grid.paddingLeft to grid.paddingRight
        }

        return ZoomScene.Viewport(frame.alongLength, start.toFloat(), end.toFloat())
    }

    /**
     * How long a header is and how its title is drawn. A header the grid has bound says best how long
     * one is; the title's place is read off one made to measure, as no bound header need be on screen.
     */
    private fun measureHeader(adapter: MediaAdapter): Pair<Int, HeaderStyle> {
        val sample = ThumbnailSectionBinding.inflate(LayoutInflater.from(grid.context), grid, false)
        // left without a title: a header holds one line whatever it says, and one line is measured
        sample.root.layoutDirection = grid.layoutDirection
        val width = grid.width - grid.paddingLeft - grid.paddingRight
        sample.root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        sample.root.layout(0, 0, width, sample.root.measuredHeight)

        val bound = grid.children.firstOrNull { it.findViewById<View>(R.id.thumbnail_section) != null }
        val text = sample.thumbnailSection
        val style = HeaderStyle(
            // the view only brings its paint round to a new colour as it draws
            paint = TextPaint(text.paint).apply { color = adapter.sectionTextColor },
            textStart = (grid.paddingLeft + text.left).toFloat(),
            textEnd = (grid.paddingLeft + text.right).toFloat(),
            baseline = (text.top + text.baseline).toFloat(),
            rtl = grid.layoutDirection == View.LAYOUT_DIRECTION_RTL
        )

        return (bound?.height ?: sample.root.measuredHeight) to style
    }

    /** Where the grid's content starts along, worked back from a child it has laid out. */
    private fun calibrate(layout: GridZoomLayout, frame: GridFrame): Double? {
        val layoutManager = grid.layoutManager ?: return null
        val sections = layout.sections
        val headerLength = layout.shape.headerLength
        for (child in grid.children) {
            val position = grid.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) {
                continue
            }

            val section = sections.sectionOf(position)
            val offset = if (sections.isHeaded(section) && position == sections.headerPosition(section)) {
                layout.sectionRowStart(section) + headerLength * sections.headersBefore(section)
            } else {
                val row = (position - sections.firstMedium(section)) / layout.columns
                layout.sectionRowStart(section) + layout.rowOffset(section, row) +
                    headerLength * sections.headersThrough(section)
            }

            val start = when {
                !frame.horizontal -> layoutManager.getDecoratedTop(child)
                frame.reversed -> grid.width - layoutManager.getDecoratedRight(child)
                else -> layoutManager.getDecoratedLeft(child)
            }

            return start.toDouble() - offset
        }

        return null
    }

    /** The pictures the grid's tiles are showing, by path - none that are only a placeholder. */
    private fun borrowPictures(adapter: MediaAdapter): Map<String, Drawable> {
        val pictures = HashMap<String, Drawable>()
        for (child in grid.children) {
            val medium = adapter.media.getOrNull(grid.getChildAdapterPosition(child)) as? Medium ?: continue
            val picture = child.findViewById<ImageView>(R.id.medium_thumbnail)?.drawable
            if (picture != null && picture !is ColorDrawable) {
                pictures[medium.path] = picture
            }
        }

        return pictures
    }

    private fun loaderOf(adapter: MediaAdapter, ladder: GridZoom) = object : ZoomThumbnails.Loader {
        // asked once a tile a frame, and working a key out takes a logarithm
        private val keys = SparseIntArray()

        // a medium whose scan left no date has its file asked for one each time, and a zoom may
        // ask for one medium at several sizes
        private val signatures = HashMap<String, ObjectKey>()

        override fun keyOf(columnCount: Int): Int {
            val index = keys.indexOfKey(columnCount)
            if (index >= 0) {
                return keys.valueAt(index)
            }

            val key = adapter.thumbnailKeyAt(columnCount, ladder.isSimplified(columnCount))
            keys.put(columnCount, key)
            return key
        }

        override fun load(medium: Medium, columnCount: Int, target: CustomTarget<Drawable>) =
            adapter.loadThumbnailAt(
                medium = medium,
                signature = signatures.getOrPut(medium.path) { medium.getKey() },
                columnCount = columnCount,
                simplified = ladder.isSimplified(columnCount),
                target = target
            )
    }
}

/**
 * The ladder's counts as a zoom draws them: each one's list, and its layout worked out once. The
 * simplified counts' list is the full one read without its headers.
 */
private class LadderCounts(
    private val ladder: GridZoom,
    private val interactive: List<ThumbnailItem>,
    private val sections: GridSections,
    private val shape: GridShape,
) : ZoomCounts {
    private val simplified = MediaWithoutHeaders(interactive, sections)
    private val simplifiedSections = sections.withoutHeaders()
    private val layouts = HashMap<Int, GridZoomLayout>()

    override fun itemsOf(rung: Int) = if (isSimplified(rung)) simplified else interactive

    override fun columnsOf(rung: Int) = ladder.rungs[rung]

    override fun isSimplified(rung: Int) = ladder.isSimplified(ladder.rungs[rung])

    fun layoutOf(rung: Int) = layouts.getOrPut(rung) {
        GridZoomLayout(columnsOf(rung), shape, if (isSimplified(rung)) simplifiedSections else sections)
    }
}
