package org.fossify.gallery.extensions

import android.content.Context
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.helpers.MediaFacts
import org.fossify.gallery.helpers.TraitIndex
import org.fossify.gallery.helpers.TraitLookup
import org.fossify.gallery.interfaces.SearchDao
import org.fossify.gallery.models.Medium

val Context.searchDB: SearchDao get() = GalleryDatabase.getInstance(applicationContext).SearchDao()

/**
 * The library as Albums shows it - every file in [folders] the scans have seen - which is what a pill
 * picked there narrows Pictures by. Blocking, call it off the main thread.
 */
fun Context.libraryMedia(folders: Set<String>): List<Medium> = try {
    searchDB.getLibrary().filter { it.parentPath in folders }
} catch (ignored: Exception) {
    emptyList()
}

/** What the trait index has read so far, for the search's options and filters. Blocking the first time. */
fun Context.traitLookup(): TraitLookup = TraitIndex.lookup(this)

/** Everything the search's options and filters ask of a file beyond its [Medium]. */
fun Context.mediaFacts() = MediaFacts(applicationContext, traitLookup())
