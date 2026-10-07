package org.fossify.gallery.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.fossify.commons.extensions.beGone
import org.fossify.gallery.R
import org.fossify.gallery.helpers.Perf
import org.fossify.gallery.helpers.SearchOptions
import org.fossify.gallery.models.SearchFilter

/** How dark the grid goes behind the options. */
private const val DIM_ALPHA = 0.55f
private const val DIM_ALPHA_NO_BLUR = 0.75f

/** Below this a blur is not worth the offscreen pass it costs. */
private const val MIN_BLUR_PX = 0.5f

/**
 * What an open search puts up under the bar: the grid dimmed behind it, and the options it can be
 * narrowed by. Built into the screen just above the content the glass copies, so the search pill is
 * neither dimmed nor frosting the dim - it looks exactly as it did. [org.fossify.gallery.helpers.SearchChrome]
 * moves this, the blur and the bar's edge together.
 */
class SearchOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // takes every touch: the grid is not to be reached under it
    private val dim = View(context).apply {
        setBackgroundColor(Color.BLACK)
        isClickable = true
    }

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    /** The options, which slide and fade in as one. */
    val options = ScrollView(context).apply {
        clipToPadding = false
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(column)
    }

    private val sections = SearchOverlaySections(column)
    private val footRoom = resources.getDimensionPixelSize(R.dimen.search_options_foot_room)
    private val blurRadius = resources.getDimension(R.dimen.search_blur_radius)

    /** The content under the overlay, which the dim blurs as well as darkens. */
    var blurred: ViewGroup? = null

    /** Whether the dim may blur at all: the platform can, and the Glass UI setting is on. */
    var canBlur = false

    var onChosen: ((SearchFilter) -> Unit)?
        get() = sections.onChosen
        set(value) {
            sections.onChosen = value
        }

    /** A tap no pill takes: on the dim, or anywhere around and between the options. */
    var onDimTapped: (() -> Unit)? = null

    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (!sections.hasPillAt(e.rawX, e.rawY)) {
                onDimTapped?.invoke()
            }

            return false
        }
    })

    private var dimming: ValueAnimator? = null

    // fading away, nothing on the overlay may be pressed: touches go through to the grid it uncovers
    var isLeaving = false
        private set

    /** How far the dim, and the blur with it, has come in, 0 to 1. */
    var dimLevel = 0f
        set(value) {
            field = value
            dim.alpha = value * if (canBlur) DIM_ALPHA else DIM_ALPHA_NO_BLUR
            blur(value)
        }

    init {
        addView(dim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(options, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val side = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        options.setPaddingRelative(side, 0, side, footRoom)
        dimLevel = 0f

        // the keyboard takes the bottom of the screen, so whatever does not fit above it scrolls
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.navigationBars())
            options.updatePadding(bottom = bottom.bottom + footRoom)
            insets
        }
    }

    /** Puts the options below [height] of bar - which already carries the status bar inset. */
    fun keepClearOfBar(height: Int) {
        options.updatePadding(top = height)
    }

    /** Takes the dim to [level] from wherever it is, dropping whatever it was doing for this. */
    fun dimTo(level: Float, duration: Long, curve: Interpolator): ValueAnimator {
        dimming?.cancel()
        isLeaving = level == 0f
        return ValueAnimator.ofFloat(dimLevel, level).apply {
            this.duration = duration
            interpolator = curve
            addUpdateListener { dimLevel = it.animatedValue as Float }
            dimming = this
            start()
        }
    }

    /** Fills the options in from [options], [active] lit as the filter already on. */
    fun fill(options: SearchOptions, active: SearchFilter?) {
        Perf.section("search.fill") { sections.fill(options, active) }
        this.options.scrollTo(0, 0)
    }

    /** Takes the faded overlay off the screen, and its pills with it: unseen, they are not to be pressed. */
    fun hide() {
        beGone()
        sections.clear()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // a gesture that began while the overlay was staying is let finish
        if (isLeaving && event.actionMasked == MotionEvent.ACTION_DOWN) {
            return false
        }

        taps.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    private fun blur(level: Float) {
        val content = blurred
        if (content == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }

        val radius = if (canBlur) level * blurRadius else 0f
        content.setRenderEffect(
            if (radius < MIN_BLUR_PX) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        )
    }
}
