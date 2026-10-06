package org.fossify.gallery.views

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.helpers.Glass
import org.fossify.gallery.models.SearchFilter
import org.fossify.gallery.models.label

private const val FILL_ALPHA = 0.12f
private const val CROSS_ALPHA = 0.8f

/**
 * The filter a grid is narrowed by, carried on the search bar between the magnifier and the field:
 * its name, and a cross that takes it off. Built into commons' bar the way the tab button is, with the
 * field moved along to start after it, so the hint and anything typed follow the chip. Tapping the
 * chip itself is tapping the bar - the search opens with the filter still on, to narrow it further.
 */
class FilterChip(private val topBar: MySearchMenu) {
    private val context = topBar.context
    private val resources = context.resources

    private var chip: LinearLayout? = null
    private var label: TextView? = null
    private var cross: ImageView? = null
    private val fill = GradientDrawable().apply {
        cornerRadius = resources.getDimension(R.dimen.filter_chip_height)
    }

    var onClear: (() -> Unit)? = null

    /** Puts [filter] on the bar, or takes the chip off it for null. */
    fun show(filter: SearchFilter?) {
        if (filter == null) {
            chip?.beGone()
            return
        }

        build()
        label?.text = filter.label(context)
        chip?.beVisible()
    }

    /** Repainted with the bar, since every colour here is the theme's. */
    fun updateColors() {
        val content = Glass.contentColor(context)
        fill.setColor(content.adjustAlpha(FILL_ALPHA))
        label?.setTextColor(content)
        cross?.applyColorFilter(content.adjustAlpha(CROSS_ALPHA))
    }

    private fun build() {
        if (chip != null) {
            return
        }

        val field = topBar.binding.topToolbarSearch
        val height = resources.getDimensionPixelSize(R.dimen.filter_chip_height)
        val padding = resources.getDimensionPixelSize(R.dimen.filter_chip_padding)
        val text = TextView(context).apply {
            val textSize = resources.getDimension(org.fossify.commons.R.dimen.normal_text_size)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxWidth = resources.getDimensionPixelSize(R.dimen.filter_chip_max_label_width)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPaddingRelative(padding, 0, 0, 0)
        }

        val clear = ImageView(context).apply {
            setImageResource(org.fossify.commons.R.drawable.ic_cross_vector)
            contentDescription = context.getString(R.string.remove_filter)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val inset = resources.getDimensionPixelSize(R.dimen.filter_chip_cross_inset)
            setPadding(inset, inset, inset, inset)
            setOnClickListener { onClear?.invoke() }
        }

        val holder = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = fill
            addView(text, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            addView(clear, LinearLayout.LayoutParams(height, height))
            setOnClickListener { reopenSearch() }
        }

        topBar.binding.toolbarContainer.addView(
            holder,
            RelativeLayout.LayoutParams(WRAP_CONTENT, height).apply {
                addRule(RelativeLayout.END_OF, topBar.binding.topToolbarSearchIcon.id)
                addRule(RelativeLayout.CENTER_VERTICAL)
                marginStart = resources.getDimensionPixelSize(R.dimen.filter_chip_gap)
            }
        )

        // a chip that is gone hands the field back to the magnifier: RelativeLayout follows a gone
        // anchor's own rule to the next one along
        (field.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.END_OF, holder.id)
        field.requestLayout()

        chip = holder
        label = text
        cross = clear
        updateColors()
    }

    private fun reopenSearch() {
        val field = topBar.binding.topToolbarSearch
        field.requestFocus()
        (context as? Activity)?.showKeyboard(field)
    }
}
