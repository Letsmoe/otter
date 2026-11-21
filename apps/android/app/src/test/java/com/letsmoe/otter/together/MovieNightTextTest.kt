package com.letsmoe.otter.together

import com.letsmoe.otter.graphql.type.TogetherStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovieNightTextTest {

    private val anna = TogetherPeer("anna", "Anna", null)
    private val ben = TogetherPeer("ben", "Ben", null)
    private val hostRoom = TogetherRoom("K7QD9MZA", hosting = true, status = TogetherStatus.CONNECTED, self = anna, peers = listOf(anna, ben))
    private val guestRoom = hostRoom.copy(hosting = false, self = ben)

    private val matrix = Suggestion("movie:603", "The Matrix", 1999, null, "anna")
    private val heat = Suggestion("movie:949", "Heat", 1995, null, "ben")

    @Test
    fun theSummaryCountsTitlesAndVoters() {
        val ballot = Ballot().withSuggestion(matrix).withSuggestion(heat).withVote("anna", matrix.mediaId)

        assertEquals("2 titles · 1 of 2 voted", voteSummary(ballot, hostRoom))
        assertEquals("1 title · 0 of 2 voted", voteSummary(Ballot().withSuggestion(matrix), hostRoom))
        assertEquals("Suggest titles and vote on one together", voteSummary(Ballot(), hostRoom))
    }

    @Test
    fun theFooterTellsWhoCanCloseTheVote() {
        val ballot = Ballot().withSuggestion(matrix)

        assertEquals("Closes when everyone voted, or when you close it.", voteStatus(ballot, hostRoom))
        assertEquals("Closes when everyone voted, or when the host closes it.", voteStatus(ballot, guestRoom))
    }

    @Test
    fun suggestionsNameWhoMadeThem() {
        assertEquals("suggested by you", suggestedByText(matrix, hostRoom))
        assertEquals("suggested by Ben", suggestedByText(heat, hostRoom))
        assertEquals("suggested by someone", suggestedByText(heat.copy(suggestedBy = "gone"), hostRoom))
    }

    @Test
    fun suggestionsLeftCountsDown() {
        val ballot = Ballot().withSuggestion(matrix).withSuggestion(heat)

        assertEquals(2, suggestionsLeft(ballot, hostRoom))
        assertEquals(2, suggestionsLeft(ballot, guestRoom))
        assertEquals(3, suggestionsLeft(Ballot(), hostRoom))
    }

    @Test
    fun theTmdbPosterPathIsTheLastSegmentOfThePosterUrl() {
        assertEquals("/abc.jpg", posterPathOf("https://image.tmdb.org/t/p/w342/abc.jpg"))
        assertNull(posterPathOf(null))
        assertNull(posterPathOf("https://image.tmdb.org/t/p/w342/"))
    }
}
