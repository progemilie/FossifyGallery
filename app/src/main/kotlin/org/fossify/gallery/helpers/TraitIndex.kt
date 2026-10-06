package org.fossify.gallery.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.fossify.commons.extensions.getParentPath
import org.fossify.gallery.extensions.searchDB
import org.fossify.gallery.models.MediaTraits
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** How many photos are read between handing what was found to the search, on a library's first pass. */
private const val BATCH = 200

/** SQLite's limit on how many values one statement can be handed. */
private const val MAX_QUERY_ARGS = 900

/**
 * What the search's Selfies, Panorama and Device options need out of each photo - see [TraitReader] -
 * read in the background and kept in Room ([MediaTraits]), the way ratings are.
 *
 * Opening every photo is the expensive part, so it is done after a scan rather than in it, one pass
 * at a time on a thread of its own, and a pass only opens what is new or has changed since it was
 * read. A library's first pass hands over what it has found as it goes, so the options fill in.
 */
object TraitIndex {
    /** Told on the main thread whenever a pass has read something new. */
    fun interface Listener {
        fun onTraitsIndexed()
    }

    // weakly: a screen that goes away mid search leaves nothing behind to be told
    private val listeners = Collections.newSetFromMap(WeakHashMap<Listener, Boolean>())
    private val mainThread = Handler(Looper.getMainLooper())
    private val isQueued = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "TraitIndex").apply { priority = Thread.MIN_PRIORITY }
    }

    // by lowercased path, as read so far
    @Volatile
    private var known: Map<String, PhotoTraits>? = null

    /** What has been read so far. Blocking the first time it is asked, which reads the table in. */
    fun lookup(context: Context): TraitLookup {
        val traits = known ?: load(context)
        return TraitLookup { path -> traits[path.lowercase(Locale.getDefault())] }
    }

    fun addListener(listener: Listener) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: Listener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    /** Reads every photo in the library that has not been read since it last changed. Returns at once. */
    fun refresh(context: Context) {
        // one pass waiting is all there ever needs to be: it reads whatever the library holds by then
        if (!isQueued.compareAndSet(false, true)) {
            return
        }

        val appContext = context.applicationContext
        worker.execute {
            isQueued.set(false)
            try {
                Perf.section("traits.pass") { index(appContext) }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun index(context: Context) {
        val dao = context.searchDB
        val rows = dao.getTraits().associateBy { it.fullPath }
        val photos = dao.getLibrary().filter { it.type == TYPE_IMAGES || it.type == TYPE_RAWS }
        val fresh = ArrayList<MediaTraits>()
        for (photo in photos) {
            val file = File(photo.path)
            val key = photo.path.lowercase(Locale.getDefault())
            val lastModified = file.lastModified()
            val size = file.length()
            val cached = rows[key]
            val isUpToDate = cached != null && cached.lastModified == lastModified && cached.size == size
            if (size == 0L || isUpToDate) {
                continue
            }

            val traits = Perf.section("traits.read") { TraitReader.read(photo.path) }
            val parent = photo.path.getParentPath().lowercase(Locale.getDefault())
            fresh.add(MediaTraits(key, parent, lastModified, size, traits.device, traits.isSelfie, traits.isPanorama))
            if (fresh.size == BATCH) {
                persist(context, fresh)
                fresh.clear()
            }
        }

        persist(context, fresh)

        // gone from the library, or never going to be asked about again
        val library = photos.mapTo(HashSet()) { it.path.lowercase(Locale.getDefault()) }
        rows.keys.filterNot { it in library }.chunked(MAX_QUERY_ARGS).forEach { dao.deleteTraits(it) }
    }

    private fun persist(context: Context, fresh: List<MediaTraits>) {
        if (fresh.isEmpty()) {
            return
        }

        context.searchDB.insertTraits(fresh)
        val traits = HashMap(known ?: load(context))
        fresh.forEach { traits[it.fullPath] = it.toTraits() }
        known = traits
        mainThread.post {
            synchronized(listeners) { listeners.toList() }.forEach { it.onTraitsIndexed() }
        }
    }

    /** A file renamed: what was read of it holds under its new name. Blocking. */
    fun renamed(context: Context, oldPath: String, newPath: String) {
        val oldKey = oldPath.lowercase(Locale.getDefault())
        val newKey = newPath.lowercase(Locale.getDefault())
        context.searchDB.renameTraits(newKey, newPath.getParentPath().lowercase(Locale.getDefault()), oldKey)
        known?.let { traits ->
            val moved = traits[oldKey] ?: return
            known = HashMap(traits).apply {
                remove(oldKey)
                put(newKey, moved)
            }
        }
    }

    @Synchronized
    private fun load(context: Context): Map<String, PhotoTraits> = known ?: try {
        context.searchDB.getTraits().associate { it.fullPath to it.toTraits() }
    } catch (ignored: Exception) {
        emptyMap()
    }.also { known = it }
}
