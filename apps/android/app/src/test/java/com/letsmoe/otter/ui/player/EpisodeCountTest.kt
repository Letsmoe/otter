package com.letsmoe.otter.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeCountTest {

    private fun episode(season: Int, number: Int): PlayerEpisode {
        return PlayerEpisode("episode:$season$number", season, number, null, null, null, null)
    }

    private val episodes = listOf(episode(1, 1), episode(1, 2), episode(2, 1), episode(2, 2), episode(2, 3))

    @Test
    fun theLabelCountsWithinTheSeason() {
        assertEquals("Episode 2 of 2", episodeCountLabel(episodes, 1, 2))
        assertEquals("Episode 1 of 3", episodeCountLabel(episodes, 2, 1))
        assertEquals("Episode 3 of 3", episodeCountLabel(episodes, 2, 3))
    }

    @Test
    fun theListOrderDoesNotMatter() {
        assertEquals("Episode 1 of 3", episodeCountLabel(episodes.reversed(), 2, 1))
    }

    @Test
    fun aSeasonNumberedFromAnotherEpisodeStillCountsFromOne() {
        val late = listOf(episode(1, 4), episode(1, 5), episode(1, 6))

        assertEquals("Episode 2 of 3", episodeCountLabel(late, 1, 5))
    }

    @Test
    fun noLabelForAnEpisodeAloneInItsSeasonOrMissingFromTheList() {
        assertNull(episodeCountLabel(listOf(episode(1, 1)), 1, 1))
        assertNull(episodeCountLabel(episodes, 3, 1))
        assertNull(episodeCountLabel(episodes, 1, 9))
        assertNull(episodeCountLabel(emptyList(), 1, 1))
    }
}
