package org.fossify.gallery.dialogs

import android.content.DialogInterface
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.SORT_BY_COUNT
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_PATH
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogChangeSortingBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SORT_BY_RATING

class ChangeSortingDialog(
    val activity: BaseSimpleActivity,
    val isDirectorySorting: Boolean,
    val showFolderCheckbox: Boolean,
    val path: String = "",
    val callback: () -> Unit
) :
    DialogInterface.OnClickListener {
    private var currSorting = 0
    private var config = activity.config
    private var pathToUse = if (!isDirectorySorting && path.isEmpty()) SHOW_ALL else path
    private val binding: DialogChangeSortingBinding

    // sorting media by hand only means something once the user arranged that folder, offering it
    // beforehand would be an option that does nothing
    private val canSortMediaCustomly = !isDirectorySorting && config.hasCustomMediaOrder(pathToUse)

    init {
        currSorting = if (isDirectorySorting) {
            config.directorySorting
        } else {
            config.getFolderSorting(pathToUse)
        }

        binding = DialogChangeSortingBinding.inflate(activity.layoutInflater).apply {
            sortingDialogRadioNumberOfItems.beVisibleIf(isDirectorySorting)
            sortingDialogOrderDivider.beVisibleIf(
                beVisible = showFolderCheckbox
                        || (currSorting and SORT_BY_NAME != 0 || currSorting and SORT_BY_PATH != 0)
            )

            sortingDialogNumericSorting.beVisibleIf(
                beVisible = showFolderCheckbox
                        && (currSorting and SORT_BY_NAME != 0 || currSorting and SORT_BY_PATH != 0)
            )

            sortingDialogNumericSorting.isChecked = currSorting and SORT_USE_NUMERIC_VALUE != 0

            sortingDialogUseForThisFolder.beVisibleIf(showFolderCheckbox)
            sortingDialogUseForThisFolder.isChecked = config.hasCustomSorting(pathToUse)
            sortingDialogBottomNote.beVisibleIf(!isDirectorySorting)
            sortingDialogRadioCustom.beVisibleIf(isDirectorySorting || canSortMediaCustomly)
            // folders have no rating of their own to sort by, only the media inside them do
            sortingDialogRadioRating.beVisibleIf(!isDirectorySorting)
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, this)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, org.fossify.commons.R.string.sort_by)
            }

        setupSortRadio()
        setupOrderRadio()
    }

    private fun setupSortRadio() {
        val sortingRadio = binding.sortingDialogRadioSorting
        sortingRadio.setOnCheckedChangeListener { _, checkedId ->
            sortingChanged(checkedId)
        }

        val sortBtn = when {
            currSorting and SORT_BY_PATH != 0 -> binding.sortingDialogRadioPath
            currSorting and SORT_BY_SIZE != 0 -> binding.sortingDialogRadioSize
            currSorting and SORT_BY_COUNT != 0 -> binding.sortingDialogRadioNumberOfItems
            currSorting and SORT_BY_DATE_MODIFIED != 0 -> binding.sortingDialogRadioLastModified
            currSorting and SORT_BY_DATE_TAKEN != 0 -> binding.sortingDialogRadioDateTaken
            currSorting and SORT_BY_RATING != 0 -> binding.sortingDialogRadioRating
            currSorting and SORT_BY_RANDOM != 0 -> binding.sortingDialogRadioRandom
            currSorting and SORT_BY_CUSTOM != 0 -> binding.sortingDialogRadioCustom
            else -> binding.sortingDialogRadioName
        }
        sortBtn.isChecked = true
    }

    private fun sortingChanged(checkedId: Int) {
        val isSortingByNameOrPath =
            checkedId == binding.sortingDialogRadioName.id
                    || checkedId == binding.sortingDialogRadioPath.id

        binding.sortingDialogNumericSorting.beVisibleIf(isSortingByNameOrPath)
        binding.sortingDialogOrderDivider.beVisibleIf(
            binding.sortingDialogNumericSorting.isVisible()
                    || binding.sortingDialogUseForThisFolder.isVisible()
        )

        val hideSortOrder =
            checkedId == binding.sortingDialogRadioCustom.id
                    || checkedId == binding.sortingDialogRadioRandom.id

        binding.sortingDialogRadioOrder.beGoneIf(hideSortOrder)
        binding.sortingDialogSortingDivider.beGoneIf(hideSortOrder)

        // a hand made order belongs to the one folder it was made in, it can never be the global sorting
        val isCustomMediaSorting =
            canSortMediaCustomly && checkedId == binding.sortingDialogRadioCustom.id
        binding.sortingDialogUseForThisFolder.apply {
            if (isCustomMediaSorting) {
                isChecked = true
            }
            isEnabled = !isCustomMediaSorting
        }
    }

    private fun setupOrderRadio() {
        var orderBtn = binding.sortingDialogRadioAscending

        if (currSorting and SORT_DESCENDING != 0) {
            orderBtn = binding.sortingDialogRadioDescending
        }
        orderBtn.isChecked = true
    }

    private fun getCheckedSorting() = when (binding.sortingDialogRadioSorting.checkedRadioButtonId) {
        R.id.sorting_dialog_radio_name -> SORT_BY_NAME
        R.id.sorting_dialog_radio_path -> SORT_BY_PATH
        R.id.sorting_dialog_radio_size -> SORT_BY_SIZE
        R.id.sorting_dialog_radio_number_of_items -> SORT_BY_COUNT
        R.id.sorting_dialog_radio_last_modified -> SORT_BY_DATE_MODIFIED
        R.id.sorting_dialog_radio_rating -> SORT_BY_RATING
        R.id.sorting_dialog_radio_random -> SORT_BY_RANDOM
        R.id.sorting_dialog_radio_custom -> SORT_BY_CUSTOM
        else -> SORT_BY_DATE_TAKEN
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        var sorting = getCheckedSorting()

        if (binding.sortingDialogRadioOrder.checkedRadioButtonId == R.id.sorting_dialog_radio_descending) {
            sorting = sorting or SORT_DESCENDING
        }

        if (binding.sortingDialogNumericSorting.isChecked) {
            sorting = sorting or SORT_USE_NUMERIC_VALUE
        }

        if (isDirectorySorting) {
            config.directorySorting = sorting
        } else {
            if (binding.sortingDialogUseForThisFolder.isChecked || sorting and SORT_BY_CUSTOM != 0) {
                config.saveCustomSorting(pathToUse, sorting)
            } else {
                config.removeCustomSorting(pathToUse)
                config.sorting = sorting
            }
        }

        if (currSorting != sorting) {
            callback()
        }
    }
}
