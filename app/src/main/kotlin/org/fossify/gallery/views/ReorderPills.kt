package org.fossify.gallery.views

import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.gallery.R
import org.fossify.gallery.databinding.MediaReorderPillsBinding
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.helpers.OutlineSettings
import org.fossify.gallery.helpers.PanelPivot
import org.fossify.gallery.helpers.hidePanel
import org.fossify.gallery.helpers.showPanel

/**
 * The pills a folder is arranged by hand through, standing where a selection's own would: the way
 * out at the top, and along the foot the two send-to-an-end arrows opposite Save. Built to the same
 * measurements as [SelectionPills], and like them never panned away with the grid.
 *
 * Painting itself is its own business - the grid only says what the buttons do.
 */
class ReorderPills(
    private val binding: MediaReorderPillsBinding,
    private val contentBehind: ViewGroup,
) {
    private val context = binding.root.context
    private val panels = binding.run { listOf(reorderCancelPanel, reorderMovePanel, reorderSavePanel) }

    var onMoveToEdge: ((toTop: Boolean) -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var onSave: (() -> Unit)? = null

    init {
        val resources = context.resources
        binding.apply {
            reorderCancelPanel.dressAsFloatingPill(resources.getDimension(R.dimen.peek_pill_radius))
            reorderMovePanel.dressAsFloatingPill(resources.getDimension(R.dimen.nav_pill_radius))
            reorderSavePanel.dressAsFloatingPill(resources.getDimension(R.dimen.nav_pill_radius))

            reorderCancel.setOnClickListener { onCancel?.invoke() }
            reorderSave.setOnClickListener { onSave?.invoke() }
            reorderMoveToTop.setOnClickListener { onMoveToEdge?.invoke(true) }
            reorderMoveToBottom.setOnClickListener { onMoveToEdge?.invoke(false) }
            // there is no room for a label beside either arrow
            TooltipCompat.setTooltipText(reorderMoveToTop, reorderMoveToTop.contentDescription)
            TooltipCompat.setTooltipText(reorderMoveToBottom, reorderMoveToBottom.contentDescription)
        }
    }

    fun show() {
        panels.forEach { it.frost(contentBehind) }
        updateColors()
        binding.reorderCancelFrame.showPanel(pivot = PanelPivot.TOP)
        binding.reorderMoveFrame.showPanel(pivot = PanelPivot.BOTTOM)
        binding.reorderSaveFrame.showPanel(pivot = PanelPivot.BOTTOM)
    }

    fun hide() {
        binding.reorderCancelFrame.hidePanel(pivot = PanelPivot.TOP)
        binding.reorderMoveFrame.hidePanel(pivot = PanelPivot.BOTTOM)
        binding.reorderSaveFrame.hidePanel(pivot = PanelPivot.BOTTOM)
    }

    /** Repainted on every resume: the theme can change while the screen is away. */
    fun updateColors() {
        val content = Glass.contentColor(context)
        // the one way out that keeps the arrangement, set apart from the pills beside it
        binding.reorderSavePanel.outline = OutlineSettings.pillLook(context)
        panels.forEach { it.updateColors() }
        binding.apply {
            listOf(reorderCancel, reorderMoveToTop, reorderMoveToBottom, reorderSave).forEach {
                it.paintWash(content, isCurrent = false)
            }

            listOf(reorderCancelIcon, reorderMoveToTopIcon, reorderMoveToBottomIcon, reorderSaveIcon).forEach {
                it.applyColorFilter(content)
            }

            reorderCancelLabel.setTextColor(content)
            reorderSaveLabel.setTextColor(content)
        }
    }
}
