package com.letsmoe.otter.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionExtrasTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val extras = CaptionExtras(scope) { }

    @After
    fun stopReloads() {
        scope.cancel()
    }

    @Test
    fun nudgesAddUpToWholeSteps() {
        extras.nudge(CAPTION_DELAY_STEP_SECONDS)
        extras.nudge(CAPTION_DELAY_STEP_SECONDS)
        extras.nudge(-CAPTION_DELAY_STEP_SECONDS)

        assertEquals(0.5, extras.state.value.delaySeconds, 0.0)
        assertEquals(500L, extras.delayMilliseconds)

        extras.nudge(-CAPTION_DELAY_STEP_SECONDS)
        assertEquals(0.0, extras.state.value.delaySeconds, 0.0)
    }

    @Test
    fun theDelayStopsAtHalfAMinute() {
        repeat(100) { extras.nudge(CAPTION_DELAY_STEP_SECONDS) }

        assertEquals(30.0, extras.state.value.delaySeconds, 0.0)
    }

    @Test
    fun aNewTitleStartsWithoutTweaks() {
        extras.nudge(CAPTION_DELAY_STEP_SECONDS)
        extras.reset()

        assertEquals(CaptionExtrasState(), extras.state.value)
    }

    @Test
    fun theLabelReadsLikeATimeShift() {
        assertEquals("0 s", captionDelayLabel(0.0))
        assertEquals("+1.5 s", captionDelayLabel(1.5))
        assertEquals("−2 s", captionDelayLabel(-2.0))
    }
}
