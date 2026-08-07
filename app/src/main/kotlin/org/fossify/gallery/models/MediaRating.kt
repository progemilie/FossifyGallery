package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A remembered star rating, so a media scan does not have to open every photo to find out what it
 * is. The rating itself lives in the file's XMP packet (see helpers/XmpRating.kt) - this is only a
 * cache of it, and the file always wins.
 *
 * Kept apart from the media table for the same reason the custom order is: media rows are dropped
 * and reinserted on every rescan, and re-reading the metadata of a whole library each time is
 * exactly the cost this table exists to avoid.
 *
 * [lastModified] and [size] are what the file looked like when the rating was read. Any change to
 * either means something else has been in there, so the entry is re-read rather than trusted.
 * Both paths are stored lowercased, matching how the custom order keys its folders.
 */
@Entity(tableName = "media_ratings", indices = [Index(value = ["parent_path"])])
data class MediaRating(
    @PrimaryKey @ColumnInfo(name = "full_path") var fullPath: String,
    @ColumnInfo(name = "parent_path") var parentPath: String,
    @ColumnInfo(name = "rating") var rating: Int,
    @ColumnInfo(name = "last_modified") var lastModified: Long,
    @ColumnInfo(name = "size") var size: Long
)
