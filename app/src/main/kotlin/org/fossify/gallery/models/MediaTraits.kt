package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.fossify.gallery.helpers.PhotoTraits

/**
 * What a photo's metadata says about it for the search's options - the camera it came from, whether
 * that was the front one, whether it is a panorama - remembered so the options do not have to open
 * every photo. A cache like the ratings one: the file is the authority, and [lastModified] and [size]
 * are what it looked like when it was read, so any change to either has it read again.
 *
 * Both paths are stored lowercased, matching how the rating cache keys its rows.
 */
@Entity(tableName = "media_traits")
data class MediaTraits(
    @PrimaryKey @ColumnInfo(name = "full_path") var fullPath: String,
    @ColumnInfo(name = "parent_path") var parentPath: String,
    @ColumnInfo(name = "last_modified") var lastModified: Long,
    @ColumnInfo(name = "size") var size: Long,
    /** The camera, as the search names it; empty for a photo that does not say. */
    @ColumnInfo(name = "device") var device: String,
    @ColumnInfo(name = "is_selfie") var isSelfie: Boolean,
    @ColumnInfo(name = "is_panorama") var isPanorama: Boolean,
) {
    fun toTraits() = PhotoTraits(device, isSelfie, isPanorama)
}
