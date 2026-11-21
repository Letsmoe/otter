package com.letsmoe.otter.ui.title

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndTimeTest {

    private fun episode(runtime: Int?, progress: Float?) = SeasonEpisode("e1", 1, 1, null, null, runtime, null, progress, null)

    @Test
    fun anUnwatchedTitleRunsItsWholeRuntime() {
        assertEquals(120, remainingMinutes(120, null))
    }

    @Test
    fun aPartlyWatchedTitleCountsFromItsResumePoint() {
        assertEquals(30, remainingMinutes(120, 0.75f))
    }

    @Test
    fun anUnknownRuntimeHasNoEndTime() {
        assertNull(remainingMinutes(null, null))
        assertNull(remainingMinutes(0, null))
    }

    @Test
    fun aTitleWatchedToTheEndHasNothingLeft() {
        assertNull(remainingMinutes(90, 1f))
    }

    @Test
    fun aResumedEpisodeCountsFromItsResumePoint() {
        val upNext = UpNext(episode(runtime = 50, progress = 0.4f), resume = true)

        assertEquals(30, upNextRemainingMinutes(upNext))
    }

    @Test
    fun aFinishedEpisodeThatMovedOnStartsFromTheBeginning() {
        val upNext = UpNext(episode(runtime = 50, progress = 0.95f), resume = false)

        assertEquals(50, upNextRemainingMinutes(upNext))
    }

    @Test
    fun noUpNextEpisodeHasNoEndTime() {
        assertNull(upNextRemainingMinutes(null))
    }

    @Test
    fun theEndTimeIsTheStartPlusTheRemainingMinutes() {
        assertEquals(1_000L + 90 * 60_000L, endsAtMillis(1_000L, 90))
    }

    @Test
    fun theClockTicksAtTheNextFullMinute() {
        assertEquals(60_000L, millisUntilNextMinute(120_000L))
        assertEquals(1_000L, millisUntilNextMinute(179_000L))
    }
}
