package org.fossify.gallery.helpers

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import org.fossify.commons.extensions.getParentPath
import org.fossify.gallery.extensions.searchDB
import org.fossify.gallery.models.MediaTraits
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** How many photos are read between handing what was found to the search, on a library's first pass. */
private const val BATCH = 200

/** SQLite's limit on how many values one statement can be handed. */
private const val MAX_QUERY_ARGS = 900

/** The longest a pass goes on trusting [changesSeen] before reading every photo's attributes anyway. */
private const val RECHECK_MS = 5 * 60 * 1000L

/**
 * What the search's Selfies, Panorama and Device options need out of each photo - see [TraitReader] -
 * read in the background and kept in Room ([MediaTraits]), the way ratings are.
 *
 * Opening every photo is the expensive part, so it is done after a scan rather than in it, one pass
 * at a time on a thread of its own, and a pass only opens what is new or has changed since it was
 * read. A library's first pass hands over what it has found as it goes, so the options fill in.
 *
 * Only a photo's last-modified and size tell that it changed, and reading those across a library
 * costs most of a pass that finds nothing to do - and scans come with every return to a grid. So a
 * pass reads them when MediaStore has seen a file added or changed, or the app has edited one in
 * place, since the last pass that did; otherwise it looks for new photos alone. A change made where
 * MediaStore did not see it is caught all the same, by the first pass of the process or one at most
 * [RECHECK_MS] after the last.
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

    // by lowercased path: the table, read in whole the first time it is asked for and kept in step after
    @Volatile
    private var known: MutableMap<String, PhotoTraits>? = null

    // the changes seen as the last pass read every photo's attributes, and when. The worker's alone
    private var checkedChanges: String? = null
    private var checkedAt = 0L

    private val edits = AtomicInteger()

    /** Moves on with every change to what has been read, so anything worked out from it can tell it is out of date. */
    val version get() = edits.get()

    /** What has been read, including whatever is read after this. Blocking the first time, which reads the table in. */
    fun lookup(context: Context): TraitLookup {
        val traits = try {
            known ?: load(context)
        } catch (ignored: Exception) {
            return TraitLookup.NONE
        }

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
        val traits = known ?: load(context)
        val changes = context.changesSeen()
        val now = SystemClock.elapsedRealtime()
        val isRecheck = changes == null || changes != checkedChanges || now - checkedAt > RECHECK_MS
        val rows = if (isRecheck) dao.getTraits().associateBy { it.fullPath } else null
        val library = HashSet<String>()
        val fresh = ArrayList<MediaTraits>()
        for (path in dao.getPhotoPaths()) {
            val key = path.lowercase(Locale.getDefault())
            library.add(key)
            // a photo read already is left be, unless something may have changed since all were looked at
            if (rows != null || key !in traits) {
                readIfChanged(path, key, rows?.get(key))?.let(fresh::add)
            }

            if (fresh.size == BATCH) {
                persist(context, traits, fresh)
                fresh.clear()
            }
        }

        persist(context, traits, fresh)

        // gone from the library, or never going to be asked about again
        val gone = traits.keys.filterNot { it in library }
        if (gone.isNotEmpty()) {
            gone.chunked(MAX_QUERY_ARGS).forEach { dao.deleteTraits(it) }
            gone.forEach { traits.remove(it) }
            edits.incrementAndGet()
        }

        if (isRecheck) {
            checkedChanges = changes
            checkedAt = now
        }
    }

    /** What [path] says of itself now, or null where [cached] still describes it - or it is empty. */
    private fun readIfChanged(path: String, key: String, cached: MediaTraits?): MediaTraits? {
        val file = File(path)
        val lastModified = file.lastModified()
        val size = file.length()
        val isUpToDate = cached != null && cached.lastModified == lastModified && cached.size == size
        if (size == 0L || isUpToDate) {
            return null
        }

        val read = Perf.section("traits.read") { TraitReader.read(path) }
        val parent = path.getParentPath().lowercase(Locale.getDefault())
        return MediaTraits(key, parent, lastModified, size, read.device, read.isSelfie, read.isPanorama)
    }

    private fun persist(context: Context, traits: MutableMap<String, PhotoTraits>, fresh: List<MediaTraits>) {
        if (fresh.isEmpty()) {
            return
        }

        context.searchDB.insertTraits(fresh)
        fresh.forEach { traits[it.fullPath] = it.toTraits() }
        edits.incrementAndGet()
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
            traits.remove(oldKey)?.let {
                traits[newKey] = it
                edits.incrementAndGet()
            }
        }
    }

    @Synchronized
    private fun load(context: Context): MutableMap<String, PhotoTraits> = known
        ?: context.searchDB.getTraits().associateTo(ConcurrentHashMap()) { it.fullPath to it.toTraits() }
            .also { known = it }
}

/**
 * The changes to files that are known of without reading them, as one value that moves on with each:
 * every file MediaStore has seen added or changed, and every edit the app has made in place. Null
 * below Android 11, where MediaStore keeps no count of them.
 */
private fun Context.changesSeen(): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return null
    }

    return try {
        val store = MediaStore.getExternalVolumeNames(this).joinToString { volume ->
            "$volume ${MediaStore.getVersion(this, volume)} ${MediaStore.getGeneration(this, volume)}"
        }

        "$store ${TransformedMedia.generation}"
    } catch (ignored: Exception) {
        null
    }
}
