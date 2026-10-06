package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.gallery.models.MediaTraits
import org.fossify.gallery.models.Medium

@Dao
interface SearchDao {
    /** Every file the scans have put in the media table and not binned, for the search's options. */
    @Query(
        "SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, " +
            "is_favorite, deleted_ts, media_store_id, rating FROM media WHERE deleted_ts = 0"
    )
    fun getLibrary(): List<Medium>

    @Query("SELECT * FROM media_traits")
    fun getTraits(): List<MediaTraits>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTraits(traits: List<MediaTraits>)

    @Query("DELETE FROM media_traits WHERE full_path IN (:paths)")
    fun deleteTraits(paths: List<String>)

    @Query(
        "UPDATE OR REPLACE media_traits SET full_path = :newPath, parent_path = :newParentPath " +
            "WHERE full_path = :oldPath"
    )
    fun renameTraits(newPath: String, newParentPath: String, oldPath: String)
}
