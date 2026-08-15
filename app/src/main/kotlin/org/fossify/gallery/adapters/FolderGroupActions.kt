package org.fossify.gallery.adapters

import android.view.Menu
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.FolderGroupNameDialog
import org.fossify.gallery.extensions.addToFolderGroup
import org.fossify.gallery.extensions.createFolderGroup
import org.fossify.gallery.extensions.removeFolderGroups
import org.fossify.gallery.extensions.removeFromFolderGroup
import org.fossify.gallery.extensions.renameFolderGroup
import org.fossify.gallery.extensions.replaceInCustomFolderOrder
import org.fossify.gallery.models.Directory

/**
 * What the folder grid's action mode can do with folder groups, and what a tile dropped onto another
 * one turns into. Driven from [DirectoryAdapter] rather than living inside it, the way
 * [FolderDragMode] is: binding tiles is upstream's job, bundling folders is ours.
 *
 * [releaseLandedTile] answers for a tile that flew into another one - the drop has to be undone if
 * the group it was meant to make is never named.
 */
// one item apiece for the five things a selection can be made into, and they belong together
@Suppress("TooManyFunctions")
internal class FolderGroupActions(
    private val adapter: DirectoryAdapter,
    private val releaseLandedTile: (restore: Boolean) -> Unit
) {
    private val activity get() = adapter.activity

    /**
     * What the selection can be made into. Folders alone can become a new group; folders picked
     * alongside exactly one group join that group instead. Two groups offer nothing - merging them
     * would have to guess which name to keep.
     */
    fun prepareMenu(menu: Menu, selectedGroups: List<Directory>, selectedFolders: List<Directory>) {
        val groupableFolders = selectedFolders.filter { it.canBeGrouped() }
        // inside a group the folders are already in one, so the only move left is out of it
        menu.findItem(R.id.cab_group_folders).isVisible =
            adapter.openGroupId == 0L && selectedGroups.isEmpty() && groupableFolders.isNotEmpty()
        menu.findItem(R.id.cab_remove_from_group).isVisible =
            adapter.openGroupId != 0L && selectedFolders.isNotEmpty()
        menu.findItem(R.id.cab_add_to_group).isVisible =
            selectedGroups.size == 1 && groupableFolders.isNotEmpty()
        menu.findItem(R.id.cab_ungroup_folders).isVisible =
            selectedGroups.isNotEmpty() && selectedFolders.isEmpty()
        menu.findItem(R.id.cab_rename_group).isVisible =
            selectedGroups.size == 1 && selectedFolders.isEmpty()
    }

    /** Whether [id] was one of ours, acted on if so. */
    fun handle(id: Int): Boolean {
        when (id) {
            R.id.cab_group_folders -> groupSelected()
            R.id.cab_add_to_group -> addSelectedToGroup()
            R.id.cab_remove_from_group -> removeSelectedFromGroup()
            R.id.cab_ungroup_folders -> ungroupSelected()
            R.id.cab_rename_group -> renameSelected()
            else -> return false
        }

        return true
    }

    /**
     * Puts [dragged] into the group it was dropped on, or asks what to call the group the two
     * folders make. The folder dropped on leads the group either way - it is the one that stayed
     * still, and the collage reads the members in order.
     */
    fun groupDragged(dragged: Directory, target: Directory) {
        if (target.isFolderGroup()) {
            activity.addToFolderGroup(target.folderGroupId(), listOf(dragged.path))
            activity.toast(activity.getString(R.string.added_to_group, target.name))
            releaseLandedTile(false)
            refreshGrid()
            return
        }

        FolderGroupNameDialog(
            activity = activity,
            titleResId = R.string.new_group,
            currentName = target.name,
            // the tile left the grid as it flew into the target, so a dialog closed without a name
            // has to put it back
            onCancel = { releaseLandedTile(true) }
        ) { name ->
            val group = activity.createFolderGroup(name, listOf(target.path, dragged.path))
            activity.replaceInCustomFolderOrder(target.path, group.syntheticPath())
            releaseLandedTile(false)
            refreshGrid()
        }
    }

    private fun groupSelected() {
        val paths = groupablePaths()
        if (paths.isEmpty()) {
            return
        }

        FolderGroupNameDialog(activity, R.string.new_group) { name ->
            activity.createFolderGroup(name, paths)
            refreshGrid()
        }
    }

    private fun addSelectedToGroup() {
        val group = selectedGroups().singleOrNull() ?: return
        val paths = groupablePaths()
        if (paths.isEmpty()) {
            return
        }

        activity.addToFolderGroup(group.folderGroupId(), paths)
        refreshGrid()
    }

    private fun removeSelectedFromGroup() {
        val paths = selectedFolders().map { it.path }
        if (adapter.openGroupId == 0L || paths.isEmpty()) {
            return
        }

        activity.removeFromFolderGroup(adapter.openGroupId, paths)
        refreshGrid()
    }

    private fun ungroupSelected() {
        val ids = selectedGroups().map { it.folderGroupId() }
        if (ids.isEmpty()) {
            return
        }

        activity.removeFolderGroups(ids)
        refreshGrid()
    }

    private fun renameSelected() {
        val group = selectedGroups().singleOrNull() ?: return
        FolderGroupNameDialog(activity, R.string.rename_group, group.name) { name ->
            activity.renameFolderGroup(group.folderGroupId(), name)
            refreshGrid()
        }
    }

    /**
     * The selected folders that can go into a group, in the order the grid holds them rather than
     * the order they happened to be tapped - that order becomes the group's, and the collage.
     */
    private fun groupablePaths(): List<String> {
        val selected = selectedFolders().filter { it.canBeGrouped() }.mapTo(HashSet()) { it.path }
        return adapter.dirs.map { it.path }.filter { selected.contains(it) }
    }

    private fun selectedGroups() = adapter.getSelectedItems().filter { it.isFolderGroup() }

    private fun selectedFolders() = adapter.getSelectedItems().filter { !it.isFolderGroup() }

    private fun refreshGrid() {
        adapter.finishActMode()
        adapter.listener?.refreshItems()
    }
}
