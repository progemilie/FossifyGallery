package org.fossify.gallery.dialogs

import android.content.DialogInterface
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.beInvisibleIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_PATH
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
import org.fossify.gallery.databinding.DialogChangeSortingBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.GROUP_BY_NONE
import org.fossify.gallery.helpers.GROUP_DESCENDING
import org.fossify.gallery.helpers.GROUP_SHOW_FILE_COUNT
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SORT_BY_RATING
import org.fossify.gallery.views.DROPDOWN_DISABLED_ALPHA
import org.fossify.gallery.views.Dropdown
import org.fossify.commons.R as commonsR

/**
 * How a grid is arranged: what it is sorted by, what it is grouped into, and which way round each of
 * those two runs - separately, a folder being often wanted newest first but with its months running
 * oldest at the top.
 *
 * Grouping belongs to the media grid alone. Folders are never grouped, and a grid that scrolls
 * sideways has its groups flattened by [org.fossify.gallery.helpers.MediaFetcher.groupMedia]
 * anyway, so that section goes away in both cases. The one "use for this folder only" tick covers
 * sorting and grouping together whenever both are on screen.
 */
class ChangeSortingDialog(
    private val activity: BaseSimpleActivity,
    private val isDirectorySorting: Boolean,
    private val path: String = "",
    private val callback: () -> Unit,
) : DialogInterface.OnClickListener {
    private val config = activity.config
    private val pathToUse = if (!isDirectorySorting && path.isEmpty()) SHOW_ALL else path

    // sorting media by hand only means something once the user arranged that folder, offering it
    // beforehand would be an option that does nothing
    private val canSortMediaCustomly = !isDirectorySorting && config.hasCustomMediaOrder(pathToUse)

    private val currSorting =
        if (isDirectorySorting) config.directorySorting else config.getFolderSorting(pathToUse)
    private val currGrouping = config.getFolderGrouping(pathToUse)
    private val hadCustomGrouping = config.hasCustomGrouping(pathToUse)
    private val showsGrouping = !isDirectorySorting && !config.scrollHorizontally

    private val binding = DialogChangeSortingBinding.inflate(activity.layoutInflater)

    private val sortField =
        sortingOptions(activity, isDirectorySorting, canSortMediaCustomly).let { options ->
            Dropdown(
                binding.sortingDialogSortField, options, selectedOption(currSorting, options)
            ) { sortingPicked(it) }
        }

    private val groupField =
        groupingOptions(activity, offerFolder = path.isEmpty()).let { options ->
            Dropdown(
                binding.sortingDialogGroupField, options, selectedOption(currGrouping, options)
            ) {
                groupingTouched = true
                groupingPicked(it)
            }
        }

    private var sortDescending = currSorting and SORT_DESCENDING != 0
    private var groupDescending = currGrouping and GROUP_DESCENDING != 0

    // grouping nobody touched is written nowhere; see saveGrouping
    private var groupingTouched = false

    init {
        binding.apply {
            sortingDialogGroupSection.beVisibleIf(showsGrouping)
            sortingDialogNumericSorting.isChecked = currSorting and SORT_USE_NUMERIC_VALUE != 0
            sortingDialogShowFileCount.isChecked = currGrouping and GROUP_SHOW_FILE_COUNT != 0
            // set before the listener goes on, or the initial state counts as the user's doing
            sortingDialogShowFileCount.setOnCheckedChangeListener { _, _ -> groupingTouched = true }

            sortingDialogUseForThisFolder.beVisibleIf(!isDirectorySorting)
            // one tick for both, so either of them already belonging to this folder starts it ticked
            sortingDialogUseForThisFolder.isChecked =
                config.hasCustomSorting(pathToUse) || (showsGrouping && hadCustomGrouping)

            sortingDialogSortOrder.setUpOrderToggle(sortDescending) { sortDescending = it }
            sortingDialogGroupOrder.setUpOrderToggle(groupDescending) {
                groupDescending = it
                groupingTouched = true
            }
        }

        sortingPicked(sortField.selectedId)
        groupingPicked(groupField.selectedId)

        activity.getAlertDialogBuilder()
            .setPositiveButton(commonsR.string.ok, this)
            .setNegativeButton(commonsR.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, commonsR.string.sort_by)
            }
    }

    /** What the sort dropdown now says, and what that leaves worth showing under it. */
    private fun sortingPicked(sorting: Int) {
        binding.sortingDialogNumericSorting
            .beVisibleIf(sorting == SORT_BY_NAME || sorting == SORT_BY_PATH)

        // a shuffle and a hand made order have no direction to run in. INVISIBLE rather than GONE,
        // or the field beside it widens and narrows as the choice changes
        binding.sortingDialogSortOrder
            .beInvisibleIf(sorting == SORT_BY_CUSTOM || sorting == SORT_BY_RANDOM)

        // a hand made order belongs to the one folder it was made in, it is never the global sorting
        val isCustomMediaSorting = canSortMediaCustomly && sorting == SORT_BY_CUSTOM
        binding.sortingDialogUseForThisFolder.apply {
            if (isCustomMediaSorting) {
                isChecked = true
            }

            isEnabled = !isCustomMediaSorting
        }

        // a hand made order is drawn as the flat list it is and a rating brings headers of its own,
        // so MediaFetcher.groupMedia ignores the grouping under either - say so, rather than leave a
        // live looking choice that is dropped the moment the dialog closes
        setGroupSectionLive(sorting != SORT_BY_CUSTOM && sorting != SORT_BY_RATING)
        refreshDivider()
    }

    /** Ungrouped media have no section headers to order or to count anything at. */
    private fun groupingPicked(grouping: Int) {
        val grouped = grouping != GROUP_BY_NONE
        binding.sortingDialogGroupOrder.beInvisibleIf(!grouped)
        binding.sortingDialogShowFileCount.beVisibleIf(showsGrouping && grouped)
        refreshDivider()
    }

    private fun setGroupSectionLive(live: Boolean) {
        val dim = if (live) 1f else DROPDOWN_DISABLED_ALPHA
        groupField.isEnabled = live
        binding.sortingDialogGroupCaption.alpha = dim
        binding.sortingDialogGroupOrder.also { it.isEnabled = live }.alpha = dim
        binding.sortingDialogShowFileCount.also { it.isEnabled = live }.alpha = dim
    }

    /** The rule only earns its place while something is left under it. */
    private fun refreshDivider() {
        binding.sortingDialogDivider.beVisibleIf(
            binding.sortingDialogNumericSorting.isVisible()
                    || binding.sortingDialogShowFileCount.isVisible()
                    || binding.sortingDialogUseForThisFolder.isVisible()
        )
    }

    private fun saveSorting(): Int {
        var sorting = sortField.selectedId
        if (sortDescending) {
            sorting = sorting or SORT_DESCENDING
        }

        if (binding.sortingDialogNumericSorting.isChecked) {
            sorting = sorting or SORT_USE_NUMERIC_VALUE
        }

        if (isDirectorySorting) {
            config.directorySorting = sorting
        } else if (binding.sortingDialogUseForThisFolder.isChecked ||
            sorting and SORT_BY_CUSTOM != 0
        ) {
            config.saveCustomSorting(pathToUse, sorting)
        } else {
            config.removeCustomSorting(pathToUse)
            config.sorting = sorting
        }

        return sorting
    }

    /** Returns whether what this folder is grouped into came out any different. */
    private fun saveGrouping(): Boolean {
        val perFolder = binding.sortingDialogUseForThisFolder.isChecked
        val scopeChanged = perFolder != hadCustomGrouping
        // grouping nobody touched is left exactly as it was found. Config.getFolderGrouping takes a
        // global "by folder" back off the value on the way out for anything but the show-all view,
        // and what that arithmetic hands over is not a number worth writing anywhere
        if (!showsGrouping || (!groupingTouched && !scopeChanged)) {
            return false
        }

        var grouping = groupField.selectedId
        if (groupDescending) {
            grouping = grouping or GROUP_DESCENDING
        }

        if (binding.sortingDialogShowFileCount.isChecked) {
            grouping = grouping or GROUP_SHOW_FILE_COUNT
        }

        if (perFolder) {
            config.saveFolderGrouping(pathToUse, grouping)
        } else {
            config.removeFolderGrouping(pathToUse)
            if (groupingTouched) {
                config.groupBy = grouping
            }
        }

        return true
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        val sorting = saveSorting()
        // both are saved, whichever of them moved: the grid has to be rebuilt either way
        val groupingChanged = saveGrouping()
        if (currSorting != sorting || groupingChanged) {
            callback()
        }
    }
}
