package org.fossify.gallery.views

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beInvisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DropdownFieldBinding
import org.fossify.gallery.databinding.DropdownListBinding
import org.fossify.gallery.databinding.DropdownRowBinding

/** One choice a [Dropdown] offers: the constant it stands for, and what it is called. */
data class DropdownOption(val id: Int, val label: String)

/** How far a dropdown, or anything else a dialog has greyed out beside one, is faded. */
internal const val DROPDOWN_DISABLED_ALPHA = 0.4f

/**
 * A field naming one choice out of a short list, which drops the rest of them open right under
 * itself when tapped. Built for dialogs, where a radio list of nine options is most of a screen.
 *
 * The list is a [PopupWindow] rather than a view of the dialog's own, so it can hang past the
 * dialog's edge, take the back press that closes it, and be dismissed by a tap anywhere else. That
 * also puts it in a window of its own, where there is nothing behind it to sample - hence the plain
 * raised surface here rather than the [GlassPanel] the three dots' drop-down is made of.
 */
class Dropdown(
    private val binding: DropdownFieldBinding,
    private val options: List<DropdownOption>,
    selection: Int,
    private val onPick: (Int) -> Unit,
) {
    private val context = binding.root.context
    private val resources = context.resources
    private val list = DropdownListBinding.inflate(LayoutInflater.from(context))
    private val panel = list.dropdownListPanel
    private val column = list.dropdownListColumn

    private val popup = PopupWindow(
        list.root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true
    ).apply {
        // the window itself stays out of sight: the panel inside it is what is seen and what casts
        // the shadow. An outside tap only dismisses a popup that has a background of some kind
        setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        isOutsideTouchable = true
    }

    /** Which option the field is naming. Setting it redraws the label and calls nothing back. */
    var selectedId: Int = selection
        set(value) {
            field = value
            binding.dropdownFieldLabel.text = options.firstOrNull { it.id == value }?.label
        }

    /** A field nothing would come of picking from is faded, and does not open. */
    var isEnabled: Boolean = true
        set(value) {
            field = value
            binding.root.alpha = if (value) 1f else DROPDOWN_DISABLED_ALPHA
            if (!value) {
                popup.dismiss()
            }
        }

    init {
        binding.root.apply {
            background = context.dropdownSurface(rippled = true)
            setOnClickListener { show() }
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                // a popup outlives the dialog that put it up, and takes the window down with it
                override fun onViewDetachedFromWindow(view: View) = popup.dismiss()
            })
        }

        binding.dropdownFieldChevron.applyColorFilter(context.getProperTextColor())

        panel.apply {
            background = context.dropdownSurface(rippled = false)
            elevation = resources.getDimension(R.dimen.dropdown_elevation)
            // the rows follow the rounded background rather than the box they are laid out in
            clipToOutline = true
            // so a tap that lands between rows stops at the panel rather than counting as a miss
            isClickable = true
        }

        // a tap on the room left for the shadow is a tap outside the list as far as anyone can tell
        list.dropdownListFrame.setOnClickListener { popup.dismiss() }
        selectedId = selection
    }

    private fun show() {
        if (!isEnabled) {
            return
        }

        fill()

        val gap = resources.getDimensionPixelSize(R.dimen.dropdown_drop_gap)
        val room = resources.getDimensionPixelSize(R.dimen.dropdown_shadow_room)
        val margin = resources.getDimensionPixelSize(R.dimen.dropdown_screen_margin)
        val anchor = binding.root

        // the list is never narrower than the field it belongs to, and the panel sits [room] inside
        // the popup on every side
        popup.width = maxOf(anchor.width, column.widestDropdownRow()) + room * 2
        val wanted = column.dropdownHeightAt(popup.width - room * 2) + room * 2
        val (height, yOffset) = anchor.dropdownPlacement(wanted, gap, room, margin)
        popup.height = height
        popup.showAsDropDown(anchor, -room, yOffset, Gravity.START)
        revealSelected()
    }

    /** Draws the options, the one in force marked. */
    private fun fill() {
        val inflater = LayoutInflater.from(context)
        val textColor = context.getProperTextColor()
        val primaryColor = context.getProperPrimaryColor()

        column.removeAllViews()
        options.forEach { option ->
            val isSelected = option.id == selectedId
            val row = DropdownRowBinding.inflate(inflater, column, false)
            row.dropdownRowLabel.text = option.label
            row.dropdownRowLabel.setTextColor(if (isSelected) primaryColor else textColor)
            // INVISIBLE rather than GONE: every row has to measure the same, or the list comes out
            // as wide as whichever one happens to be ticked
            row.dropdownRowCheck.beInvisibleIf(!isSelected)
            row.dropdownRowCheck.applyColorFilter(primaryColor)
            row.root.setOnClickListener { pick(option.id) }
            column.addView(row.root)
        }
    }

    private fun pick(id: Int) {
        popup.dismiss()
        if (id != selectedId) {
            selectedId = id
            onPick(id)
        }
    }

    /** A list too long to fit opens on the option in force rather than at the top of itself. */
    private fun revealSelected() {
        val index = options.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        panel.post {
            val row = column.getChildAt(index) ?: return@post
            panel.scrollTo(0, row.top - (panel.height - row.height) / 2)
        }
    }
}
