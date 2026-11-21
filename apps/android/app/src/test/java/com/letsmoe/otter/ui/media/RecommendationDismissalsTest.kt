package com.letsmoe.otter.ui.media

import com.letsmoe.otter.graphql.type.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationDismissalsTest {

    private fun movie(id: String) = RailItem(id, MediaType.MOVIE, "Title $id", null, null)

    @Test
    fun hiddenTitlesAreTrackedPerProfile() {
        val hidden = emptyMap<String, Set<String>>()
            .withTitle("profile:1", "movie:603")
            .withTitle("profile:2", "movie:155")
        assertEquals(setOf("movie:603"), hidden.getValue("profile:1"))
        assertEquals(setOf("movie:155"), hidden.getValue("profile:2"))
    }

    @Test
    fun restoringBringsATitleBack() {
        val hidden = emptyMap<String, Set<String>>()
            .withTitle("profile:1", "movie:603")
            .withoutTitle("profile:1", "movie:603")
        assertTrue(hidden.getValue("profile:1").isEmpty())
    }

    @Test
    fun withoutHiddenKeepsTheOrderOfTheRest() {
        val items = listOf(movie("movie:1"), movie("movie:2"), movie("movie:3"))
        val visible = items.withoutHidden(setOf("movie:2"))
        assertEquals(listOf("movie:1", "movie:3"), visible.map { item -> item.id })
    }
}
