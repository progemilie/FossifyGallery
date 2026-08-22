package org.fossify.gallery.dialogs

import android.content.Context
import android.widget.ImageView
import androidx.appcompat.widget.TooltipCompat
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.helpers.SORT_BY_COUNT
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_PATH
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.gallery.R
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_DAILY
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_MONTHLY
import org.fossify.gallery.helpers.GROUP_BY_EXTENSION
import org.fossify.gallery.helpers.GROUP_BY_FILE_TYPE
import org.fossify.gallery.helpers.GROUP_BY_FOLDER
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_DAILY
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_MONTHLY
import org.fossify.gallery.helpers.GROUP_BY_NONE
import org.fossify.gallery.helpers.SORT_BY_RATING
import org.fossify.gallery.views.DropdownOption
import org.fossify.commons.R as commonsR

/**
 * What the two dropdowns of [ChangeSortingDialog] offer, and the arrow that sits beside each of
 * them. Split off the dialog to keep either side small.
 */

/** Everything a grid can be sorted by, less whatever this one has no use for. */
internal fun sortingOptions(
    context: Context,
    isDirectorySorting: Boolean,
    canSortMediaCustomly: Boolean,
): List<DropdownOption> = buildList {
    add(SORT_BY_NAME to commonsR.string.name)
    add(SORT_BY_PATH to commonsR.string.path)
    add(SORT_BY_SIZE to commonsR.string.size)
    // only a folder has a number of items to it
    if (isDirectorySorting) {
        add(SORT_BY_COUNT to commonsR.string.number_of_items)
    }

    add(SORT_BY_DATE_MODIFIED to commonsR.string.last_modified)
    add(SORT_BY_DATE_TAKEN to commonsR.string.date_taken)
    // folders have no rating of their own, only the media inside them do
    if (!isDirectorySorting) {
        add(SORT_BY_RATING to R.string.rating)
    }

    add(SORT_BY_RANDOM to commonsR.string.random)
    // sorting media by hand only means something once the user arranged that folder
    if (isDirectorySorting || canSortMediaCustomly) {
        add(SORT_BY_CUSTOM to commonsR.string.custom)
    }
}.toOptions(context)

/** Everything the media grid can be grouped into. Only the show-all view has folders to group by. */
internal fun groupingOptions(context: Context, offerFolder: Boolean): List<DropdownOption> =
    buildList {
        add(GROUP_BY_NONE to R.string.do_not_group_files)
        add(GROUP_BY_LAST_MODIFIED_DAILY to R.string.by_last_modified_daily)
        add(GROUP_BY_LAST_MODIFIED_MONTHLY to R.string.by_last_modified_monthly)
        add(GROUP_BY_DATE_TAKEN_DAILY to R.string.by_date_taken_daily)
        add(GROUP_BY_DATE_TAKEN_MONTHLY to R.string.by_date_taken_monthly)
        add(GROUP_BY_FILE_TYPE to R.string.by_file_type)
        add(GROUP_BY_EXTENSION to R.string.by_extension)
        if (offerFolder) {
            add(GROUP_BY_FOLDER to R.string.by_folder)
        }
    }.toOptions(context)

private fun List<Pair<Int, Int>>.toOptions(context: Context) =
    map { (id, label) -> DropdownOption(id, context.getString(label)) }

/**
 * Which of [options] a stored value stands for. Anything the list does not offer falls back to the
 * first of them - a rating sorting inherited by the folder grid, or the mangled "by folder"
 * grouping Config.getFolderGrouping hands back inside a folder - so a dropdown can never come up
 * naming something it does not itself offer.
 */
internal fun selectedOption(value: Int, options: List<DropdownOption>) =
    options.firstOrNull { value and it.id != 0 }?.id ?: options.first().id

/** The arrow at the end of a dropdown row, which flips what it shows every time it is tapped. */
internal fun ImageView.setUpOrderToggle(descending: Boolean, onToggle: (Boolean) -> Unit) {
    var isDescending = descending

    fun draw() {
        setImageResource(
            if (isDescending) R.drawable.ic_arrow_down_vector else R.drawable.ic_arrow_up_vector
        )

        // a freshly set drawable comes in uncoloured every time
        applyColorFilter(context.getProperPrimaryColor())
        contentDescription = context.getString(
            if (isDescending) commonsR.string.descending else commonsR.string.ascending
        )

        TooltipCompat.setTooltipText(this, contentDescription)
    }

    draw()
    setOnClickListener {
        isDescending = !isDescending
        draw()
        onToggle(isDescending)
    }
}
