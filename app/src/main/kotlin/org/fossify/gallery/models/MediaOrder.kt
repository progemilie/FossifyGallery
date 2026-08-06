package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One entry of a user defined media order. A folder's order is the set of rows sharing a
 * [folderPath], read back sorted by [position]. Kept apart from the media table on purpose - media
 * rows are dropped and reinserted on rescans, the order the user arranged has to outlive that.
 */
@Entity(
    tableName = "media_order",
    primaryKeys = ["folder_path", "full_path"],
    indices = [Index(value = ["folder_path"])]
)
data class MediaOrder(
    @ColumnInfo(name = "folder_path") var folderPath: String,
    @ColumnInfo(name = "full_path") var fullPath: String,
    @ColumnInfo(name = "position") var position: Int
)
