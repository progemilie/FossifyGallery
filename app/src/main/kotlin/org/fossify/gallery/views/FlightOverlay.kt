package org.fossify.gallery.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.withClip
import androidx.core.view.isInvisible
import org.fossify.gallery.extensions.screenLocation
import kotlin.math.max
import kotlin.math.min

/**
 * The picture in flight between a grid tile and the fullscreen photo - the one thing on screen
 * while a flight is running, drawn over the viewer with the grid still visible behind it. It draws
 * a bitmap into a rect rather than being one, so a flight costs no layout at all. See
 * [org.fossify.gallery.helpers.TileFlight].
 *
 * **Two things move at once, and both have to.** The rect travels from the tile to where the photo
 * comes to rest, and the crop travels with it - a flight moving only the rect would draw a cropped
 * square at one end or a letterboxed photo at the other, which is what leaves a cut at the end.
 *
 * Never GONE, only INVISIBLE: a flight is set up against this view's own position on screen, and a
 * GONE view is never laid out to have one.
 */
class FlightOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var image: Bitmap? = null

    private val from = RectF()
    private val to = RectF()
    private val current = RectF()
    private val drawMatrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * How much of a crop the picture is wearing this instant: 1 fills the rect the way a cropped
     * tile does, 0 fits inside it the way the fullscreen photo does.
     */
    private var crop = 0f

    /** What the crop runs between, and from where along the flight - see [handOver]. */
    private var cropStart = 0f
    private var cropEnd = 0f
    private var cropPickedUpAt = 0f

    /** How far along the flight is, 0 at [from] and 1 at [to]. */
    var progress = 0f
        set(value) {
            field = value
            lerpBounds()
            invalidate()
        }

    /**
     * Puts a picture up to fly between two screen rects. Both are in screen coordinates, the only
     * space the grid's window and the viewer's have in common; they are moved into this view's own
     * here, which is why it must already be laid out.
     *
     * [cropAtStart] and [cropAtEnd] say how the picture fills the rect at either end: a cropped
     * tile growing into a photo unfolds 1 to 0, a photo shrinking back into one folds 0 to 1, and
     * an uncropped grid leaves both at 0 because nothing about the picture changes on the way.
     */
    fun fly(image: Bitmap, fromRect: RectF, toRect: RectF, cropAtStart: Float, cropAtEnd: Float) {
        this.image = image
        from.set(fromRect.inThisView())
        to.set(toRect.inThisView())
        cropStart = cropAtStart
        cropEnd = cropAtEnd
        cropPickedUpAt = 0f

        alpha = 1f
        isInvisible = false
        progress = 0f
    }

    /**
     * Moves where the flight is heading without disturbing where it has got to, and hands it the
     * picture it should have been drawn with all along - a flight that set off before its picture
     * had decoded flies with the tile's own until then. The unfolding waits for the real picture
     * and then runs over whatever is left of the flight, rather than jumping to where it would be.
     */
    fun handOver(image: Bitmap, toRect: RectF, cropAtEnd: Float) {
        this.image = image
        to.set(toRect.inThisView())
        // picked up from wherever the crop has got to, so the change of picture moves nothing
        cropStart = crop
        cropEnd = cropAtEnd
        cropPickedUpAt = progress
        lerpBounds()
        invalidate()
    }

    /** Moves where the flight is heading, keeping the picture it already has. */
    fun retarget(toRect: RectF) {
        val moved = toRect.inThisView()
        if (moved != to) {
            to.set(moved)
            lerpBounds()
            invalidate()
        }
    }

    /** Where the picture is being drawn this instant, in screen coordinates. */
    fun currentRect(): RectF {
        val (screenLeft, screenTop) = screenLocation()
        return RectF(current).apply { offset(screenLeft, screenTop) }
    }

    fun clear() {
        isInvisible = true
        image = null
    }

    private fun RectF.inThisView(): RectF {
        val (screenLeft, screenTop) = screenLocation()
        return RectF(this).apply { offset(-screenLeft, -screenTop) }
    }

    private fun lerpBounds() {
        current.set(
            from.left + (to.left - from.left) * progress,
            from.top + (to.top - from.top) * progress,
            from.right + (to.right - from.right) * progress,
            from.bottom + (to.bottom - from.bottom) * progress
        )

        // measured from wherever the crop was picked up rather than from the start of the flight,
        // which is what lets a stand-in picture hand over mid-flight without anything jumping
        val remaining = 1f - cropPickedUpAt
        val along = if (remaining <= 0f) 1f else ((progress - cropPickedUpAt) / remaining).coerceIn(0f, 1f)
        crop = cropStart + (cropEnd - cropStart) * along
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = image ?: return
        if (current.isEmpty || bitmap.width == 0 || bitmap.height == 0) {
            return
        }

        // covering the rect and fitting inside it are the same transform at two scales, so the
        // crop unfolds by moving between them rather than by trading one for the other
        val cover = max(current.width() / bitmap.width, current.height() / bitmap.height)
        val fit = min(current.width() / bitmap.width, current.height() / bitmap.height)
        val scale = fit + (cover - fit) * crop

        drawMatrix.setScale(scale, scale)
        drawMatrix.postTranslate(
            current.centerX() - bitmap.width * scale / 2,
            current.centerY() - bitmap.height * scale / 2
        )

        canvas.withClip(current) { drawBitmap(bitmap, drawMatrix, paint) }
    }
}
