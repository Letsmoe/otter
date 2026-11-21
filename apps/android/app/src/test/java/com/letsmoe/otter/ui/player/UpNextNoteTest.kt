package com.letsmoe.otter.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpNextNoteTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var played = 0
    private val upNext = UpNext(scope) { played++ }

    private val seasonTwoFinale = PlayerEpisode("e2-9", 2, 9, "Hello, Ms. Cobel", null, null, null)
    private val seasonThreeStart = PlayerEpisode("e3-1", 3, 1, "Hakuna Matata", null, null, null)
    private val seasonTwoMiddle = PlayerEpisode("e2-4", 2, 4, "Woe's Hollow", null, null, null)
    private val seasonTwoNext = PlayerEpisode("e2-5", 2, 5, null, null, null, null)

    @After
    fun stopCountdowns() {
        scope.cancel()
    }

    @Test
    fun theFirstEpisodeOfTheNextSeasonIsNamedWithItsSeason() {
        val note = upNextNote(seasonTwoFinale, seasonThreeStart)!!

        assertEquals("Next season", note.heading)
        assertEquals("Season 3 · Episode 1 · Hakuna Matata", note.label)
        assertTrue(note.countsDown)
    }

    @Test
    fun anUnnamedEpisodeIsNamedByItsPositionAlone() {
        assertEquals("Season 3 · Episode 1", seasonStartLabel(PlayerEpisode("e3-1", 3, 1, null, null, null, null)))
    }

    @Test
    fun anEpisodeOfTheSameSeasonKeepsTheUsualLabel() {
        val note = upNextNote(seasonTwoMiddle, seasonTwoNext)!!

        assertEquals("Next episode", note.heading)
        assertEquals("S2:E5 · Episode 5", note.label)
        assertTrue(note.countsDown)
    }

    @Test
    fun withoutAnotherEpisodeTheSeasonFinaleHasNothingAfterIt() {
        val note = upNextNote(seasonTwoFinale, null)!!

        assertEquals("Season finale", note.heading)
        assertEquals("Nothing after it yet", note.label)
        assertFalse(note.countsDown)
    }

    @Test
    fun aMovieHasNoNote() {
        assertNull(upNextNote(null, null))
    }

    @Test
    fun theNextEpisodeIsFoundAcrossSeasonsAndSpecialsAreLeftOut() {
        val special = PlayerEpisode("e0-1", 0, 1, null, null, null, null)
        val episodes = listOf(seasonThreeStart, special, seasonTwoFinale, seasonTwoMiddle)

        assertEquals("e3-1", episodes.episodeAfter("e2-9")?.id)
        assertEquals("e2-9", episodes.episodeAfter("e2-4")?.id)
        assertNull(episodes.episodeAfter("e3-1"))
        assertNull(episodes.episodeAfter("e0-1"))
        assertNull(episodes.episodeAfter("missing"))
    }

    @Test
    fun theCardOfANewSeasonCountsDownAndPlaysIt() {
        upNext.follow(inCredits = true, playing = true, next = seasonThreeStart, current = seasonTwoFinale)

        val card = upNext.card.value!!
        assertEquals("Next season", card.heading)
        assertEquals("Season 3 · Episode 1 · Hakuna Matata", card.episodeLabel)
        assertTrue(card.countsDown)

        upNext.playNow()
        assertEquals(1, played)
    }

    @Test
    fun theSeasonFinaleCardOnlyTellsAndGoesAfterAWhile() {
        upNext.follow(inCredits = true, playing = true, next = null, current = seasonTwoFinale)

        val card = upNext.card.value!!
        assertEquals("Season finale", card.heading)
        assertFalse(card.countsDown)

        upNext.playNow()
        assertNotNull(upNext.card.value)
        assertEquals(0, played)

        upNext.follow(inCredits = true, playing = false, next = null, current = seasonTwoFinale)
        assertNotNull(upNext.card.value)

        upNext.follow(inCredits = false, playing = true, next = null, current = seasonTwoFinale)
        assertNull(upNext.card.value)
    }

    @Test
    fun withoutAnEpisodeToNameNothingIsOffered() {
        upNext.follow(inCredits = true, playing = true, next = null, current = null)

        assertNull(upNext.card.value)
    }
}
