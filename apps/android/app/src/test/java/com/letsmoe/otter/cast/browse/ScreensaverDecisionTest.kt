package com.letsmoe.otter.cast.browse

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreensaverDecisionTest {

    private val idle = ScreensaverInputs(browsing = false, televisionId = "tv-1", playing = false, enabled = true, encoderUsable = true)

    @Test
    fun anIdleTelevisionGetsTheScreensaverAfterTheWait() {
        assertEquals(ScreensaverAction.StartWhenIdle, nextScreensaverAction(idle, running = false))
    }

    @Test
    fun aRunningScreensaverIsLeftAlone() {
        assertEquals(ScreensaverAction.Keep, nextScreensaverAction(idle, running = true))
    }

    @Test
    fun anOpenTitlePageIsNotIdle() {
        val browsing = idle.copy(browsing = true)

        assertEquals(ScreensaverAction.Keep, nextScreensaverAction(browsing, running = false))
        assertEquals(ScreensaverAction.Forget, nextScreensaverAction(browsing, running = true))
    }

    @Test
    fun aPlayingTitleOrAGoneTelevisionEndsIt() {
        assertEquals(ScreensaverAction.Forget, nextScreensaverAction(idle.copy(playing = true), running = true))
        assertEquals(ScreensaverAction.Forget, nextScreensaverAction(idle.copy(televisionId = null), running = true))
        assertEquals(ScreensaverAction.Forget, nextScreensaverAction(idle.copy(playing = true), running = false))
    }

    @Test
    fun switchingTheSettingOffTakesItOffTheTelevision() {
        val off = idle.copy(enabled = false)

        assertEquals(ScreensaverAction.Clear, nextScreensaverAction(off, running = true))
        assertEquals(ScreensaverAction.Keep, nextScreensaverAction(off, running = false))
    }

    @Test
    fun aFailedEncoderStartsNothing() {
        assertEquals(ScreensaverAction.Keep, nextScreensaverAction(idle.copy(encoderUsable = false), running = false))
    }
}
