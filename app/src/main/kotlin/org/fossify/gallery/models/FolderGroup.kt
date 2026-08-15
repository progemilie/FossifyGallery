package org.fossify.gallery.models

import org.fossify.gallery.helpers.FOLDER_GROUP_PATH_PREFIX

/**
 * A user made bundle of folders shown as one tile in the folder grid. Nothing on the filesystem
 * moves - the members keep their real paths and are only drawn together.
 *
 * [paths] is ordered, and the order is the user's: the first four are what the tile's collage
 * shows, and dragging inside the group is what rewrites it.
 */
data class FolderGroup(
    var id: Long = 0L,
    var name: String = "",
    var paths: MutableList<String> = mutableListOf()
) {
    /**
     * What the group stands under in the folder grid. Selection, pinning and the custom folder
     * order are all keyed by a Directory's path, so giving the tile one of these lets it travel
     * through every one of them with no case of its own.
     */
    fun syntheticPath() = "$FOLDER_GROUP_PATH_PREFIX$id"
}
