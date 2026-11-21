package com.letsmoe.otter.ui.home

import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.ui.media.RailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HiddenTitlesRowsTest {

    private val labels = PageLabels("Movies", "New & Coming Soon", "Continue Watching")

    private fun movie(number: Int) = RailItem(
        id = "movie:$number",
        mediaType = MediaType.MOVIE,
        title = "Movie $number",
        year = 2000 + number,
        posterUrl = null,
    )

    private fun movies(range: IntRange) = range.map { number -> movie(number) }

    private fun List<HomeRow>.itemIdsOf(key: String): List<String> {
        val row = firstOrNull { candidate -> candidate.key == key } as PosterRow?
        assertNotNull("row $key is missing", row)
        return row!!.items.map { item -> item.id }
    }

    @Test
    fun hiddenTitleLeavesRecommendationRow() {
        val state = TitlesPageState(recommended = movies(1..8))
        val rows = state.toRows(labels, hiddenIds = setOf("movie:3"))
        assertEquals(
            listOf("movie:1", "movie:2", "movie:4", "movie:5", "movie:6", "movie:7", "movie:8"),
            rows.itemIdsOf("recommended"),
        )
    }

    @Test
    fun rowLeftWithTooFewTitlesIsDropped() {
        val state = TitlesPageState(recommended = movies(1..6))
        val rows = state.toRows(labels, hiddenIds = setOf("movie:1", "movie:2"))
        assertNull(rows.firstOrNull { row -> row.key == "recommended" })
    }

    @Test
    fun personalRowsKeepHiddenTitles() {
        val state = TitlesPageState(myList = movies(1..2), watchAgain = movies(3..4))
        val rows = state.toRows(labels, hiddenIds = setOf("movie:1", "movie:3"))
        assertEquals(listOf("movie:1", "movie:2"), rows.itemIdsOf("my-list"))
        assertEquals(listOf("movie:3", "movie:4"), rows.itemIdsOf("watch-again"))
    }

    @Test
    fun withoutHiddenIdsEveryTitleStays() {
        val state = TitlesPageState(recommended = movies(1..6))
        assertEquals(6, state.toRows(labels).itemIdsOf("recommended").size)
    }
}
