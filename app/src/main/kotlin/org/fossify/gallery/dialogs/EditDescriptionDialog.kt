package org.fossify.gallery.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.value
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogEditDescriptionBinding

/**
 * Writes the caption a file carries about itself. An empty field is a valid answer - it is how a
 * description is taken back off a file.
 */
class EditDescriptionDialog(
    private val activity: BaseSimpleActivity,
    private val currentDescription: String,
    private val callback: (description: String) -> Unit,
) {
    init {
        val binding = DialogEditDescriptionBinding.inflate(activity.layoutInflater).apply {
            editDescription.setText(currentDescription)
            editDescription.setSelection(currentDescription.length)
        }

        val title = if (currentDescription.isEmpty()) {
            R.string.add_description
        } else {
            R.string.edit_description
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, title) { alertDialog ->
                    alertDialog.showKeyboard(binding.editDescription)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val description = binding.editDescription.value.trim()
                        alertDialog.dismiss()
                        if (description != currentDescription) {
                            callback(description)
                        }
                    }
                }
            }
    }
}
