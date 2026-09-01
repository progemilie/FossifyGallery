package org.fossify.gallery.views

import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.gallery.R
import org.fossify.gallery.databinding.InfoPopupBinding

/**
 * The short note the (i) beside a setting opens under itself, saying what the setting does.
 *
 * A [PopupWindow] rather than a view of the screen's own, so it can hang past the row it belongs to,
 * take the back press that closes it, and be dismissed by a tap anywhere else. It is drawn on the
 * same raised surface as [Dropdown]'s list and placed by the same rules.
 */
class InfoPopup(private val anchor: View, private val text: CharSequence) {
    private val context = anchor.context
    private val resources = context.resources
    private val binding = InfoPopupBinding.inflate(LayoutInflater.from(context))

    private val popup = PopupWindow(
        binding.root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true
    ).apply {
        // the window itself stays out of sight: the panel inside it is what is seen and what casts
        // the shadow. An outside tap only dismisses a popup that has a background of some kind
        setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        isOutsideTouchable = true
    }

    fun show() {
        dress()

        val gap = resources.getDimensionPixelSize(R.dimen.dropdown_drop_gap)
        val room = resources.getDimensionPixelSize(R.dimen.dropdown_shadow_room)
        val margin = resources.getDimensionPixelSize(R.dimen.dropdown_screen_margin)
        val frame = Rect().also { anchor.getWindowVisibleDisplayFrame(it) }

        // as wide as the note asks for, less wherever the window is narrower than that
        popup.width = minOf(
            resources.getDimensionPixelSize(R.dimen.info_popup_width), frame.width() - margin * 2
        ) + room * 2

        val wanted = binding.infoPopupPanel.dropdownHeightAt(popup.width - room * 2) + room * 2
        val (height, yOffset) = anchor.dropdownPlacement(wanted, gap, room, margin)
        popup.height = height
        popup.showAsDropDown(anchor, room - margin, yOffset, Gravity.END)
    }

    /** Painted every time it opens: the theme can have changed since the last one. */
    private fun dress() {
        binding.infoPopupText.text = text
        binding.infoPopupText.setTextColor(context.getProperTextColor())
        binding.infoPopupPanel.apply {
            background = context.dropdownSurface(rippled = false)
            elevation = resources.getDimension(R.dimen.dropdown_elevation)
            // the note follows the rounded background rather than the box it is laid out in
            clipToOutline = true
        }

        // a tap on the room left for the shadow is a tap outside the note as far as anyone can tell
        binding.infoPopupFrame.setOnClickListener { popup.dismiss() }
    }
}

/** Hangs the note [text] off this view, which a tap opens. */
fun View.explains(text: CharSequence) {
    setOnClickListener { InfoPopup(this, text).show() }
}
