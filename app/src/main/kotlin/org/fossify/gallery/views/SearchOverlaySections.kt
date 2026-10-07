package org.fossify.gallery.views

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.children
import androidx.core.view.descendants
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.gallery.R
import org.fossify.gallery.helpers.SearchOptions
import org.fossify.gallery.models.SearchFilter
import org.fossify.gallery.models.label

/** How many devices are offered before the rest wait behind More. */
private const val DEVICES_SHOWN = 6

private const val HEADING_ALPHA = 0.7f

/**
 * The search overlay's sections - Type, Device, File size - each a heading over the pills it offers,
 * wrapping onto as many lines as they need. A section with nothing to offer is left out altogether.
 */
class SearchOverlaySections(private val column: LinearLayout) {
    private val context = column.context
    private val resources = context.resources
    private val gap = resources.getDimensionPixelSize(R.dimen.search_pill_gap)
    private val sectionGap = resources.getDimensionPixelSize(R.dimen.search_section_gap)
    private val headingGap = resources.getDimensionPixelSize(R.dimen.search_heading_gap)

    var onChosen: ((SearchFilter) -> Unit)? = null

    fun fill(options: SearchOptions, active: SearchFilter?) {
        column.removeAllViews()
        section(R.string.search_type, options.kinds.map(SearchFilter::Kind), active)
        section(R.string.search_device, options.devices.map(SearchFilter::Device), active, shown = DEVICES_SHOWN)
        section(R.string.search_file_size, options.sizes.map(SearchFilter::Size), active)
        // said rather than left to an empty dim, which reads as the search not having opened at all
        if (column.isEmpty()) {
            column.addView(heading(R.string.search_nothing_to_filter, isBold = false))
        }
    }

    fun clear() = column.removeAllViews()

    /** Whether the point [x], [y] of the screen falls on a pill on show. */
    fun hasPillAt(x: Float, y: Float): Boolean {
        val at = IntArray(2)
        return column.descendants.any { pill ->
            pill is SearchPill && pill.isShown && run {
                pill.getLocationOnScreen(at)
                x >= at[0] && x < at[0] + pill.width && y >= at[1] && y < at[1] + pill.height
            }
        }
    }

    private fun section(
        @StringRes title: Int,
        choices: List<SearchFilter>,
        active: SearchFilter?,
        shown: Int = Int.MAX_VALUE,
    ) {
        if (choices.isEmpty()) {
            return
        }

        column.addView(heading(title))
        val row = FlowRow(context).apply {
            horizontalGap = gap
            verticalGap = gap
        }

        choices.forEachIndexed { index, filter ->
            row.addView(
                SearchPill(context).apply {
                    text = filter.label(context)
                    isLit = filter == active
                    // the filter already on stays in sight wherever it falls in the list
                    isVisible = index < shown || isLit
                    setOnClickListener { onChosen?.invoke(filter) }
                }
            )
        }

        if (row.children.any { !it.isVisible }) {
            row.addView(morePill(row))
        }

        column.addView(row)
    }

    /** Puts up every pill the row was holding back, in its own place. */
    private fun morePill(row: FlowRow) = SearchPill(context).apply {
        text = context.getString(R.string.search_more)
        setOnClickListener {
            row.removeView(this)
            row.children.forEach { it.isVisible = true }
        }
    }

    private fun heading(@StringRes title: Int, isBold: Boolean = true) = TextView(context).apply {
        text = context.getString(title)
        setTypeface(typeface, if (isBold) Typeface.BOLD else Typeface.NORMAL)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(org.fossify.commons.R.dimen.normal_text_size))
        setTextColor(Color.WHITE.adjustAlpha(HEADING_ALPHA))
        updatePadding(top = if (column.isEmpty()) 0 else sectionGap, bottom = headingGap)
    }
}
