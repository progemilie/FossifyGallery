package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.BlurViewFacade
import org.fossify.gallery.helpers.Glass

/**
 * A panel of the app's frosted glass ([Glass]): rounded, and filled with a blurred copy of whatever
 * is drawn behind it. [frost] is what points it at the content to copy - until that is called, and
 * always where the platform has no cheap blur to offer, it paints itself flat in the colour the blur
 * would have been tinted with.
 */
open class GlassPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BlurView(context, attrs, defStyleAttr) {

    private var backdrop: BlurViewFacade? = null

    /** How hard this panel frosts what is behind it. */
    var blurRadius = Glass.DEFAULT_RADIUS
        set(value) {
            field = value
            backdrop?.setBlurRadius(value)
        }

    /** Rounds the panel, the blur inside it and the shadow it casts alike. */
    var cornerRadius = 0f
        set(value) {
            field = value
            invalidateOutline()
            (background as? GradientDrawable)?.cornerRadius = value
        }

    /** Stops the copying for a panel that is still on screen but not to be looked at. */
    var isFrostPaused = false
        set(value) {
            field = value
            syncAutoUpdate()
        }

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }

        clipToOutline = true
    }

    /**
     * The panel is about to be seen, for content that has to be repainted along with it. Never
     * called while the panel is still being built, so a subclass can reach its own fields here.
     */
    protected open fun onGlassShown() = Unit

    /** Starts frosting whatever [contentBehind] draws under this panel. */
    fun frost(contentBehind: ViewGroup) {
        if (backdrop != null || !Glass.isSupported()) {
            return
        }

        backdrop = setupWith(contentBehind).setBlurRadius(blurRadius)
        updateColors()
        syncAutoUpdate()
    }

    /** Re-reads the theme, which is the only place any of these colours come from. */
    fun updateColors() {
        val backdrop = backdrop
        if (backdrop == null) {
            background = GradientDrawable().also {
                it.cornerRadius = cornerRadius
                it.setColor(Glass.flatFill(context))
            }

            return
        }

        // the blurred copy goes down before the panel paints itself, so a background would bury it
        background = null
        backdrop
            // a grid paints nothing of its own between the thumbnails, so a capture that starts from
            // a cleared buffer comes back transparent everywhere there is no photo - over a date
            // header, over the gaps between cells, over the half of a panel hanging past the end of a
            // row. Blurring transparency leaves transparency, and the real content shows straight
            // through it, unblurred and sharp. Starting each capture from the background the content
            // sits on is what makes a panel frost everything under it and not just the photos.
            .setFrameClearDrawable(SolidColorDrawable(Glass.baseColor(context)))
            // the frame clear above stands in for that background and stays the real colour, so the
            // lift rides on the tint instead - the panel parts company with the app behind it without
            // the frost parting company with the content it is a copy of
            .setOverlayColor(Glass.overlay(context))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateColors()
        syncAutoUpdate()
    }

    // reached from View's own constructor for a panel laid out GONE, which is why nothing here may
    // touch a subclass. isShown() is false throughout that, and for an INVISIBLE panel being placed.
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isShown) {
            // panels that spend most of their life hidden are worth a fresh look at the theme
            updateColors()
            onGlassShown()
        }

        syncAutoUpdate()
    }

    // a blur is a copy of the screen every frame, so it is only ever kept up while on screen
    private fun syncAutoUpdate() {
        backdrop?.setBlurAutoUpdate(isShown && !isFrostPaused)
    }
}

/**
 * Floods whatever canvas it is handed, whatever bounds it was set. BlurView hands its capture buffer
 * over untransformed and without ever setting bounds on this, which a plain ColorDrawable would
 * answer by painting nothing at all.
 */
private class SolidColorDrawable(private val color: Int) : Drawable() {
    override fun draw(canvas: Canvas) = canvas.drawColor(color)

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("abstract on Drawable, so it has to be answered whatever its own docs say")
    override fun getOpacity() = PixelFormat.OPAQUE
}
