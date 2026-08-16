package org.fossify.gallery.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.views.MyAppCompatCheckbox
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogRemoveMetadataBinding
import org.fossify.gallery.databinding.ItemRemoveMetadataGroupBinding
import org.fossify.gallery.extensions.strippedCopyPath
import org.fossify.gallery.models.MetadataGroup

private const val DIVIDER_ALPHA = 0.15f

/**
 * Picks which of the metadata groups a file carries should come off it, and whether the result
 * replaces the file or is saved beside it.
 *
 * Nothing is ticked to begin with - this rewrites someone's photo, so the choice is made rather than
 * accepted - and "select all" is there for the common case of wanting a clean file.
 */
class RemoveMetadataDialog(
    private val activity: BaseSimpleActivity,
    path: String,
    groups: List<MetadataGroup>,
    private val callback: (groups: Set<MetadataGroup>, copyPath: String?) -> Unit,
) {
    private val binding = DialogRemoveMetadataBinding.inflate(activity.layoutInflater)
    private val boxes = LinkedHashMap<MetadataGroup, MyAppCompatCheckbox>()

    init {
        val copyPath = activity.strippedCopyPath(path)
        groups.forEach { group -> boxes[group] = addGroup(group) }

        val dividerColor = activity.getProperTextColor().adjustAlpha(DIVIDER_ALPHA)
        binding.removeMetadataGroupsDivider.setBackgroundColor(dividerColor)
        binding.removeMetadataCopyDivider.setBackgroundColor(dividerColor)

        // the ticks answer clicks rather than changes, so a box driven from another one below never
        // sets a listener off in turn
        binding.removeMetadataAll.setOnClickListener { setAll(binding.removeMetadataAll.isChecked) }
        binding.removeMetadataCopy.setOnCheckedChangeListener { _, checked ->
            binding.removeMetadataCopyName.beVisibleIf(checked)
        }

        binding.removeMetadataCopyName.text = copyPath.getFilenameFromPath()

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.metadata_remove) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        confirm(alertDialog, copyPath)
                    }
                }
            }
    }

    private fun addGroup(group: MetadataGroup): MyAppCompatCheckbox {
        val box = ItemRemoveMetadataGroupBinding.inflate(
            activity.layoutInflater, binding.removeMetadataGroups, true
        ).root

        box.setText(group.labelRes)
        box.setOnClickListener { sync() }
        return box
    }

    private fun setAll(checked: Boolean) {
        boxes.values.forEach { it.isChecked = checked }
        sync()
    }

    /**
     * Puts the ties between the boxes right after any of them moved: "select all" reads back what
     * the rest say, and the Exif tick carries the location with it - the location is stored inside
     * the Exif, so a location tick that could be cleared while Exif stayed ticked would be a lie.
     */
    private fun sync() {
        val exifPicked = boxes[MetadataGroup.EXIF]?.isChecked == true
        boxes[MetadataGroup.LOCATION]?.apply {
            if (exifPicked) isChecked = true
            isEnabled = !exifPicked
        }

        binding.removeMetadataAll.isChecked = boxes.values.all { it.isChecked }
    }

    private fun confirm(dialog: AlertDialog, copyPath: String) {
        val picked = boxes.filterValues { it.isChecked }.keys
        if (picked.isEmpty()) {
            activity.toast(R.string.metadata_remove_nothing_picked)
            return
        }

        dialog.dismiss()
        callback(picked, copyPath.takeIf { binding.removeMetadataCopy.isChecked })
    }
}
