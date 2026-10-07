package org.fossify.gallery.views

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
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
 * its name, and a cross that takes it off. It goes into the bar's [SearchLine] ahead of the field, so
 * anything typed follows the chip and scrolls it away once there is too much to fit beside it; and it
 * stands in for the bar's hint while it is up. Tapping the chip itself is tapping the bar - the search
 * opens with the filter still on, to narrow it further.
 */
class FilterChip(private val topBar: MySearchMenu, private val line: SearchLine) {
    private val context = topBar.context
    private val resources = context.resources

    private var chip: LinearLayout? = null
    private var label: TextView? = null
    private var cross: ImageView? = null
    private val fill = GradientDrawable().apply {
        cornerRadius = resources.getDimension(R.dimen.filter_chip_height)
    }

    var onClear: (() -> Unit)? = null

    // the bar's hint, put aside while the chip says what is being searched instead
    private var hint: CharSequence? = null

    /** Puts [filter] on the bar, or takes the chip off it for null. */
    fun show(filter: SearchFilter?) {
        // the hint touched only when it changes: setting it has the field drop its text layout, and
        // this runs as a tap opens the search, which then finds no layout and puts no keyboard up
        val field = topBar.binding.topToolbarSearch
        if (filter == null) {
            chip?.beGone()
            // unless the bar has been named again since
            if (field.hint == null && hint != null) {
                field.hint = hint
            }

            return
        }

        build()
        label?.text = filter.label(context)
        chip?.beVisible()
        if (field.hint != null) {
            hint = field.hint
            field.hint = null
        }
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
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = fill
            addView(text, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            addView(clear, LinearLayout.LayoutParams(height, height))
            setOnClickListener { reopenSearch() }
        }

        line.row.addView(
            holder,
            0,
            LinearLayout.LayoutParams(WRAP_CONTENT, height).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.filter_chip_gap)
            }
        )

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
