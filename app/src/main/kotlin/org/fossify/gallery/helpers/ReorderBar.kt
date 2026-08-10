package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getBottomNavigationBackgroundColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.fossify.gallery.databinding.MediaReorderBarBinding

/**
 * The bar that takes the navigation bar's place while a folder is being arranged by hand: send the
 * marked items to either end, then keep the arrangement or drop it.
 *
 * Painting and enabling itself is its own business rather than the grid screen's - the only thing
 * that screen has to say is what the four buttons do.
 */
class ReorderBar(private val binding: MediaReorderBarBinding) {
    /** The bar itself, for the inset handling that has to know where it sits. */
    val root get() = binding.root

    var onMoveToEdge: ((toTop: Boolean) -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var onSave: (() -> Unit)? = null

    init {
        binding.apply {
            reorderMoveToTop.setOnClickListener { onMoveToEdge?.invoke(true) }
            reorderMoveToBottom.setOnClickListener { onMoveToEdge?.invoke(false) }
            reorderCancel.setOnClickListener { onCancel?.invoke() }
            reorderSave.setOnClickListener { onSave?.invoke() }
        }

        updateColors()
    }

    fun show() = root.beVisible()

    fun hide() = root.beGone()

    /**
     * Paints the bar in the current theme - it is built once but the colors can change under it, so
     * this runs again on every resume. Save is filled with the accent color to read as the action
     * that ends the arrangement, Cancel only gets an outline to stay the quieter of the two.
     */
    fun updateColors() {
        val context = root.context
        val textColor = context.getProperTextColor()
        val primaryColor = context.getProperPrimaryColor()
        binding.apply {
            root.backgroundTintList = ColorStateList.valueOf(context.getBottomNavigationBackgroundColor())
            reorderMoveToTop.applyColorFilter(textColor)
            reorderMoveToBottom.applyColorFilter(textColor)

            reorderCancel.setTextColor(textColor)
            reorderCancel.strokeColor = ColorStateList.valueOf(textColor.adjustAlpha(MEDIUM_ALPHA))
            reorderCancel.rippleColor = ColorStateList.valueOf(textColor.adjustAlpha(LOWER_ALPHA))

            val onPrimaryColor = primaryColor.getContrastColor()
            reorderSave.backgroundTintList = ColorStateList.valueOf(primaryColor)
            reorderSave.setTextColor(onPrimaryColor)
            reorderSave.rippleColor = ColorStateList.valueOf(onPrimaryColor.adjustAlpha(LOWER_ALPHA))
        }
    }

    /**
     * The two send-to-an-end buttons have nothing to act on until something is marked, so they stay
     * in place but dimmed until then rather than appearing and moving the rest of the bar along.
     */
    fun setMarkedCount(markedCount: Int) {
        val canMove = markedCount > 0
        listOf(binding.reorderMoveToTop, binding.reorderMoveToBottom).forEach {
            it.isEnabled = canMove
            it.alpha = if (canMove) 1f else MEDIUM_ALPHA
        }
    }
}
