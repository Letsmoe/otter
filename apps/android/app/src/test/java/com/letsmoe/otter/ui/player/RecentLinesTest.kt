package com.letsmoe.otter.ui.player

import com.letsmoe.otter.cast.overlay.CaptionCue
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentLinesTest {

    private val cues = (1..6).map { index -> CaptionCue(index * 10.0, index * 10.0 + 2, "line $index") }

    @Test
    fun theLastFourLinesSaidSoFar() {
        val lines = recentLines(cues, 52.0, 0.0)

        assertEquals(listOf("line 2", "line 3", "line 4", "line 5"), lines.map { cue -> cue.text })
    }

    @Test
    fun laterCaptionsAreSaidLater() {
        val lines = recentLines(cues, 50.5, 1.0)

        assertEquals("line 4", lines.last().text)
    }

    @Test
    fun nothingBeforeTheFirstLine() {
        assertEquals(emptyList<CaptionCue>(), recentLines(cues, 5.0, 0.0))
    }
}
