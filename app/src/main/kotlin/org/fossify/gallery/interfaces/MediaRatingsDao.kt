package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.gallery.models.MediaRating

@Dao
interface MediaRatingsDao {
    @Query("SELECT * FROM media_ratings")
    fun getAll(): List<MediaRating>

    @Query("SELECT * FROM media_ratings WHERE parent_path = :parentPath")
    fun getFolderRatings(parentPath: String): List<MediaRating>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(ratings: List<MediaRating>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(rating: MediaRating)

    @Query("DELETE FROM media_ratings WHERE full_path = :path")
    fun deletePath(path: String)

    @Query("UPDATE media_ratings SET full_path = :newPath, parent_path = :newParentPath WHERE full_path = :oldPath")
    fun updatePath(newPath: String, newParentPath: String, oldPath: String)
}
