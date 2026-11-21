package com.letsmoe.otter.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinishWatchTest {

    private val finishWatch = FinishWatch()

    @Test
    fun timedCreditsFinishTheTitleTheMomentTheyStart() {
        assertFalse(finishWatch.reached(inCredits = false, positionSeconds = 1000.0, durationSeconds = 2600.0))
        assertTrue(finishWatch.reached(inCredits = true, positionSeconds = 2400.0, durationSeconds = 2600.0))
    }

    @Test
    fun withoutTimedCreditsTheLastThreePercentFinishesIt() {
        assertFalse(finishWatch.reached(inCredits = false, positionSeconds = 2500.0, durationSeconds = 2600.0))
        assertTrue(finishWatch.reached(inCredits = false, positionSeconds = 2530.0, durationSeconds = 2600.0))
    }

    @Test
    fun aTitleIsFinishedOnlyOncePerPlay() {
        assertTrue(finishWatch.reached(inCredits = true, positionSeconds = 2400.0, durationSeconds = 2600.0))
        assertFalse(finishWatch.reached(inCredits = true, positionSeconds = 2410.0, durationSeconds = 2600.0))

        finishWatch.reset()
        assertTrue(finishWatch.reached(inCredits = true, positionSeconds = 2400.0, durationSeconds = 2600.0))
    }

    @Test
    fun nothingIsFinishedWhileTheLengthIsUnknown() {
        assertFalse(finishWatch.reached(inCredits = true, positionSeconds = 10.0, durationSeconds = 0.0))
    }
}
