package com.letsmoe.otter.cast

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallPauseTest {

    private val tracker = CallPauseTracker()

    @Test
    fun aCallThatStartsWhileTheTelevisionPlaysPausesIt() {
        assertEquals(CallPauseAction.PAUSE, tracker.onCheck(callNow = true, playing = true))
    }

    @Test
    fun theTelevisionPlaysOnWhenTheCallEnds() {
        tracker.onCheck(callNow = true, playing = true)

        assertEquals(CallPauseAction.NONE, tracker.onCheck(callNow = true, playing = false))
        assertEquals(CallPauseAction.RESUME, tracker.onCheck(callNow = false, playing = false))
    }

    @Test
    fun aTelevisionThatWasPausedBeforeTheCallStaysPaused() {
        assertEquals(CallPauseAction.NONE, tracker.onCheck(callNow = true, playing = false))
        assertEquals(CallPauseAction.NONE, tracker.onCheck(callNow = false, playing = false))
    }

    @Test
    fun aViewerWhoPlaysAgainDuringTheCallIsLeftAlone() {
        tracker.onCheck(callNow = true, playing = true)

        assertEquals(CallPauseAction.NONE, tracker.onCheck(callNow = false, playing = true))
    }

    @Test
    fun theNextCallPausesAgainButOnlyOnce() {
        tracker.onCheck(callNow = true, playing = true)
        tracker.onCheck(callNow = false, playing = false)

        assertEquals(CallPauseAction.NONE, tracker.onCheck(callNow = false, playing = true))
        assertEquals(CallPauseAction.PAUSE, tracker.onCheck(callNow = true, playing = true))
    }

    @Test
    fun resettingForgetsTheCall() {
        tracker.onCheck(callNow = true, playing = true)
        tracker.reset()

        assertEquals(CallPauseAction.NONE, tracker.onCheck(callNow = false, playing = false))
    }

    @Test
    fun ringingAndCallsAreCallModes() {
        assertTrue(isCallMode(AudioManager.MODE_RINGTONE))
        assertTrue(isCallMode(AudioManager.MODE_IN_CALL))
        assertTrue(isCallMode(AudioManager.MODE_IN_COMMUNICATION))
        assertFalse(isCallMode(AudioManager.MODE_NORMAL))
    }
}
