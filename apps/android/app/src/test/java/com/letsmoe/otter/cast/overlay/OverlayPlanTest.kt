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
    fun aDelayMovesBothCaptionLanguagesButNotTheHints() {
        val plan = OverlayPlan(
            captions = listOf(CaptionCue(10.0, 12.0, "Where were you?")),
            skips = listOf(SkipHint("Skip intro on your phone", 10.0, 12.0)),
            secondCaptions = listOf(CaptionCue(10.0, 12.0, "Wo warst du?")),
        )

        val later = plan.frameAt(10.5, captionDelaySeconds = 1.0)
        assertNull(later.caption)
        assertNull(later.secondCaption)
        assertEquals("Skip intro on your phone", later.skipLabel)

        val shifted = plan.frameAt(11.5, captionDelaySeconds = 1.0)
        assertEquals("Where were you?", shifted.caption)
        assertEquals("Wo warst du?", shifted.secondCaption)

        assertEquals("Where were you?", plan.frameAt(9.5, captionDelaySeconds = -1.0).caption)
    }

    @Test
    fun theTitleCardShowsForItsSecondsOnly() {
        val card = TitleCard("Severance", "S1:E2 · Half Loop", 300.0, 306.0)
        val plan = OverlayPlan(titleCard = card)

        assertNull(plan.frameAt(299.9).titleCard)
        assertEquals(card, plan.frameAt(300.0).titleCard)
        assertEquals(card, plan.frameAt(305.9).titleCard)
        assertNull(plan.frameAt(306.0).titleCard)
    }

    @Test
    fun theCardFillsAsItCountsDown() {
        val plan = OverlayPlan(upNext = UpNextHint("S1:E2 · Pilot", 1200.0, 10.0, null))

        assertNull(plan.frameAt(1199.9).upNextProgress)
        assertEquals(0f, plan.frameAt(1200.0).upNextProgress!!, 0.001f)
        assertEquals(0.5f, plan.frameAt(1205.0).upNextProgress!!, 0.01f)
        assertTrue(plan.frameAt(1209.99).upNextProgress!! > 0.99f)
        assertNull(plan.frameAt(1210.0).upNextProgress)
    }

    @Test
    fun framesAreEqualWhileNothingChanges() {
        val plan = OverlayPlan(captions = parseSrt(srt))

        assertEquals(plan.frameAt(1.6), plan.frameAt(2.9))
        assertTrue(plan.frameAt(0.5).isEmpty)
    }
}
