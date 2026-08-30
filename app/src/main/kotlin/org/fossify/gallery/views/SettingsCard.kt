package org.fossify.gallery.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.core.content.res.use
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.R
import org.fossify.gallery.databinding.SettingsCardHeaderBinding
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.helpers.PANEL_ENTER_MS

/**
 * One section of the settings screen: a title and a line saying what is inside it, until it is
 * tapped and the settings themselves take that line's place.
 *
 * A card's rows are simply its children in the layout - [onFinishInflate] moves them into a holder
 * of its own under the header - so activity_settings.xml still reads as the list of settings it is,
 * and a section costs no more than the tag put around it.
 */
class SettingsCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = SettingsCardHeaderBinding.inflate(LayoutInflater.from(context), this, false)

    private val rows = SettingsRows(context).apply { beGone() }

    /** This card's settings, for a screen that paints them only once they can be seen. */
    val settings: ViewGroup get() = rows

    private var heightAnimator: ValueAnimator? = null

    /** Told when this card opens, for a screen that lets only one of them be open at a time. */
    var onOpened: ((SettingsCard) -> Unit)? = null

    /**
     * Told once the card has finished opening and everything on the screen has settled where it is
     * going to be - the card that shut to make room for it included. For anything that has to work
     * from where the card actually ended up rather than from where it started.
     */
    var onOpenSettled: ((SettingsCard) -> Unit)? = null

    var isOpen = false
        private set

    init {
        orientation = VERTICAL
        elevation = resources.getDimension(R.dimen.settings_card_elevation)
        clipToOutline = true
        background = GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.settings_card_corner_radius)
        }

        context.obtainStyledAttributes(attrs, R.styleable.SettingsCard).use {
            binding.settingsCardTitle.text = it.getString(R.styleable.SettingsCard_cardTitle)
            binding.settingsCardDescription.text = it.getString(R.styleable.SettingsCard_cardDescription)
            binding.settingsCardIcon.setImageResource(it.getResourceId(R.styleable.SettingsCard_cardIcon, 0))
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        val settings = children.toList()
        removeAllViews()
        addView(binding.root)
        settings.forEach { rows.addView(it) }
        addView(rows, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        binding.settingsCardHeader.setOnClickListener { toggle() }
    }

    /** Re-reads the theme. Called on every resume, since the colours can change while this is up. */
    fun updateColors() {
        (background as? GradientDrawable)?.setColor(Glass.tint(context))
        binding.settingsCardTitle.setTextColor(context.getProperPrimaryColor())
        // painted with the title rather than with the rows, the two of them being the one heading
        binding.settingsCardIcon.applyColorFilter(context.getProperPrimaryColor())
        binding.settingsCardDescription.setTextColor(context.getProperTextColor())
        binding.settingsCardChevron.applyColorFilter(context.getProperTextColor())
        rows.lineColor = context.getProperTextColor().adjustAlpha(DIVIDER_ALPHA)
    }

    fun toggle() {
        setOpen(!isOpen)
        if (isOpen) {
            onOpened?.invoke(this)
        }
    }

    fun close() = setOpen(false)

    private fun setOpen(open: Boolean) {
        if (isOpen == open) {
            return
        }

        isOpen = open
        binding.settingsCardChevron.animate()
            .rotation(if (open) TURNED else 0f)
            .setDuration(PANEL_ENTER_MS)
            .start()

        // description and rows swap outright rather than fading past each other: what is animated is
        // the card's own height, and a line still drawing where the card has already grown past
        // reads as a second thing moving
        binding.settingsCardDescription.beVisibleIf(!open)
        // the rows bring padding of their own, so the header gives its bottom back to them
        binding.settingsCardHeader.updatePadding(
            bottom = if (open) 0 else resources.getDimensionPixelSize(org.fossify.commons.R.dimen.normal_margin)
        )

        rows.beVisibleIf(open)
        rows.alpha = if (open) 0f else 1f
        rows.animate().alpha(if (open) 1f else 0f).setDuration(PANEL_ENTER_MS).start()
        animateHeight()
    }

    /**
     * Grows or shrinks the card to fit whatever it is now showing. Its own height rather than the
     * rows' - the description leaving with them is a height change too, and one animation covering
     * both is what keeps the title still while everything under it moves.
     */
    private fun animateHeight() {
        heightAnimator?.cancel()
        val from = height
        if (from == 0 || width == 0) {
            return
        }

        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        heightAnimator = ValueAnimator.ofInt(from, measuredHeight).apply {
            duration = PANEL_ENTER_MS
            interpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in)
            addUpdateListener { setFixedHeight(it.animatedValue as Int) }
            // the height is let go of again at the end, or the card could never answer a row of its
            // own changing size - a switch turning a second row on underneath it, say
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    setFixedHeight(LayoutParams.WRAP_CONTENT)
                    // the draw after the layout the line above asks for, by which point the card
                    // that shut for this one has finished shrinking and moved everything with it
                    if (isOpen) {
                        doOnPreDraw { onOpenSettled?.invoke(this@SettingsCard) }
                    }
                }
            })

            start()
        }
    }

    private fun setFixedHeight(value: Int) = updateLayoutParams<ViewGroup.LayoutParams> { height = value }

    private companion object {
        /** How far the chevron turns over to say the card is open. */
        const val TURNED = 180f

        /** How much of the text colour is left in the rule between two settings. */
        const val DIVIDER_ALPHA = 0.12f
    }
}

/**
 * The rows of one card, with a hairline between them. Drawn rather than laid out: a divider view
 * between every pair would add its own height to a card whose opening is animated to the pixel, and
 * these are meant to cost nothing. Inset to where the labels start, so the column of text is what
 * the rules line up with.
 */
private class SettingsRows(context: Context) : LinearLayout(context) {
    private val inset = resources
        .getDimensionPixelSize(org.fossify.commons.R.dimen.settings_label_start_margin).toFloat()
    private val paint = Paint().apply {
        strokeWidth = resources.getDimension(R.dimen.settings_card_divider_thickness)
    }

    /** Set rather than themed, the card being the one place any of its colours are read. */
    var lineColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    init {
        orientation = VERTICAL
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val shown = children.filter { it.isVisible }.toList()
        // sat just inside each row's foot rather than on the boundary, where half of a hairline
        // would fall outside the last row and be clipped away with it
        val half = paint.strokeWidth / 2f
        shown.dropLast(1).forEach {
            canvas.drawLine(inset, it.bottom - half, width - inset, it.bottom - half, paint)
        }
    }
}

/**
 * Lets only one of these be open at a time: opening one shuts whichever was. [alsoOnOpen] is
 * anything the screen has to do about the card that just opened, run before its first frame.
 */
fun List<SettingsCard>.makeAccordion(alsoOnOpen: (SettingsCard) -> Unit = {}) = forEach { card ->
    card.onOpened = { opened ->
        forEach { if (it !== opened) it.close() }
        alsoOnOpen(opened)
    }
}
