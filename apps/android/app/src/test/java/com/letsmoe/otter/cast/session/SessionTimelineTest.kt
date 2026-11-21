package com.letsmoe.otter.cast.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTimelineTest {

    private var elapsedMicroseconds = 5_000_000L
    private val clock = SessionClock { elapsedMicroseconds }

    @Test
    fun theClockStandsStillUntilItRuns() {
        elapsedMicroseconds += 3_000_000

        assertEquals(0L, clock.nowMicroseconds())
        assertFalse(clock.isRunning)
    }

    @Test
    fun theClockCountsOnlyWhileItRuns() {
        clock.resume()
        elapsedMicroseconds += 2_000_000
        clock.pause()
        elapsedMicroseconds += 10_000_000

        assertEquals(2_000_000L, clock.nowMicroseconds())

        clock.resume()
        elapsedMicroseconds += 500_000

        assertEquals(2_500_000L, clock.nowMicroseconds())
    }

    @Test
    fun resumingARunningClockChangesNothing() {
        clock.resume()
        elapsedMicroseconds += 1_000_000
        clock.resume()
        elapsedMicroseconds += 1_000_000

        assertEquals(2_000_000L, clock.nowMicroseconds())
    }

    @Test
    fun samplesWithinTheLeadAreWrittenAtOnce() {
        assertEquals(0L, SessionTimeline.pacingWaitMicroseconds(sampleMicroseconds = 12_000_000, clockMicroseconds = 10_000_000, leadMicroseconds = 2_500_000))
        assertEquals(0L, SessionTimeline.pacingWaitMicroseconds(sampleMicroseconds = 12_500_000, clockMicroseconds = 10_000_000, leadMicroseconds = 2_500_000))
    }

    @Test
    fun samplesBeyondTheLeadWaitForTheClock() {
        val wait = SessionTimeline.pacingWaitMicroseconds(sampleMicroseconds = 14_000_000, clockMicroseconds = 10_000_000, leadMicroseconds = 2_500_000)

        assertEquals(1_500_000L, wait)
    }

    @Test
    fun theTelevisionShowsTheLeadBehindTheClock() {
        assertEquals(7_500_000L, SessionTimeline.shownMicroseconds(clockMicroseconds = 10_000_000, leadMicroseconds = 2_500_000))
        assertEquals(0L, SessionTimeline.shownMicroseconds(clockMicroseconds = 1_000_000, leadMicroseconds = 2_500_000))
    }

    @Test
    fun aTitleShowsItsStartUntilTheTelevisionReachesItsJob() {
        assertEquals(600.0, SessionTimeline.titleSecondsShown(600.0, jobStartMicroseconds = null, shownMicroseconds = 50_000_000), 0.0)
        assertEquals(600.0, SessionTimeline.titleSecondsShown(600.0, jobStartMicroseconds = 40_000_000, shownMicroseconds = 39_000_000), 0.0)
    }

    @Test
    fun aTitleRunsOnFromWhereItsJobStarts() {
        val seconds = SessionTimeline.titleSecondsShown(600.0, jobStartMicroseconds = 40_000_000, shownMicroseconds = 52_500_000)

        assertEquals(612.5, seconds, 0.0001)
    }

    @Test
    fun aJobIsReachedOnceTheTelevisionShowsItsStart() {
        assertFalse(SessionTimeline.hasReached(jobStartMicroseconds = null, shownMicroseconds = 1_000_000))
        assertFalse(SessionTimeline.hasReached(jobStartMicroseconds = 2_000_000, shownMicroseconds = 1_000_000))
        assertTrue(SessionTimeline.hasReached(jobStartMicroseconds = 2_000_000, shownMicroseconds = 2_000_000))
    }
}
