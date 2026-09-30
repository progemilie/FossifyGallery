package org.fossify.gallery.helpers

import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_DESCENDING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sorting by rating giving up the headers it brought, with nobody's grid changing for it. */
class RatingHeadersTest {

    private val byRating = SORT_BY_RATING or SORT_DESCENDING
    private val monthly = GROUP_BY_DATE_TAKEN_MONTHLY or GROUP_DESCENDING or GROUP_SHOW_FILE_COUNT

    @Test
    fun `nothing sorted by rating leaves every grouping alone`() {
        val kept = RatingHeaders.keep(
            defaultSorting = SORT_BY_DATE_TAKEN,
            defaultGrouping = monthly,
            folderSortings = mapOf("/dcim" to SORT_BY_NAME),
            folderGroupings = mapOf("/pictures" to GROUP_BY_NONE)
        )

        assertNull(kept.defaultGrouping)
        assertTrue(kept.folderGroupings.isEmpty())
    }

    @Test
    fun `a default sorted by rating is grouped by rating the way it was drawn`() {
        val kept = RatingHeaders.keep(byRating, monthly, emptyMap(), emptyMap())
        // the direction is the sorting's, the file count the grouping's
        assertEquals(GROUP_BY_RATING or GROUP_DESCENDING or GROUP_SHOW_FILE_COUNT, kept.defaultGrouping)
        assertTrue(kept.folderGroupings.isEmpty())
    }

    @Test
    fun `a folder sorted its own way keeps the default grouping it had`() {
        val kept = RatingHeaders.keep(
            defaultSorting = byRating,
            defaultGrouping = monthly,
            folderSortings = mapOf("/dcim" to SORT_BY_DATE_TAKEN),
            folderGroupings = emptyMap()
        )

        assertEquals(mapOf("/dcim" to monthly), kept.folderGroupings)
    }

    @Test
    fun `a folder grouped its own way under a rating default is grouped by rating`() {
        val kept = RatingHeaders.keep(
            defaultSorting = SORT_BY_RATING,
            defaultGrouping = GROUP_BY_NONE,
            folderSortings = emptyMap(),
            folderGroupings = mapOf("/dcim" to monthly)
        )

        assertEquals(mapOf("/dcim" to (GROUP_BY_RATING or GROUP_SHOW_FILE_COUNT)), kept.folderGroupings)
    }

    @Test
    fun `a folder sorted by rating of its own is grouped by rating of its own`() {
        val kept = RatingHeaders.keep(
            defaultSorting = SORT_BY_DATE_TAKEN,
            defaultGrouping = monthly,
            folderSortings = mapOf("/dcim" to byRating, "/pictures" to SORT_BY_RATING),
            folderGroupings = mapOf("/pictures" to GROUP_BY_NONE)
        )

        assertNull(kept.defaultGrouping)
        assertEquals(
            mapOf(
                "/dcim" to (GROUP_BY_RATING or GROUP_DESCENDING or GROUP_SHOW_FILE_COUNT),
                "/pictures" to GROUP_BY_RATING
            ),
            kept.folderGroupings
        )
    }

    @Test
    fun `a folder already showing what the new default shows is left to it`() {
        val kept = RatingHeaders.keep(
            defaultSorting = byRating,
            defaultGrouping = GROUP_BY_NONE,
            folderSortings = mapOf("/dcim" to byRating),
            folderGroupings = emptyMap()
        )

        assertEquals(GROUP_BY_RATING or GROUP_DESCENDING, kept.defaultGrouping)
        assertTrue(kept.folderGroupings.isEmpty())
    }
}
