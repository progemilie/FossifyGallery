package org.fossify.gallery.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogFolderGroupNameBinding

/**
 * Asks for a folder group's name, both when one is created and when it is renamed. [onCancel] is
 * for callers that have already shown something happening and have to put it back if it does not.
 */
class FolderGroupNameDialog(
    private val activity: BaseSimpleActivity,
    private val titleResId: Int,
    private val currentName: String = "",
    private val onCancel: () -> Unit = {},
    private val callback: (name: String) -> Unit
) {
    init {
        val binding = DialogFolderGroupNameBinding.inflate(activity.layoutInflater).apply {
            folderGroupName.setText(currentName)
            folderGroupName.setSelection(currentName.length)
        }

        var named = false
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, titleResId) { alertDialog ->
                    alertDialog.showKeyboard(binding.folderGroupName)
                    alertDialog.setOnDismissListener {
                        if (!named) {
                            onCancel()
                        }
                    }

                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = binding.folderGroupName.value.trim()
                        if (name.isEmpty()) {
                            activity.toast(R.string.empty_group_name)
                            return@setOnClickListener
                        }

                        named = true
                        callback(name)
                        alertDialog.dismiss()
                    }
                }
            }
    }
}
