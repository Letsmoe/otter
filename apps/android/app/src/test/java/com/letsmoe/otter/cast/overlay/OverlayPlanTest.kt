package com.letsmoe.otter.cast.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPlanTest {

    private val srt = """
        1
        00:00:01,500 --> 00:00:04,000
        <i>Where were you?</i>

        2
        00:00:03,000 --> 00:00:05,250
        {\an8}Upstairs.
        With the dog.

        3
        not a cue

        4
        01:02:03.4 --> 01:02:05.000
        Later
    """.trimIndent()

    @Test
    fun srtCuesAreReadWithoutMarkup() {
        val cues = parseSrt(srt)

        assertEquals(3, cues.size)
        assertEquals(CaptionCue(1.5, 4.0, "Where were you?"), cues[0])
        assertEquals(CaptionCue(3.0, 5.25, "Upstairs.\nWith the dog."), cues[1])
        assertEquals(3723.4, cues[2].startSeconds, 0.0001)
    }

    @Test
    fun windowsLineEndingsAreRead() {
        val cues = parseSrt("1\r\n00:00:01,000 --> 00:00:02,000\r\nHello\r\n\r\n")

        assertEquals(listOf(CaptionCue(1.0, 2.0, "Hello")), cues)
    }

    @Test
    fun overlappingCaptionsShowTogether() {
        val plan = OverlayPlan(captions = parseSrt(srt))

        assertEquals("Where were you?", plan.frameAt(2.0).caption)
        assertEquals("Where were you?\nUpstairs.\nWith the dog.", plan.frameAt(3.5).caption)
        assertNull(plan.frameAt(5.25).caption)
    }

    @Test
    fun theSkipHintShowsDuringTheIntroOnly() {
        val plan = OverlayPlan(skips = listOf(SkipHint("Skip intro on your phone", 30.0, 90.0)))

        assertNull(plan.frameAt(29.9).skipLabel)
        assertEquals("Skip intro on your phone", plan.frameAt(30.0).skipLabel)
        assertNull(plan.frameAt(90.0).skipLabel)
    }

    @Test
    fun theNextEpisodeCountsDownFromTheCredits() {
        val plan = OverlayPlan(upNext = UpNextHint("S1:E2 · Pilot", 1200.0, 10.0, null))

        assertNull(plan.frameAt(1199.0).upNextSecondsLeft)
        assertEquals(10, plan.frameAt(1200.0).upNextSecondsLeft)
        assertEquals(5, plan.frameAt(1205.2).upNextSecondsLeft)
        assertEquals(1, plan.frameAt(1209.9).upNextSecondsLeft)
        assertNull(plan.frameAt(1210.0).upNextSecondsLeft)
    }

    @Test
    fun framesAreEqualWhileNothingChanges() {
        val plan = OverlayPlan(captions = parseSrt(srt))

        assertEquals(plan.frameAt(1.6), plan.frameAt(2.9))
        assertTrue(plan.frameAt(0.5).isEmpty)
    }
}
