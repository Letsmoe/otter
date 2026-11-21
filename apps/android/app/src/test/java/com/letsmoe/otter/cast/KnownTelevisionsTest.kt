package com.letsmoe.otter.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KnownTelevisionsTest {

    private fun renderer(id: String, name: String) = DlnaRenderer(name, id, "http://tv/$id", "service")

    @Test
    fun aKnownTelevisionSurvivesBeingSaved() {
        val television = KnownTelevision("uuid:1", "Living Room: 4K TV", 1234L)

        assertEquals(television, decodeKnownTelevision(encodeKnownTelevision(television)))
    }

    @Test
    fun brokenEntriesAreIgnored() {
        assertNull(decodeKnownTelevision("not an entry"))
        assertNull(decodeKnownTelevision("soon\tuuid:1\tTV"))
    }

    @Test
    fun theSuggestionIsATelevisionCastToBeforeThatIsFound() {
        val found = listOf(renderer("uuid:1", "Bedroom"), renderer("uuid:2", "Living room"))
        val known = listOf(KnownTelevision("uuid:2", "Living room", 10L))

        assertEquals("uuid:2", suggestedTelevision(found, known)?.id)
    }

    @Test
    fun theTelevisionCastToLastWins() {
        val found = listOf(renderer("uuid:1", "Bedroom"), renderer("uuid:2", "Living room"))
        val known = listOf(KnownTelevision("uuid:1", "Bedroom", 50L), KnownTelevision("uuid:2", "Living room", 10L))

        assertEquals("uuid:1", suggestedTelevision(found, known)?.id)
    }

    @Test
    fun nothingIsSuggestedWhenNoKnownTelevisionAnswers() {
        val found = listOf(renderer("uuid:3", "Guest room"))
        val known = listOf(KnownTelevision("uuid:1", "Bedroom", 50L))

        assertNull(suggestedTelevision(found, known))
        assertNull(suggestedTelevision(emptyList(), known))
    }
}
