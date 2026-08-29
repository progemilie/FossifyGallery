package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
 * wherever the glass is off (the Glass UI setting, or a platform with no cheap blur to offer), it
 * paints itself flat in the colour the blur would have been tinted with.
 */
open class GlassPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BlurView(context, attrs, defStyleAttr) {

    private var backdrop: BlurViewFacade? = null

    /**
     * What this panel is a copy of, once [frost] has been told. Reachable by a panel that keeps a
     * second one of its own beside it, which has to be pointed at the same content.
     */
    protected var contentBehind: ViewGroup? = null
        private set

    // whether the blur is actually running; false leaves the panel a flat fill
    private var isFrosted = false

    // read once per theme rather than per frame, since draw() is on the scrolling path
    private var flatFill = Color.TRANSPARENT

    /** How hard this panel frosts what is behind it. */
    var blurRadius = Glass.DEFAULT_RADIUS
        set(value) {
            field = value
            backdrop?.setBlurRadius(value)
        }

    /** How thick the wash over the blur is. See [Glass.overlay]. */
    var overlayAlpha = Glass.TINT_ALPHA
        set(value) {
            field = value
            updateColors()
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

    /** Starts frosting whatever [contentBehind] draws under this panel. Safe to call again. */
    fun frost(contentBehind: ViewGroup) {
        this.contentBehind = contentBehind
        syncGlass()
    }

    /**
     * Brings the panel in line with the Glass UI setting, which the user can turn off long after the
     * panel was built. A blur cannot be taken back out of a BlurView once it is set up, so it is
     * switched off instead and the panel falls back to the flat fill it wears on older platforms.
     */
    private fun syncGlass() {
        val contentBehind = contentBehind
        val enabled = Glass.isEnabled(context)
        if (enabled && backdrop == null && contentBehind != null) {
            backdrop = setupWith(contentBehind).setBlurRadius(blurRadius)
        }

        isFrosted = enabled && backdrop != null
        backdrop?.setBlurEnabled(isFrosted)
        updateColors()
        syncAutoUpdate()
    }

    /** Re-reads the theme, which is the only place any of these colours come from. */
    fun updateColors() {
        flatFill = Glass.flatFill(context)

        val backdrop = backdrop
        if (!isFrosted || backdrop == null) {
            background = GradientDrawable().also {
                it.cornerRadius = cornerRadius
                it.setColor(flatFill)
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
            .setOverlayColor(Glass.overlay(context, overlayAlpha))
    }

    /**
     * A copy can only hold what the platform will redraw into a bitmap, and a video's surface is not
     * that - the copy comes back with a hole punched where it was, and the panel would be a window
     * onto the sharp, unblurred thing it is supposed to be frosting. Laying the flat fill down first
     * means anything the copy could not reach is simply a panel without a blur behind it.
     *
     * Only ever onto the screen: a copy is taken into a software canvas, and painting the fill there
     * would feed the panel its own colour back frame after frame until nothing else was left of it.
     */
    override fun draw(canvas: Canvas) {
        if (isFrosted && canvas.isHardwareAccelerated) {
            canvas.drawColor(flatFill)
        }

        super.draw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncGlass()
    }

    // reached from View's own constructor for a panel laid out GONE, which is why nothing here may
    // touch a subclass. isShown() is false throughout that, and for an INVISIBLE panel being placed.
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isShown) {
            // panels that spend most of their life hidden are worth a fresh look at the theme and at
            // the setting, either of which may have changed since they were last up
            syncGlass()
            onGlassShown()
            return
        }

        syncAutoUpdate()
    }

    // a blur is a copy of the screen every frame, so it is only ever kept up while on screen
    private fun syncAutoUpdate() {
        backdrop?.setBlurAutoUpdate(isFrosted && isShown && !isFrostPaused)
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
