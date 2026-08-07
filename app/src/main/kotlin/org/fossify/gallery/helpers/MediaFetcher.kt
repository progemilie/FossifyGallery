package org.fossify.gallery.helpers

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import android.text.format.DateFormat
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.R
import org.fossify.gallery.extensions.*
import org.fossify.gallery.models.MediaRating
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import java.io.File
import java.util.Calendar
import java.util.Locale

class MediaFetcher(val context: Context) {
    var shouldStop = false

    // on Android 11 we fetch all files at once from MediaStore and have it split by folder, use it if available
    fun getFilesFrom(
        curPath: String, isPickImage: Boolean, isPickVideo: Boolean, getProperDateTaken: Boolean, getProperLastModified: Boolean,
        getProperFileSize: Boolean, favoritePaths: ArrayList<String>, getVideoDurations: Boolean,
        lastModifieds: HashMap<String, Long>, dateTakens: HashMap<String, Long>, android11Files: HashMap<String, ArrayList<Medium>>?
    ): ArrayList<Medium> {
        val filterMedia = context.config.filterMedia
        if (filterMedia == 0) {
            return ArrayList()
        }

        val curMedia = ArrayList<Medium>()
        if (context.isPathOnOTG(curPath)) {
            if (context.hasOTGConnected()) {
                val newMedia = getMediaOnOTG(curPath, isPickImage, isPickVideo, filterMedia, favoritePaths, getVideoDurations)
                curMedia.addAll(newMedia)
            }
        } else {
            if (curPath != FAVORITES && curPath != RECYCLE_BIN && isRPlus() && !isExternalStorageManager()) {
                if (android11Files?.containsKey(curPath.lowercase(Locale.getDefault())) == true) {
                    curMedia.addAll(android11Files[curPath.lowercase(Locale.getDefault())]!!)
                } else if (android11Files == null) {
                    val files = getAndroid11FolderMedia(isPickImage, isPickVideo, favoritePaths, false, getProperDateTaken, dateTakens)
                    if (files.containsKey(curPath.lowercase(Locale.getDefault()))) {
                        curMedia.addAll(files[curPath.lowercase(Locale.getDefault())]!!)
                    }
                }
            }

            if (curMedia.isEmpty()) {
                // the maps are read, never drained, so every folder can share the caller's copies -
                // cloning them per folder meant copying every known path once per folder scanned
                val newMedia = getMediaInFolder(
                    curPath, isPickImage, isPickVideo, filterMedia, getProperDateTaken, getProperLastModified, getProperFileSize,
                    favoritePaths, getVideoDurations, lastModifieds, dateTakens
                )

                if (curPath == FAVORITES && isRPlus() && !isExternalStorageManager()) {
                    val files = getAndroid11FolderMedia(
                        isPickImage, isPickVideo, favoritePaths, true, getProperDateTaken, dateTakens
                    )
                    newMedia.forEach { newMedium ->
                        for ((folder, media) in files) {
                            media.forEach { medium ->
                                if (medium.path == newMedium.path) {
                                    newMedium.size = medium.size
                                }
                            }
                        }
                    }
                }
                curMedia.addAll(newMedia)
            }
        }

        sortMedia(curMedia, context.config.getFolderSorting(curPath), curPath)
        return curMedia
    }

    fun getFoldersToScan(): ArrayList<String> {
        return try {
            val OTGPath = context.config.OTGPath
            val folders = getLatestFileFolders()
            folders.addAll(arrayListOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString(),
                "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)}/Camera",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
            ).filter { context.getDoesFilePathExist(it, OTGPath) })

            val filterMedia = context.config.filterMedia
            val uri = Files.getContentUri("external")
            val projection = arrayOf(Images.Media.DATA)
            val selection = getSelectionQuery(filterMedia)
            val selectionArgs = getSelectionArgsQuery(filterMedia).toTypedArray()
            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            folders.addAll(parseCursor(cursor!!))

            val config = context.config
            val shouldShowHidden = config.shouldShowHidden
            val excludedPaths = if (config.temporarilyShowExcluded) {
                HashSet()
            } else {
                config.excludedFolders
            }

            val includedPaths = config.includedFolders

            val folderNoMediaStatuses = HashMap<String, Boolean>()
            val distinctPathsMap = HashMap<String, String>()
            val distinctPaths = folders.distinctBy {
                when {
                    distinctPathsMap.containsKey(it) -> distinctPathsMap[it]
                    else -> {
                        val distinct = it.getDistinctPath()
                        distinctPathsMap[it.getParentPath()] = distinct.getParentPath()
                        distinct
                    }
                }
            }

            val noMediaFolders = context.getNoMediaFoldersSync()
            noMediaFolders.forEach { folder ->
                folderNoMediaStatuses["$folder/$NOMEDIA"] = true
            }

            distinctPaths.filter {
                it.shouldFolderBeVisible(excludedPaths, includedPaths, shouldShowHidden, folderNoMediaStatuses) { path, hasNoMedia ->
                    folderNoMediaStatuses[path] = hasNoMedia
                }
            }.toMutableList() as ArrayList<String>
        } catch (e: Exception) {
            ArrayList()
        }
    }

    private fun getLatestFileFolders(): LinkedHashSet<String> {
        val uri = Files.getContentUri("external")
        val projection = arrayOf(Images.ImageColumns.DATA)
        val parents = LinkedHashSet<String>()
        var cursor: Cursor? = null
        try {
            if (isRPlus()) {
                val bundle = Bundle().apply {
                    putInt(ContentResolver.QUERY_ARG_LIMIT, 10)
                    putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(BaseColumns._ID))
                    putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                }

                cursor = context.contentResolver.query(uri, projection, bundle, null)
                if (cursor?.moveToFirst() == true) {
                    do {
                        val path = cursor.getStringValue(Images.ImageColumns.DATA) ?: continue
                        parents.add(path.getParentPath())
                    } while (cursor.moveToNext())
                }
            } else {
                val sorting = "${BaseColumns._ID} DESC LIMIT 10"
                cursor = context.contentResolver.query(uri, projection, null, null, sorting)
                if (cursor?.moveToFirst() == true) {
                    do {
                        val path = cursor.getStringValue(Images.ImageColumns.DATA) ?: continue
                        parents.add(path.getParentPath())
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return parents
    }

    private fun getSelectionQuery(filterMedia: Int): String {
        val query = StringBuilder()
        if (filterMedia and TYPE_IMAGES != 0) {
            photoExtensions.forEach {
                query.append("${Images.Media.DATA} LIKE ? OR ")
            }
        }

        if (filterMedia and TYPE_PORTRAITS != 0) {
            query.append("${Images.Media.DATA} LIKE ? OR ")
            query.append("${Images.Media.DATA} LIKE ? OR ")
        }

        if (filterMedia and TYPE_VIDEOS != 0) {
            videoExtensions.forEach {
                query.append("${Images.Media.DATA} LIKE ? OR ")
            }
        }

        if (filterMedia and TYPE_GIFS != 0) {
            query.append("${Images.Media.DATA} LIKE ? OR ")
        }

        if (filterMedia and TYPE_RAWS != 0) {
            rawExtensions.forEach {
                query.append("${Images.Media.DATA} LIKE ? OR ")
            }
        }

        if (filterMedia and TYPE_SVGS != 0) {
            query.append("${Images.Media.DATA} LIKE ? OR ")
        }

        return query.toString().trim().removeSuffix("OR")
    }

    private fun getSelectionArgsQuery(filterMedia: Int): ArrayList<String> {
        val args = ArrayList<String>()
        if (filterMedia and TYPE_IMAGES != 0) {
            photoExtensions.forEach {
                args.add("%$it")
            }
        }

        if (filterMedia and TYPE_PORTRAITS != 0) {
            args.add("%.jpg")
            args.add("%.jpeg")
        }

        if (filterMedia and TYPE_VIDEOS != 0) {
            videoExtensions.forEach {
                args.add("%$it")
            }
        }

        if (filterMedia and TYPE_GIFS != 0) {
            args.add("%.gif")
        }

        if (filterMedia and TYPE_RAWS != 0) {
            rawExtensions.forEach {
                args.add("%$it")
            }
        }

        if (filterMedia and TYPE_SVGS != 0) {
            args.add("%.svg")
        }

        return args
    }

    private fun parseCursor(cursor: Cursor): LinkedHashSet<String> {
        val foldersToIgnore = arrayListOf("/storage/emulated/legacy")
        val config = context.config
        val includedFolders = config.includedFolders
        val OTGPath = config.OTGPath
        val foldersToScan = config.everShownFolders.filter { it == FAVORITES || it == RECYCLE_BIN || context.getDoesFilePathExist(it, OTGPath) }.toHashSet()

        cursor.use {
            if (cursor.moveToFirst()) {
                do {
                    val path = cursor.getStringValue(Images.Media.DATA)
                    val parentPath = File(path).parent ?: continue
                    if (!includedFolders.contains(parentPath) && !foldersToIgnore.contains(parentPath)) {
                        foldersToScan.add(parentPath)
                    }
                } while (cursor.moveToNext())
            }
        }

        includedFolders.forEach {
            addFolder(foldersToScan, it)
        }

        return foldersToScan.toMutableSet() as LinkedHashSet<String>
    }

    private fun addFolder(curFolders: HashSet<String>, folder: String) {
        curFolders.add(folder)
        val files = File(folder).listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                addFolder(curFolders, file.absolutePath)
            }
        }
    }

    private fun getMediaInFolder(
        folder: String, isPickImage: Boolean, isPickVideo: Boolean, filterMedia: Int, getProperDateTaken: Boolean,
        getProperLastModified: Boolean, getProperFileSize: Boolean, favoritePaths: ArrayList<String>,
        getVideoDurations: Boolean, lastModifieds: HashMap<String, Long>, dateTakens: HashMap<String, Long>
    ): ArrayList<Medium> {
        val media = ArrayList<Medium>()
        val isRecycleBin = folder == RECYCLE_BIN
        val deletedMedia = if (isRecycleBin) {
            context.getUpdatedDeletedMedia()
        } else {
            ArrayList()
        }

        val config = context.config
        val checkProperFileSize = getProperFileSize || config.fileLoadingPriority == PRIORITY_COMPROMISE
        val checkFileExistence = config.fileLoadingPriority == PRIORITY_VALIDITY
        val showHidden = config.shouldShowHidden
        val showPortraits = filterMedia and TYPE_PORTRAITS != 0
        val fileSizes = if (checkProperFileSize || checkFileExistence) getFolderSizes(folder) else HashMap()
        // hashed once instead of scanning the list again for every single file below
        val favorites = favoritePaths.toHashSet()

        val ratings = RatingScan(folder)

        val files = when (folder) {
            FAVORITES -> favoritePaths.filter { showHidden || !it.contains("/.") }.map { File(it) }.toMutableList() as ArrayList<File>
            RECYCLE_BIN -> deletedMedia.map { File(it.path) }.toMutableList() as ArrayList<File>
            else -> File(folder).listFiles()?.toMutableList() ?: return media
        }

        for (curFile in files) {
            var file = curFile
            if (shouldStop) {
                break
            }

            var path = file.absolutePath
            var isPortrait = false
            val isImage = path.isImageFast()
            val isVideo = if (isImage) false else path.isVideoFast()
            val isGif = if (isImage || isVideo) false else path.isGif()
            val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
            val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()

            if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) {
                if (showPortraits && file.name.startsWith("img_", true) && file.isDirectory) {
                    val portraitFiles = file.listFiles() ?: continue
                    val cover = portraitFiles.firstOrNull { it.name.contains("cover", true) } ?: portraitFiles.firstOrNull()
                    if (cover != null && !files.contains(cover)) {
                        file = cover
                        path = cover.absolutePath
                        isPortrait = true
                    } else {
                        continue
                    }
                } else {
                    continue
                }
            }

            if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0))
                continue

            if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0))
                continue

            if (isGif && filterMedia and TYPE_GIFS == 0)
                continue

            if (isRaw && filterMedia and TYPE_RAWS == 0)
                continue

            if (isSvg && filterMedia and TYPE_SVGS == 0)
                continue

            val filename = file.name
            if (!showHidden && filename.startsWith('.'))
                continue

            var size = 0L
            if (checkProperFileSize || checkFileExistence) {
                var newSize = fileSizes.remove(path)
                if (newSize == null) {
                    newSize = file.length()
                }
                size = newSize
            }

            if ((checkProperFileSize || checkFileExistence) && size <= 0L) {
                continue
            }

            if (checkFileExistence && (!file.exists() || !file.isFile)) {
                continue
            }

            if (isRecycleBin) {
                deletedMedia.firstOrNull { it.path == path }?.apply {
                    media.add(this)
                }
            } else {
                var lastModified: Long
                var newLastModified = lastModifieds[path]
                if (newLastModified == null) {
                    newLastModified = if (getProperLastModified) {
                        file.lastModified()
                    } else {
                        0L
                    }
                }
                lastModified = newLastModified

                var dateTaken = lastModified
                val videoDuration = if (getVideoDurations && isVideo) context.getDuration(path) ?: 0 else 0

                if (getProperDateTaken) {
                    var newDateTaken = dateTakens[path]
                    if (newDateTaken == null) {
                        newDateTaken = if (getProperLastModified) {
                            lastModified
                        } else {
                            file.lastModified()
                        }
                    }
                    dateTaken = newDateTaken
                }

                val type = mediaTypeOf(isVideo, isGif, isRaw, isSvg, isPortrait)
                val isFavorite = favorites.contains(path)
                val medium = Medium(
                    null, filename, path, file.parent, lastModified, dateTaken, size, type, videoDuration, isFavorite,
                    0L, 0L, ratings.of(file)
                )
                media.add(medium)
            }
        }

        ratings.persist()
        return media
    }

    /**
     * The rating side of a scan. It answers what each file is rated out of the cache, opens the file
     * only when the cache has nothing that still describes it, and remembers everything it had to
     * read so the next scan does not have to.
     *
     * Reading the metadata is the one genuinely expensive thing a scan can do per item, so it only
     * happens at all when something - a thumbnail badge, a rating sort - is going to use the answer.
     */
    private inner class RatingScan(private val folder: String) {
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
                // the favorites and recycle bin views collect files from all over, and the Android
                // 11 query walks the whole of MediaStore - none of them has one parent path to
                // narrow the lookup down to
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

    private fun mediaTypeOf(
        isVideo: Boolean, isGif: Boolean, isRaw: Boolean, isSvg: Boolean, isPortrait: Boolean
    ) = when {
        isVideo -> TYPE_VIDEOS
        isGif -> TYPE_GIFS
        isRaw -> TYPE_RAWS
        isSvg -> TYPE_SVGS
        isPortrait -> TYPE_PORTRAITS
        else -> TYPE_IMAGES
    }

    fun getAndroid11FolderMedia(
        isPickImage: Boolean,
        isPickVideo: Boolean,
        favoritePaths: ArrayList<String>,
        getFavoritePathsOnly: Boolean,
        getProperDateTaken: Boolean,
        dateTakens: HashMap<String, Long>
    ): HashMap<String, ArrayList<Medium>> {
        val media = HashMap<String, ArrayList<Medium>>()
        if (!isRPlus() || Environment.isExternalStorageManager()) {
            return media
        }

        val filterMedia = context.config.filterMedia
        val showHidden = context.config.shouldShowHidden
        // hashed once instead of scanning the list again for every MediaStore row below
        val favorites = favoritePaths.toHashSet()

        val ratings = RatingScan(FAVORITES)

        val projection = arrayOf(
            Images.Media._ID,
            Images.Media.DISPLAY_NAME,
            Images.Media.DATA,
            Images.Media.DATE_MODIFIED,
            Images.Media.DATE_TAKEN,
            Images.Media.SIZE,
            MediaStore.MediaColumns.DURATION
        )

        val uri = Files.getContentUri("external")

        context.queryCursor(uri, projection) { cursor ->
            if (shouldStop) {
                return@queryCursor
            }

            try {
                val mediaStoreId = cursor.getLongValue(Images.Media._ID)
                val filename = cursor.getStringValue(Images.Media.DISPLAY_NAME)
                val path = cursor.getStringValue(Images.Media.DATA)
                if (getFavoritePathsOnly && !favorites.contains(path)) {
                    return@queryCursor
                }

                val isPortrait = false
                val isImage = path.isImageFast()
                val isVideo = if (isImage) false else path.isVideoFast()
                val isGif = if (isImage || isVideo) false else path.isGif()
                val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
                val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()

                if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) {
                    return@queryCursor
                }

                if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0))
                    return@queryCursor

                if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0))
                    return@queryCursor

                if (isGif && filterMedia and TYPE_GIFS == 0)
                    return@queryCursor

                if (isRaw && filterMedia and TYPE_RAWS == 0)
                    return@queryCursor

                if (isSvg && filterMedia and TYPE_SVGS == 0)
                    return@queryCursor

                if (!showHidden && filename.startsWith('.'))
                    return@queryCursor

                val size = cursor.getLongValue(Images.Media.SIZE)
                if (size <= 0L) {
                    return@queryCursor
                }

                val type = mediaTypeOf(isVideo, isGif, isRaw, isSvg, isPortrait)

                val lastModified = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000
                var dateTaken = cursor.getLongValue(Images.Media.DATE_TAKEN)

                if (getProperDateTaken) {
                    dateTaken = dateTakens[path] ?: lastModified
                }

                if (dateTaken == 0L) {
                    dateTaken = lastModified
                }

                val videoDuration = Math.round(cursor.getIntValue(MediaStore.MediaColumns.DURATION) / 1000.toDouble()).toInt()
                val isFavorite = favoritePaths.contains(path)
                val medium =
                    Medium(
                        null, filename, path, path.getParentPath(), lastModified, dateTaken, size, type, videoDuration,
                        isFavorite, 0L, mediaStoreId, ratings.of(File(path))
                    )
                val parent = medium.parentPath.lowercase(Locale.getDefault())
                val currentFolderMedia = media[parent]
                if (currentFolderMedia == null) {
                    media[parent] = ArrayList<Medium>()
                }

                media[parent]?.add(medium)
            } catch (e: Exception) {
            }
        }

        ratings.persist()
        return media
    }

    private fun getMediaOnOTG(
        folder: String, isPickImage: Boolean, isPickVideo: Boolean, filterMedia: Int, favoritePaths: ArrayList<String>,
        getVideoDurations: Boolean
    ): ArrayList<Medium> {
        val media = ArrayList<Medium>()
        val files = context.getDocumentFile(folder)?.listFiles() ?: return media
        val checkFileExistence = context.config.fileLoadingPriority == PRIORITY_VALIDITY
        val showHidden = context.config.shouldShowHidden
        val OTGPath = context.config.OTGPath
        // hashed once instead of scanning the list again for every single file below
        val favorites = favoritePaths.toHashSet()

        for (file in files) {
            if (shouldStop) {
                break
            }

            val filename = file.name ?: continue
            val isImage = filename.isImageFast()
            val isVideo = if (isImage) false else filename.isVideoFast()
            val isGif = if (isImage || isVideo) false else filename.isGif()
            val isRaw = if (isImage || isVideo || isGif) false else filename.isRawFast()
            val isSvg = if (isImage || isVideo || isGif || isRaw) false else filename.isSvg()

            if (!isImage && !isVideo && !isGif && !isRaw && !isSvg)
                continue

            if (isVideo && (isPickImage || filterMedia and TYPE_VIDEOS == 0))
                continue

            if (isImage && (isPickVideo || filterMedia and TYPE_IMAGES == 0))
                continue

            if (isGif && filterMedia and TYPE_GIFS == 0)
                continue

            if (isRaw && filterMedia and TYPE_RAWS == 0)
                continue

            if (isSvg && filterMedia and TYPE_SVGS == 0)
                continue

            if (!showHidden && filename.startsWith('.'))
                continue

            val size = file.length()
            if (size <= 0L || (checkFileExistence && !context.getDoesFilePathExist(file.uri.toString(), OTGPath)))
                continue

            val dateTaken = file.lastModified()
            val dateModified = file.lastModified()

            val type = mediaTypeOf(isVideo, isGif, isRaw, isSvg, isPortrait = false)

            val path = Uri.decode(
                file.uri.toString().replaceFirst("${context.config.OTGTreeUri}/document/${context.config.OTGPartition}%3A", "${context.config.OTGPath}/")
            )
            val videoDuration = if (getVideoDurations) context.getDuration(path) ?: 0 else 0
            val isFavorite = favorites.contains(path)
            val medium = Medium(null, filename, path, folder, dateModified, dateTaken, size, type, videoDuration, isFavorite, 0L, 0L)
            media.add(medium)
        }

        return media
    }

    fun getFolderDateTakens(folder: String): HashMap<String, Long> {
        val dateTakens = HashMap<String, Long>()
        if (folder != FAVORITES) {
            val projection = arrayOf(
                Images.Media.DISPLAY_NAME,
                Images.Media.DATE_TAKEN
            )

            val uri = Files.getContentUri("external")
            val selection = "${Images.Media.DATA} LIKE ? AND ${Images.Media.DATA} NOT LIKE ?"
            val selectionArgs = arrayOf("$folder/%", "$folder/%/%")

            context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
                try {
                    val dateTaken = cursor.getLongValue(Images.Media.DATE_TAKEN)
                    if (dateTaken != 0L) {
                        val name = cursor.getStringValue(Images.Media.DISPLAY_NAME)
                        dateTakens["$folder/$name"] = dateTaken
                    }
                } catch (e: Exception) {
                }
            }
        }

        val dateTakenValues = try {
            if (folder == FAVORITES) {
                context.dateTakensDB.getAllDateTakens()
            } else {
                context.dateTakensDB.getDateTakensFromPath(folder)
            }
        } catch (e: Exception) {
            return dateTakens
        }

        dateTakenValues.forEach {
            dateTakens[it.fullPath] = it.taken
        }

        return dateTakens
    }

    fun getDateTakens(): HashMap<String, Long> {
        val dateTakens = HashMap<String, Long>()
        val projection = arrayOf(
            Images.Media.DATA,
            Images.Media.DATE_TAKEN
        )

        val uri = Files.getContentUri("external")

        try {
            context.queryCursor(uri, projection) { cursor ->
                try {
                    val dateTaken = cursor.getLongValue(Images.Media.DATE_TAKEN)
                    if (dateTaken != 0L) {
                        val path = cursor.getStringValue(Images.Media.DATA)
                        dateTakens[path] = dateTaken
                    }
                } catch (e: Exception) {
                }
            }

            val dateTakenValues = context.dateTakensDB.getAllDateTakens()

            dateTakenValues.forEach {
                dateTakens[it.fullPath] = it.taken
            }
        } catch (e: Exception) {
        }

        return dateTakens
    }

    fun getFolderLastModifieds(folder: String): HashMap<String, Long> {
        val lastModifieds = HashMap<String, Long>()
        if (folder != FAVORITES) {
            val projection = arrayOf(
                Images.Media.DISPLAY_NAME,
                Images.Media.DATE_MODIFIED
            )

            val uri = Files.getContentUri("external")
            val selection = "${Images.Media.DATA} LIKE ? AND ${Images.Media.DATA} NOT LIKE ?"
            val selectionArgs = arrayOf("$folder/%", "$folder/%/%")

            context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
                try {
                    val lastModified = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000
                    if (lastModified != 0L) {
                        val name = cursor.getStringValue(Images.Media.DISPLAY_NAME)
                        lastModifieds["$folder/$name"] = lastModified
                    }
                } catch (e: Exception) {
                }
            }
        }

        return lastModifieds
    }

    fun getLastModifieds(): HashMap<String, Long> {
        val lastModifieds = HashMap<String, Long>()
        val projection = arrayOf(
            Images.Media.DATA,
            Images.Media.DATE_MODIFIED
        )

        val uri = Files.getContentUri("external")

        try {
            context.queryCursor(uri, projection) { cursor ->
                try {
                    val lastModified = cursor.getLongValue(Images.Media.DATE_MODIFIED) * 1000
                    if (lastModified != 0L) {
                        val path = cursor.getStringValue(Images.Media.DATA)
                        lastModifieds[path] = lastModified
                    }
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }

        return lastModifieds
    }

    private fun getFolderSizes(folder: String): HashMap<String, Long> {
        val sizes = HashMap<String, Long>()
        if (folder != FAVORITES) {
            val projection = arrayOf(
                Images.Media.DISPLAY_NAME,
                Images.Media.SIZE
            )

            val uri = Files.getContentUri("external")
            val selection = "${Images.Media.DATA} LIKE ? AND ${Images.Media.DATA} NOT LIKE ?"
            val selectionArgs = arrayOf("$folder/%", "$folder/%/%")

            context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
                try {
                    val size = cursor.getLongValue(Images.Media.SIZE)
                    if (size != 0L) {
                        val name = cursor.getStringValue(Images.Media.DISPLAY_NAME)
                        sizes["$folder/$name"] = size
                    }
                } catch (e: Exception) {
                }
            }
        }

        return sizes
    }

    fun sortMedia(media: ArrayList<Medium>, sorting: Int, path: String = "") {
        if (sorting and SORT_BY_RANDOM != 0) {
            media.shuffle()
            return
        }

        if (sorting and SORT_BY_CUSTOM != 0) {
            sortMediaByCustomOrder(media, path)
            return
        }

        media.sortWith { o1, o2 -> compareMedia(o1 as Medium, o2 as Medium, sorting) }
    }

    private fun compareMedia(o1: Medium, o2: Medium, sorting: Int): Int {
        var result = compareBySortKey(o1, o2, sorting)
        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }

        // a rating on its own leaves whole blocks of items tied - every five star photo compares
        // equal to every other - so fall back to newest first within each rating, the way Aves
        // does. after the sign flip, so reversing the ratings does not also flip the dates inside
        // them
        if (result == 0 && sorting and SORT_BY_RATING != 0) {
            result = o2.taken.compareTo(o1.taken)
        }

        // files that tie on the sort key - a burst of shots sharing a timestamp, or a whole folder
        // with no Exif dates at all - would otherwise keep whatever order the MediaStore cursor
        // happened to return, which is not the same from one scan to the next. that moves items
        // around the grid and, since a folder's cover is simply its first item, swaps album covers
        // for no reason. the path is unique, so it settles every tie
        if (result == 0) {
            result = o1.path.compareTo(o2.path)
        }

        return result
    }

    private fun compareBySortKey(o1: Medium, o2: Medium, sorting: Int): Int {
        val numeric = sorting and SORT_USE_NUMERIC_VALUE != 0
        return when {
            sorting and SORT_BY_NAME != 0 -> {
                val name1 = o1.name.normalizeString().lowercase(Locale.getDefault())
                val name2 = o2.name.normalizeString().lowercase(Locale.getDefault())
                if (numeric) AlphanumericComparator().compare(name1, name2) else name1.compareTo(name2)
            }

            sorting and SORT_BY_PATH != 0 -> {
                val path1 = o1.path.lowercase(Locale.getDefault())
                val path2 = o2.path.lowercase(Locale.getDefault())
                if (numeric) AlphanumericComparator().compare(path1, path2) else path1.compareTo(path2)
            }

            sorting and SORT_BY_SIZE != 0 -> o1.size.compareTo(o2.size)
            sorting and SORT_BY_DATE_MODIFIED != 0 -> o1.modified.compareTo(o2.modified)
            sorting and SORT_BY_RATING != 0 -> o1.rating.compareTo(o2.rating)
            else -> o1.taken.compareTo(o2.taken)
        }
    }

    /**
     * Arranges [media] the way the user last dragged the items of [path] into. Anything the saved
     * order does not know about - typically media added after the ordering was made - keeps its
     * relative order and lands at the end, the sort being stable. Media stays untouched when the
     * folder has no saved order, there is nothing better to fall back to than what it came in as.
     */
    private fun sortMediaByCustomOrder(media: ArrayList<Medium>, path: String) {
        val pathToUse = path.ifEmpty { SHOW_ALL }
        val positions = context.getCustomMediaOrder(pathToUse)
        if (positions.isEmpty()) {
            return
        }

        media.sortWith(
            compareBy<Medium> { positions[it.path.lowercase(Locale.getDefault())] ?: Int.MAX_VALUE }
                // everything the saved order does not cover ties on the line above, so give the
                // tail a fixed order of its own rather than the scan's incidental one
                .thenBy { it.path }
        )
    }

    fun groupMedia(media: ArrayList<Medium>, path: String): ArrayList<ThumbnailItem> {
        val pathToCheck = if (path.isEmpty()) SHOW_ALL else path
        val sorting = context.config.getFolderSorting(pathToCheck)
        val savedGrouping = context.config.getFolderGrouping(pathToCheck)

        // sorting by rating carries its own headers - a run of five star photos followed by a run
        // of four star ones is already grouped, all it is missing is the labels - so it overrides
        // whatever grouping the folder is otherwise set to, keeping only the file count preference
        val isRatingSorting = sorting and SORT_BY_RATING != 0
        val currentGrouping = if (isRatingSorting) {
            GROUP_BY_RATING or
                (savedGrouping and GROUP_SHOW_FILE_COUNT) or
                (if (sorting and SORT_DESCENDING != 0) GROUP_DESCENDING else 0)
        } else {
            savedGrouping
        }

        // a hand made order cuts across whatever groups would be formed, show it as the flat list it is
        val isCustomSorting = sorting and SORT_BY_CUSTOM != 0
        if (currentGrouping and GROUP_BY_NONE != 0 || isCustomSorting) {
            return media as ArrayList<ThumbnailItem>
        }

        val thumbnailItems = ArrayList<ThumbnailItem>()
        if (context.config.scrollHorizontally) {
            media.mapTo(thumbnailItems) { it }
            return thumbnailItems
        }

        val mediumGroups = LinkedHashMap<String, ArrayList<Medium>>()
        media.forEach {
            val key = it.getGroupingKey(currentGrouping)
            if (!mediumGroups.containsKey(key)) {
                mediumGroups[key] = ArrayList()
            }
            mediumGroups[key]!!.add(it)
        }

        val sortDescending = currentGrouping and GROUP_DESCENDING != 0
        val sorted = if (currentGrouping and GROUP_BY_LAST_MODIFIED_DAILY != 0 || currentGrouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0 ||
            currentGrouping and GROUP_BY_DATE_TAKEN_DAILY != 0 || currentGrouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0
        ) {
            mediumGroups.toSortedMap(if (sortDescending) compareByDescending {
                it.toLongOrNull() ?: 0L
            } else {
                compareBy { it.toLongOrNull() ?: 0L }
            })
        } else {
            mediumGroups.toSortedMap(if (sortDescending) compareByDescending { it } else compareBy { it })
        }

        mediumGroups.clear()
        for ((key, value) in sorted) {
            mediumGroups[key] = value
        }

        val today = formatDate(System.currentTimeMillis().toString(), true)
        val yesterday = formatDate((System.currentTimeMillis() - DAY_SECONDS * 1000).toString(), true)
        for ((key, value) in mediumGroups) {
            var currentGridPosition = 0
            val sectionKey = getFormattedKey(key, currentGrouping, today, yesterday, value.size)
            thumbnailItems.add(ThumbnailSection(sectionKey))

            value.forEach {
                it.gridPosition = currentGridPosition++
            }

            thumbnailItems.addAll(value)
        }

        return thumbnailItems
    }

    private fun getFormattedKey(key: String, grouping: Int, today: String, yesterday: String, count: Int): String {
        var result = when {
            grouping and GROUP_BY_LAST_MODIFIED_DAILY != 0 || grouping and GROUP_BY_DATE_TAKEN_DAILY != 0 -> getFinalDate(
                formatDate(key, true),
                today,
                yesterday
            )

            grouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0 || grouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0 -> formatDate(key, false)
            grouping and GROUP_BY_FILE_TYPE != 0 -> getFileTypeString(key)
            grouping and GROUP_BY_RATING != 0 -> context.getRatingLabel(key.toIntOrNull() ?: 0)
            grouping and GROUP_BY_EXTENSION != 0 -> key.uppercase(Locale.getDefault())
            grouping and GROUP_BY_FOLDER != 0 -> context.humanizePath(key)
            else -> key
        }

        if (result.isEmpty()) {
            result = context.getString(org.fossify.commons.R.string.unknown)
        }

        return if (grouping and GROUP_SHOW_FILE_COUNT != 0) {
            "$result ($count)"
        } else {
            result
        }
    }

    private fun getFinalDate(date: String, today: String, yesterday: String): String {
        return when (date) {
            today -> context.getString(org.fossify.commons.R.string.today)
            yesterday -> context.getString(org.fossify.commons.R.string.yesterday)
            else -> date
        }
    }

    private fun formatDate(timestamp: String, showDay: Boolean): String {
        return if (timestamp.areDigitsOnly()) {
            val cal = Calendar.getInstance(Locale.ENGLISH)
            cal.timeInMillis = timestamp.toLong()
            val format = if (showDay) context.config.dateFormat else "MMMM yyyy"
            DateFormat.format(format, cal).toString()
        } else {
            ""
        }
    }

    private fun getFileTypeString(key: String): String {
        val stringId = when (key.toInt()) {
            TYPE_IMAGES -> R.string.images
            TYPE_VIDEOS -> R.string.videos
            TYPE_GIFS -> R.string.gifs
            TYPE_RAWS -> R.string.raw_images
            TYPE_SVGS -> R.string.svgs
            else -> R.string.portraits
        }
        return context.getString(stringId)
    }
}
