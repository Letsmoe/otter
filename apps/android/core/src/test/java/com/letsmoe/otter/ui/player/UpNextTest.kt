package com.letsmoe.otter.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpNextTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var played = 0
    private val upNext = UpNext(scope) { played++ }
    private val next = PlayerEpisode("e2", 1, 2, "The Long Way Home", null, null, null)

    @After
    fun stopCountdowns() {
        scope.cancel()
    }

    @Test
    fun theCardOffersTheNextEpisodeWhileTheCreditsPlay() {
        upNext.follow(inCredits = true, playing = true, next = next)

        val card = upNext.card.value!!
        assertEquals("S1:E2 · The Long Way Home", card.episodeLabel)
        assertNull(card.pausedRemainingMillis)
        assertTrue(remainingMillisOf(card) > UP_NEXT_MILLISECONDS - 1_000)
    }

    @Test
    fun pausingHoldsTheCountdownAndPlayingResumesIt() {
        upNext.follow(inCredits = true, playing = true, next = next)
        upNext.follow(inCredits = true, playing = false, next = next)

        val paused = upNext.card.value!!
        val held = paused.pausedRemainingMillis
        assertNotNull(held)
        assertEquals(held, remainingMillisOf(paused))

        upNext.follow(inCredits = true, playing = true, next = next)

        val resumed = upNext.card.value!!
        assertNull(resumed.pausedRemainingMillis)
        assertTrue(remainingMillisOf(resumed) <= held!!)
        assertTrue(remainingMillisOf(resumed) > held - 1_000)
        assertEquals(0, played)
    }

    @Test
    fun aPausedPlayerGetsNoCard() {
        upNext.follow(inCredits = true, playing = false, next = next)

        assertNull(upNext.card.value)
    }

    @Test
    fun leavingTheCreditsWithdrawsThePausedCard() {
        upNext.follow(inCredits = true, playing = true, next = next)
        upNext.follow(inCredits = true, playing = false, next = next)
        upNext.follow(inCredits = false, playing = false, next = next)

        assertNull(upNext.card.value)
    }
}
