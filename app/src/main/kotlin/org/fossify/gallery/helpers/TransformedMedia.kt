package org.fossify.gallery.helpers

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide record of media files this app transformed in place.
 *
 * Nothing else in the app can tell that such a file changed. A lossless mirror rewrites only the
 * Exif orientation, so the file keeps its size, and with "Keep old last-modified value at file
 * operations" enabled it keeps its modified time too. Every cache key and refresh gate in the app
 * is built from exactly those two values - Medium.getSignature() ("path-modified-size", the Glide
 * key for grid thumbnails and the fullscreen image, and the Picasso stable key for the zoomable
 * view), Directory.getKey() ("path-modified", the Glide key for album covers) and the adapters'
 * hashcode-based diffing - so all of them stay identical across the edit, and every screen except
 * the one that performed it goes on serving its cached pre-transform bitmap.
 *
 * A per-path version gives those cache keys something that does change, and [generation] lets a
 * screen that was not on top during the edit notice, on its next refresh, that it may be holding
 * stale bitmaps and rebind.
 *
 * Deliberately in-memory only: the caches it exists to invalidate do not outlive the process
 * either. Glide's memory cache starts empty, and its disk cache is cleared on every transform (see
 * fileTransformedSuccessfully), so after a restart the base cache keys decode the file afresh.
 */
object TransformedMedia {
    private val versions = ConcurrentHashMap<String, Int>()

    /**
     * Incremented on every transform. Screens compare it against the value they last rendered to
     * decide whether to rebind, since their own data may be unchanged.
     */
    @Volatile
    var generation: Int = 0
        private set

    @Synchronized
    fun onTransformed(path: String) {
        versions[path] = (versions[path] ?: 0) + 1
        generation++
    }

    /**
     * Empty for paths this process has not transformed, so their cache keys keep the exact form
     * they had before - only files that actually changed miss their cached bitmaps.
     */
    fun cacheKeySuffixFor(path: String): String {
        val version = versions[path] ?: return ""
        return "-$version"
    }
}
