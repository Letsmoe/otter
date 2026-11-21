package com.letsmoe.otter.ui.title

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewatchStateTest {

    private fun episode(id: String, number: Int, progress: Float?, nextId: String?): SeasonEpisode {
        return SeasonEpisode(
            id = id,
            seasonNumber = 1,
            episodeNumber = number,
            name = null,
            overview = null,
            runtime = null,
            stillPath = null,
            progress = progress,
            nextEpisodeId = nextId,
        )
    }

    @Test
    fun aFinishedTitleOffersARewatchAndARunningOneOffersStopping() {
        assertEquals(RewatchOffer.START, rewatchOffer(finishedBefore = true, rewatching = false))
        assertEquals(RewatchOffer.RUNNING, rewatchOffer(finishedBefore = true, rewatching = true))
        assertEquals(RewatchOffer.RUNNING, rewatchOffer(finishedBefore = false, rewatching = true))
        assertEquals(RewatchOffer.NONE, rewatchOffer(finishedBefore = false, rewatching = false))
    }

    @Test
    fun aRewatchIsRunningUntilItsFinishedFlagIsSet() {
        assertTrue(isRewatchRunning(false))
        assertFalse(isRewatchRunning(true))
        assertFalse(isRewatchRunning(null))
    }

    @Test
    fun aSeriesIsFinishedWhenItsLastEpisodeWasWatchedToTheEnd() {
        val episodes = listOf(episode("a", 1, 1f, "b"), episode("b", 2, 0.95f, null))
        assertTrue(isSeriesFinished(episodes))
    }

    @Test
    fun aSeriesWithAnEpisodeLeftIsNotFinished() {
        val episodes = listOf(episode("a", 1, 1f, "b"), episode("b", 2, null, null))
        assertFalse(isSeriesFinished(episodes))
        val halfWay = listOf(episode("a", 1, 0.4f, "b"), episode("b", 2, null, null))
        assertFalse(isSeriesFinished(halfWay))
    }

    @Test
    fun aSeriesNobodyStartedIsNotFinished() {
        assertFalse(isSeriesFinished(listOf(episode("a", 1, null, null))))
        assertFalse(isSeriesFinished(emptyList()))
    }

    @Test
    fun theSeriesPlayButtonSaysRewatch() {
        val first = episode("a", 1, null, "b")
        assertEquals("Rewatch S1:E1", rewatchSeriesPlayLabel(UpNext(first, resume = false)))
        assertEquals("Continue rewatch S1:E1", rewatchSeriesPlayLabel(UpNext(first, resume = true)))
        assertEquals("Continue rewatch", rewatchSeriesPlayLabel(null))
    }
}
