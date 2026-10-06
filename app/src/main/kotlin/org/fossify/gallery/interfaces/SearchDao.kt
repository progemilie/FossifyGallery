package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Query
import org.fossify.gallery.models.Medium

@Dao
interface SearchDao {
    /** Every file the scans have put in the media table and not binned, for Albums' search options. */
    @Query(
        "SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, " +
            "is_favorite, deleted_ts, media_store_id, rating FROM media WHERE deleted_ts = 0"
    )
    fun getLibrary(): List<Medium>
}
