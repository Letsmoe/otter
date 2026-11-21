package com.letsmoe.otter.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TogetherPayloadsTest {

    @Test
    fun aSuggestionSurvivesTheWire() {
        val sent = SuggestPayload("movie:603", "The Matrix", 1999, "/abc.jpg")

        assertEquals(sent, parseTogetherPayload(sent.toJson()))
    }

    @Test
    fun aSuggestionWithoutYearOrPosterSurvivesTheWire() {
        val sent = SuggestPayload("movie:603", "The Matrix", null, null)

        assertEquals(sent, parseTogetherPayload(sent.toJson()))
    }

    @Test
    fun aVoteAndAResultSurviveTheWire() {
        assertEquals(VotePayload("movie:603"), parseTogetherPayload(VotePayload("movie:603").toJson()))
        val result = VoteResultPayload("movie:603", "The Matrix")
        assertEquals(result, parseTogetherPayload(result.toJson()))
    }

    @Test
    fun theWireTypesAreNamedLikeTheWebClientsExpectToIgnoreThem() {
        assertEquals("vote.suggest", org.json.JSONObject(SuggestPayload("movie:1", "A", null, null).toJson()).getString("type"))
        assertEquals("vote.cast", org.json.JSONObject(VotePayload("movie:1").toJson()).getString("type"))
        assertEquals("vote.result", org.json.JSONObject(VoteResultPayload("movie:1", "A").toJson()).getString("type"))
    }

    @Test
    fun payloadsFromNewerAppsAreIgnored() {
        assertNull(parseTogetherPayload("{\"type\":\"vote.veto\",\"mediaId\":\"movie:1\"}"))
        assertNull(parseTogetherPayload("{\"type\":\"something.else\"}"))
    }

    @Test
    fun malformedVotePayloadsAreIgnored() {
        assertNull(parseTogetherPayload("{\"type\":\"vote.cast\"}"))
        assertNull(parseTogetherPayload("{\"type\":\"vote.suggest\",\"mediaId\":\"movie:1\"}"))
        assertNull(parseTogetherPayload("not json"))
    }

    @Test
    fun existingPayloadsStillParse() {
        assertEquals(ChatPayload("hi"), parseTogetherPayload("{\"type\":\"chat\",\"text\":\"hi\"}"))
    }
}
