package org.fossify.gallery.models

import android.content.Context
import androidx.room.*
import com.bumptech.glide.signature.ObjectKey
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.helpers.*
import org.fossify.gallery.helpers.FOLDER_GROUP_PATH_PREFIX
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.TransformedMedia

@Entity(tableName = "directories", indices = [Index(value = ["path"], unique = true)])
data class Directory(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "path") var path: String,
    @ColumnInfo(name = "thumbnail") var tmb: String,
    @ColumnInfo(name = "filename") var name: String,
    @ColumnInfo(name = "media_count") var mediaCnt: Int,
    @ColumnInfo(name = "last_modified") var modified: Long,
    @ColumnInfo(name = "date_taken") var taken: Long,
    @ColumnInfo(name = "size") var size: Long,
    @ColumnInfo(name = "location") var location: Int,
    @ColumnInfo(name = "media_types") var types: Int,
    @ColumnInfo(name = "sort_value") var sortValue: String,

    // used with "Group direct subfolders" enabled
    @Ignore var subfoldersCount: Int = 0,
    @Ignore var subfoldersMediaCount: Int = 0,
    @Ignore var containsMediaFilesDirectly: Boolean = true,

    // the folders standing under this tile when it is a folder group, empty for a real folder.
    // never persisted - a group tile is built for display only, see extensions/FolderGroups.kt
    @Ignore var groupMembers: List<Directory> = emptyList()
) {

    constructor() : this(null, "", "", "", 0, 0L, 0L, 0L, 0, 0, "", 0, 0)

    fun getBubbleText(sorting: Int, context: Context, dateFormat: String? = null, timeFormat: String? = null) = when {
        sorting and SORT_BY_NAME != 0 -> name
        // a group's path is synthetic and means nothing to the user, its name is what it sorts on
        sorting and SORT_BY_PATH != 0 -> if (isFolderGroup()) name else path
        sorting and SORT_BY_SIZE != 0 -> size.formatSize()
        sorting and SORT_BY_DATE_MODIFIED != 0 -> modified.formatDate(context, dateFormat, timeFormat)
        sorting and SORT_BY_RANDOM != 0 -> name
        else -> taken.formatDate(context)
    }

    fun areFavorites() = path == FAVORITES

    fun isRecycleBin() = path == RECYCLE_BIN

    fun isFolderGroup() = path.startsWith(FOLDER_GROUP_PATH_PREFIX)

    // the two sentinel folders stand for a query rather than a place on disk, and a group is not a
    // folder at all - none of the three can be bundled under a folder group
    fun canBeGrouped() = !isFolderGroup() && !areFavorites() && !isRecycleBin()

    fun folderGroupId() = path.removePrefix(FOLDER_GROUP_PATH_PREFIX).toLongOrNull() ?: 0L

    // the cover is a media file in its own right, so transforming it has to change this key too -
    // the folder's own modified time doesn't move when the app rewrites a file's Exif in place
    fun getKey() = ObjectKey("$path-$modified${TransformedMedia.cacheKeySuffixFor(tmb)}")
}
