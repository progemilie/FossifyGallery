package org.fossify.gallery.dialogs

import android.content.DialogInterface
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogChangeGroupingBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*

class ChangeGroupingDialog(val activity: BaseSimpleActivity, val path: String = "", val callback: () -> Unit) :
    DialogInterface.OnClickListener {
    private var currGrouping = 0
    private var config = activity.config
    private val pathToUse = if (path.isEmpty()) SHOW_ALL else path
    private val sorting = config.getFolderSorting(pathToUse)
    private val binding: DialogChangeGroupingBinding

    init {
        currGrouping = config.getFolderGrouping(pathToUse)
        binding = DialogChangeGroupingBinding.inflate(activity.layoutInflater).apply {
            groupingDialogUseForThisFolder.isChecked = config.hasCustomGrouping(pathToUse)
            groupingDialogRadioFolder.beVisibleIf(path.isEmpty())
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, this)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.group_by)
            }

        setupGroupRadio()
        setupOrderRadio()
        binding.groupingDialogRadioGrouping.setOnCheckedChangeListener { _, _ -> setupOrderRadio() }
        binding.groupingDialogShowFileCount.isChecked = currGrouping and GROUP_SHOW_FILE_COUNT != 0
    }

    private fun setupGroupRadio() {
        val groupBtn = when {
            currGrouping and GROUP_BY_NONE != 0 -> binding.groupingDialogRadioNone
            currGrouping and GROUP_BY_LAST_MODIFIED_DAILY != 0 -> binding.groupingDialogRadioLastModifiedDaily
            currGrouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0 -> binding.groupingDialogRadioLastModifiedMonthly
            currGrouping and GROUP_BY_DATE_TAKEN_DAILY != 0 -> binding.groupingDialogRadioDateTakenDaily
            currGrouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0 -> binding.groupingDialogRadioDateTakenMonthly
            currGrouping and GROUP_BY_FILE_TYPE != 0 -> binding.groupingDialogRadioFileType
            currGrouping and GROUP_BY_EXTENSION != 0 -> binding.groupingDialogRadioExtension
            else -> binding.groupingDialogRadioFolder
        }
        groupBtn.isChecked = true
    }

    /**
     * Group by a day or a month of the very date the media is sorted by and the group order is no
     * longer the user's to pick - MediaFetcher takes it from the sorting, so that one timeline does
     * not run in two directions at once. Show them that: the radio that will actually apply, greyed
     * out, and a note saying where it comes from, rather than a live looking choice that gets
     * ignored the moment the dialog closes.
     */
    private fun setupOrderRadio() {
        val followsSorting = doesGroupOrderFollowSorting()
        val isDescending = if (followsSorting) {
            sorting and SORT_DESCENDING != 0
        } else {
            currGrouping and GROUP_DESCENDING != 0
        }

        binding.groupingDialogRadioAscending.isEnabled = !followsSorting
        binding.groupingDialogRadioDescending.isEnabled = !followsSorting
        val orderBtn = if (isDescending) binding.groupingDialogRadioDescending else binding.groupingDialogRadioAscending
        orderBtn.isChecked = true
        binding.groupingDialogBottomNote.setText(
            if (followsSorting) R.string.grouping_follows_sorting else R.string.grouping_and_sorting
        )
    }

    private fun doesGroupOrderFollowSorting(): Boolean {
        val grouping = getSelectedGrouping()
        val groupsByTaken = grouping and (GROUP_BY_DATE_TAKEN_DAILY or GROUP_BY_DATE_TAKEN_MONTHLY) != 0
        val groupsByModified = grouping and (GROUP_BY_LAST_MODIFIED_DAILY or GROUP_BY_LAST_MODIFIED_MONTHLY) != 0
        return (groupsByTaken && sorting and SORT_BY_DATE_TAKEN != 0) ||
            (groupsByModified && sorting and SORT_BY_DATE_MODIFIED != 0)
    }

    private fun getSelectedGrouping() = when (binding.groupingDialogRadioGrouping.checkedRadioButtonId) {
        R.id.grouping_dialog_radio_none -> GROUP_BY_NONE
        R.id.grouping_dialog_radio_last_modified_daily -> GROUP_BY_LAST_MODIFIED_DAILY
        R.id.grouping_dialog_radio_last_modified_monthly -> GROUP_BY_LAST_MODIFIED_MONTHLY
        R.id.grouping_dialog_radio_date_taken_daily -> GROUP_BY_DATE_TAKEN_DAILY
        R.id.grouping_dialog_radio_date_taken_monthly -> GROUP_BY_DATE_TAKEN_MONTHLY
        R.id.grouping_dialog_radio_file_type -> GROUP_BY_FILE_TYPE
        R.id.grouping_dialog_radio_extension -> GROUP_BY_EXTENSION
        else -> GROUP_BY_FOLDER
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        var grouping = getSelectedGrouping()

        if (binding.groupingDialogRadioOrder.checkedRadioButtonId == R.id.grouping_dialog_radio_descending) {
            grouping = grouping or GROUP_DESCENDING
        }

        if (binding.groupingDialogShowFileCount.isChecked) {
            grouping = grouping or GROUP_SHOW_FILE_COUNT
        }

        if (binding.groupingDialogUseForThisFolder.isChecked) {
            config.saveFolderGrouping(pathToUse, grouping)
        } else {
            config.removeFolderGrouping(pathToUse)
            config.groupBy = grouping
        }

        callback()
    }
}
