package com.letsmoe.otter.ui.shelves

import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.ui.home.PageLabels
import com.letsmoe.otter.ui.home.PosterRow
import com.letsmoe.otter.ui.home.TitlesPageState
import com.letsmoe.otter.ui.home.toRows
import com.letsmoe.otter.ui.media.RailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShelfTest {

    private val labels = PageLabels("Movies", "New & Coming Soon", "Continue Watching")

    private fun item(id: String, mediaType: MediaType = MediaType.MOVIE) =
        RailItem(id, mediaType, "Title $id", null, null)

    private fun shelf(id: String, name: String, vararg items: RailItem) = Shelf(id, name, items.toList())

    @Test
    fun movedBySwapsWithTheNeighbour() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").movedBy(0, 1))
        assertEquals(listOf("a", "c", "b"), listOf("a", "b", "c").movedBy(2, -1))
    }

    @Test
    fun movedByStaysPutAtTheEnds() {
        val list = listOf("a", "b", "c")
        assertEquals(list, list.movedBy(0, -1))
        assertEquals(list, list.movedBy(2, 1))
        assertEquals(list, list.movedBy(5, 1))
    }

    @Test
    fun shelfNamesAreTrimmedAndLimited() {
        assertEquals("Comfort shows", cleanShelfName("  Comfort shows "))
        assertNull(cleanShelfName("   "))
        assertNull(cleanShelfName("x".repeat(41)))
    }

    @Test
    fun shelfKnowsItsTitles() {
        val comfort = shelf("shelf:1", "Comfort shows", item("series:1"), item("movie:2"))
        assertEquals(true, comfort.holds("movie:2"))
        assertEquals(false, comfort.holds("movie:3"))
    }

    @Test
    fun shelvesComeAfterTheMyListRowAndKeepTheirOrder() {
        val state = TitlesPageState(myList = listOf(item("movie:9")))
        val shelves = listOf(
            shelf("shelf:1", "Comfort shows", item("movie:1")),
            shelf("shelf:2", "Watch with Anna", item("movie:2"), item("movie:3")),
        )
        val keys = state.toRows(labels, shelves, MediaType.MOVIE).map { row -> row.key }
        assertEquals(listOf("my-list", "shelf-shelf:1", "shelf-shelf:2"), keys)
    }

    @Test
    fun shelfRowKeepsAllItsTitlesInOrderAndEvenAFewOnes() {
        val state = TitlesPageState(recommended = listOf(item("movie:1"), item("movie:2")))
        val shelves = listOf(shelf("shelf:1", "Comfort shows", item("movie:2"), item("movie:1")))
        val row = state.toRows(labels, shelves, MediaType.MOVIE).first { candidate -> candidate.key == "shelf-shelf:1" } as PosterRow
        assertEquals(listOf("movie:2", "movie:1"), row.items.map { it.id })
    }

    @Test
    fun aShelfShowsOnlyTheTitlesOfThePagesKind() {
        val mixed = shelf("shelf:1", "Mixed", item("movie:1"), item("series:2", MediaType.SERIES))
        val state = TitlesPageState()
        val movieRow = state.toRows(labels, listOf(mixed), MediaType.MOVIE).single() as PosterRow
        val seriesRow = state.toRows(labels, listOf(mixed), MediaType.SERIES).single() as PosterRow
        assertEquals(listOf("movie:1"), movieRow.items.map { it.id })
        assertEquals(listOf("series:2"), seriesRow.items.map { it.id })
    }

    @Test
    fun aShelfWithoutTitlesOfTheKindIsLeftOut() {
        val seriesOnly = shelf("shelf:1", "Shows", item("series:2", MediaType.SERIES))
        assertEquals(emptyList<String>(), TitlesPageState().toRows(labels, listOf(seriesOnly), MediaType.MOVIE).map { it.key })
    }

    @Test
    fun titleCountIsSingularForOne() {
        assertEquals("1 title", titleCount(1))
        assertEquals("0 titles", titleCount(0))
        assertEquals("5 titles", titleCount(5))
    }
}
