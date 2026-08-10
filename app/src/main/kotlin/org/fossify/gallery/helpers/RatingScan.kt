package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.extensions.canBeRated
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFileRating
import org.fossify.gallery.extensions.mediaRatingsDB
import org.fossify.gallery.models.MediaRating
import java.io.File
import java.util.Locale

/**
 * The rating side of a media scan. It answers what each file is rated out of the cache, opens the
 * file only when the cache has nothing that still describes it, and remembers everything it had to
 * read so the next scan does not have to.
 *
 * Reading the metadata is the one genuinely expensive thing a scan can do per item, so it only
 * happens at all when something - a thumbnail badge, a rating sort - is going to use the answer.
 *
 * One of these covers one pass over [folder]: ask it [of] per file and [persist] at the end.
 */
class RatingScan(private val context: Context, private val folder: String) {
    private val wanted = context.config.showThumbnailRating ||
        context.config.getFolderSorting(folder) and SORT_BY_RATING != 0

    private val known by lazy { if (wanted) loadKnown() else emptyMap() }
    private val fresh = ArrayList<MediaRating>()

    fun of(file: File): Int {
        val path = file.absolutePath
        if (!wanted || !path.canBeRated()) {
            return 0
        }

        val lastModified = file.lastModified()
        val size = file.length()
        val key = path.lowercase(Locale.getDefault())
        val cached = known[key]
        if (cached != null && cached.lastModified == lastModified && cached.size == size) {
            return cached.rating
        }

        // a rating of 0 is worth caching too - "this file has no rating" is just as much of an
        // answer as any other, and just as expensive to work out again
        val rating = getFileRating(path)
        val parent = path.getParentPath().lowercase(Locale.getDefault())
        fresh.add(MediaRating(key, parent, rating, lastModified, size))
        return rating
    }

    fun persist() {
        if (fresh.isEmpty()) {
            return
        }

        try {
            context.mediaRatingsDB.insertAll(fresh)
        } catch (ignored: Exception) {
        }
    }

    private fun loadKnown(): Map<String, MediaRating> {
        return try {
            // the favorites and recycle bin views collect files from all over, and the Android 11
            // query walks the whole of MediaStore - none of them has one parent path to narrow the
            // lookup down to
            val rows = if (folder == FAVORITES || folder == RECYCLE_BIN) {
                context.mediaRatingsDB.getAll()
            } else {
                context.mediaRatingsDB.getFolderRatings(folder.lowercase(Locale.getDefault()))
            }

            rows.associateBy { it.fullPath }
        } catch (ignored: Exception) {
            emptyMap()
        }
    }
}
