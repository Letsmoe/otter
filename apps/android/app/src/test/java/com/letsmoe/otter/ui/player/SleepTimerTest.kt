package com.letsmoe.otter.ui.player

import com.letsmoe.otter.ui.player.audio.scalePcm16
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {

    private var nowMillis = 1_000_000L
    private val timer = SleepTimer { nowMillis }

    @Test
    fun theSoundIsFullUntilTheLastTwentySecondsThenFadesToSilence() {
        assertEquals(1f, sleepGainAt(60_000), 0.0001f)
        assertEquals(1f, sleepGainAt(20_000), 0.0001f)
        assertEquals(0.25f, sleepGainAt(10_000), 0.0001f)
        assertEquals(0f, sleepGainAt(0), 0.0001f)
        assertEquals(0f, sleepGainAt(-5_000), 0.0001f)
    }

    @Test
    fun theFadeNeverGetsLouder() {
        var previous = 1f
        for (remaining in 20_000L downTo 0L step 500L) {
            val gain = sleepGainAt(remaining)
            assertTrue(gain <= previous)
            previous = gain
        }
    }

    @Test
    fun theTimerEndsAfterItsMinutes() {
        timer.start(15)

        assertFalse(timer.isDue())
        nowMillis += 15 * 60_000L - 1
        assertFalse(timer.isDue())
        nowMillis += 1
        assertTrue(timer.isDue())
    }

    @Test
    fun theGainFollowsTheTimeLeft() {
        timer.start(15)
        assertEquals(1f, timer.gain(), 0.0001f)

        nowMillis += 15 * 60_000L - 10_000
        assertEquals(0.25f, timer.gain(), 0.0001f)
    }

    @Test
    fun aTvFadesEarlierByTheTimeItsSoundIsLate() {
        timer.start(1)
        nowMillis += 60_000L - 10_000

        assertEquals(0.25f, timer.gain(), 0.0001f)
        assertEquals(sleepGainAt(5_000), timer.gain(leadMillis = 5_000), 0.0001f)
    }

    @Test
    fun withoutATimerNothingIsFadedOrDue() {
        assertEquals(1f, timer.gain(), 0.0001f)
        assertFalse(timer.isDue())

        timer.start(30)
        timer.cancel()

        assertNull(timer.run.value)
        assertFalse(timer.isDue())
        assertEquals(1f, timer.gain(), 0.0001f)
    }

    @Test
    fun samplesAreScaledAndKeptInRange() {
        assertEquals(1000.toShort(), scalePcm16(2000, 0.5f))
        assertEquals(0.toShort(), scalePcm16(Short.MAX_VALUE, 0f))
        assertEquals((-1000).toShort(), scalePcm16(-2000, 0.5f))
        assertEquals(Short.MAX_VALUE, scalePcm16(Short.MAX_VALUE, 1f))
    }

    @Test
    fun theSheetShowsWhatIsSet() {
        val run = SleepTimerRun(30, 5L)

        assertEquals(SleepChoice.MINUTES, sleepChoiceOf(run, stopsAfterEpisode = false))
        assertEquals(SleepChoice.END_OF_EPISODE, sleepChoiceOf(null, stopsAfterEpisode = true))
        assertEquals(SleepChoice.OFF, sleepChoiceOf(null, stopsAfterEpisode = false))
        assertEquals("1 hour", sleepMinutesLabel(60))
        assertEquals("15 minutes", sleepMinutesLabel(15))
    }
}
