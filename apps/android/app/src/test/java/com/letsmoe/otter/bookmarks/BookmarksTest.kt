package com.letsmoe.otter.bookmarks

import com.letsmoe.otter.cast.overlay.CaptionCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarksTest {

    private val cues = listOf(
        CaptionCue(10.0, 12.0, "Wake up, Neo."),
        CaptionCue(11.0, 13.0, "The Matrix has you."),
        CaptionCue(30.0, 32.0, "Follow the white rabbit."),
    )

    @Test
    fun theLineOnScreenIsKept() {
        assertEquals("Follow the white rabbit.", captionAtMoment(cues, 31.0, 0.0))
    }

    @Test
    fun twoSpeakersAtOnceAreKeptTogether() {
        assertEquals("Wake up, Neo. The Matrix has you.", captionAtMoment(cues, 11.5, 0.0))
    }

    @Test
    fun withoutALineOnScreenTheOneThatJustEndedIsKept() {
        assertEquals("The Matrix has you.", captionAtMoment(cues, 15.0, 0.0))
        assertNull(captionAtMoment(cues, 20.0, 0.0))
    }

    @Test
    fun shiftedCaptionsAreReadWhereTheViewerSawThem() {
        // Captions 2 seconds late: the line of second 10 shows at second 12.
        assertEquals("Wake up, Neo.", captionAtMoment(cues, 12.5, 2.0))
        assertEquals("Wake up, Neo. The Matrix has you.", captionAtMoment(cues, 13.5, 2.0))
    }

    @Test
    fun aLineBreakInsideALineBecomesASpace() {
        val broken = listOf(CaptionCue(0.0, 5.0, "First\nsecond"))

        assertEquals("First second", captionAtMoment(broken, 1.0, 0.0))
    }

    @Test
    fun noCaptionsGiveNoLine() {
        assertNull(captionAtMoment(emptyList(), 10.0, 0.0))
    }

    @Test
    fun theRoomIsToldWhatWasBookmarked() {
        assertEquals("Bookmarked 12:34 · “Wake up, Neo.”", bookmarkChatText("12:34", "Wake up, Neo."))
        assertEquals("Bookmarked 12:34", bookmarkChatText("12:34", null))
        assertEquals("Bookmarked 12:34", bookmarkChatText("12:34", ""))
    }
}
