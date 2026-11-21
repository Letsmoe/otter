package com.letsmoe.otter.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieNightBallotTest {

    private fun suggestion(mediaId: String, by: String) = Suggestion(mediaId, "Title $mediaId", 2020, null, by)

    private val matrix = suggestion("movie:603", "anna")
    private val heat = suggestion("movie:949", "ben")
    private val alien = suggestion("movie:348", "anna")

    @Test
    fun aPersonMaySuggestThreeTitles() {
        var ballot = Ballot()
        for (number in 1..MAX_SUGGESTIONS_PER_PERSON + 2) {
            ballot = ballot.withSuggestion(suggestion("movie:$number", "anna"))
        }

        assertEquals(MAX_SUGGESTIONS_PER_PERSON, ballot.suggestionsOf("anna").size)
        assertFalse(ballot.canSuggest("anna", "movie:99"))
        assertTrue(ballot.canSuggest("ben", "movie:99"))
    }

    @Test
    fun aTitleIsOnTheBallotOnlyOnce() {
        val ballot = Ballot().withSuggestion(matrix).withSuggestion(suggestion("movie:603", "ben"))

        assertEquals(1, ballot.suggestions.size)
        assertEquals("anna", ballot.suggestions[0].suggestedBy)
    }

    @Test
    fun votingAgainMovesTheVote() {
        val ballot = Ballot().withSuggestion(matrix).withSuggestion(heat).withVote("anna", matrix.mediaId).withVote("anna", heat.mediaId)

        assertEquals(mapOf(matrix.mediaId to 0, heat.mediaId to 1), ballot.tally())
    }

    @Test
    fun aVoteThatArrivesBeforeItsSuggestionCountsOnceItIsThere() {
        val early = Ballot().withVote("ben", matrix.mediaId)
        assertEquals(emptyMap<String, Int>(), early.tally())

        val ballot = early.withSuggestion(matrix)

        assertEquals(1, ballot.tally().getValue(matrix.mediaId))
    }

    @Test
    fun theTitleWithTheMostVotesWins() {
        val ballot = Ballot().withSuggestion(matrix).withSuggestion(heat)
            .withVote("anna", heat.mediaId).withVote("ben", heat.mediaId).withVote("cleo", matrix.mediaId)

        assertEquals(heat, ballot.winner())
    }

    @Test
    fun aTieGoesToTheTitleSuggestedFirst() {
        val ballot = Ballot().withSuggestion(matrix).withSuggestion(heat).withVote("anna", heat.mediaId).withVote("ben", matrix.mediaId)

        assertEquals(matrix, ballot.winner())
    }

    @Test
    fun nobodyVotedMeansNoWinner() {
        assertNull(Ballot().withSuggestion(matrix).winner())
        assertNull(Ballot().winner())
    }

    @Test
    fun everyoneVotedOnlyWhenEachPersonHasAVoteThatCounts() {
        val people = listOf("anna", "ben")
        val ballot = Ballot().withSuggestion(matrix).withVote("anna", matrix.mediaId)

        assertFalse(ballot.everyoneVoted(people))
        assertTrue(ballot.withVote("ben", matrix.mediaId).everyoneVoted(people))
        assertFalse(ballot.withVote("ben", "movie:unknown").everyoneVoted(people))
    }

    @Test
    fun anEmptyBallotIsNeverComplete() {
        assertFalse(Ballot().everyoneVoted(listOf("anna")))
        assertFalse(Ballot().withSuggestion(matrix).everyoneVoted(emptyList()))
    }

    @Test
    fun peopleWhoLeftStopCounting() {
        val ballot = Ballot().withSuggestion(matrix).withVote("anna", matrix.mediaId).withVote("ben", matrix.mediaId)

        val afterBenLeft = ballot.onlyVotesOf(setOf("anna"))

        assertEquals(1, afterBenLeft.tally().getValue(matrix.mediaId))
        assertTrue(afterBenLeft.everyoneVoted(listOf("anna")))
    }

    @Test
    fun someoneElsesSuggestionsDoNotUseUpMine() {
        val ballot = Ballot().withSuggestion(matrix).withSuggestion(alien).withSuggestion(heat)

        assertEquals(1, ballot.suggestionsOf("ben").size)
        assertEquals(2, ballot.suggestionsOf("anna").size)
    }
}
