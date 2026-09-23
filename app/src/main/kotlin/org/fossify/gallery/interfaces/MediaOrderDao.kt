package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.fossify.gallery.models.MediaOrder

@Dao
interface MediaOrderDao {
    @Query("SELECT full_path FROM media_order WHERE folder_path = :folderPath ORDER BY position")
    fun getOrderedPaths(folderPath: String): List<String>

    @Query("SELECT DISTINCT folder_path FROM media_order")
    fun getOrderedFolders(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entries: List<MediaOrder>)

    @Query("DELETE FROM media_order WHERE folder_path = :folderPath")
    fun deleteFolderOrder(folderPath: String)

    // every order the file is in keeps its place, the all media grid's included
    @Query("UPDATE OR REPLACE media_order SET full_path = :newPath WHERE full_path = :oldPath COLLATE NOCASE")
    fun renamePath(oldPath: String, newPath: String)

    @Transaction
    fun replaceFolderOrder(folderPath: String, paths: List<String>) {
        deleteFolderOrder(folderPath)
        insertAll(paths.mapIndexed { index, path -> MediaOrder(folderPath, path, index) })
    }
}
