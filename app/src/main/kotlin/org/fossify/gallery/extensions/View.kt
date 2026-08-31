package org.fossify.gallery.extensions

import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import org.fossify.commons.extensions.toast
import org.fossify.gallery.helpers.ViewerTransition

fun View.sendFakeClick(x: Float, y: Float) {
    val uptime = SystemClock.uptimeMillis()
    val event = MotionEvent.obtain(uptime, uptime, MotionEvent.ACTION_DOWN, x, y, 0)
    dispatchTouchEvent(event)
    event.action = MotionEvent.ACTION_UP
    dispatchTouchEvent(event)
}

fun View.showContentDescriptionOnLongClick() {
    setOnLongClickListener {
        val contentDescription = contentDescription
        if (contentDescription != null) {
            context.toast(contentDescription.toString())
        }
        true
    }
}

/** Where this view's top left corner sits on the display. */
fun View.screenLocation(): Pair<Float, Float> {
    val location = IntArray(2)
    getLocationOnScreen(location)
    return location[0].toFloat() to location[1].toFloat()
}

/**
 * This view's bounds on the display - the one space a grid's window and the viewer's have in
 * common, and so what a continuity flight is measured in. See
 * [org.fossify.gallery.helpers.ViewerTransition].
 */
fun View.screenRect(): RectF {
    val (left, top) = screenLocation()
    return RectF(left, top, left + width, top + height)
}

/**
 * Where this view is actually drawing its picture on screen, which is smaller than the view itself
 * wherever the picture does not fill it. Null when there is nothing drawn yet.
 *
 * A view left to fit its own picture is measured from the picture's proportions; one driven by a
 * matrix - which is how [com.alexvasilkov.gestures.views.GestureImageView] pans and zooms - is
 * measured through that matrix, since the picture may have been moved anywhere inside the view.
 */
fun ImageView.displayedImageRect(): RectF? {
    val drawable = drawable ?: return null
    val width = drawable.intrinsicWidth.toFloat()
    val height = drawable.intrinsicHeight.toFloat()
    if (width <= 0f || height <= 0f) {
        return null
    }

    val content = RectF(
        paddingLeft.toFloat(),
        paddingTop.toFloat(),
        (this.width - paddingRight).toFloat(),
        (this.height - paddingBottom).toFloat()
    )

    if (content.isEmpty) {
        return null
    }

    // an identity matrix is one that has not been positioned yet rather than one saying "unmoved",
    // and taken at face value it puts the picture at its own full pixel size in the corner
    val isPositioned = scaleType == ImageView.ScaleType.MATRIX && !imageMatrix.isIdentity
    val drawn = if (isPositioned) {
        RectF(0f, 0f, width, height).apply { imageMatrix.mapRect(this) }
            .apply { offset(content.left, content.top) }
    } else {
        ViewerTransition.restingRect(width / height, content)
    }

    val (screenLeft, screenTop) = screenLocation()
    return drawn.apply { offset(screenLeft, screenTop) }
}
