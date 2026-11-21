package com.letsmoe.otter.cast.session

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class IdleScreenTest {

    private val browsing = SessionScreen.Picture(File("browse.jpg"))

    @Test
    fun withNothingChosenTheSessionShowsTheIdleScreen() {
        assertEquals(SessionScreen.Idle, idleScreenOf(askingStillWatching = false, chosen = SessionScreen.Idle))
    }

    @Test
    fun theTitleBeingBrowsedShowsWhileNothingPlays() {
        assertEquals(browsing, idleScreenOf(askingStillWatching = false, chosen = browsing))
    }

    @Test
    fun stillWatchingGoesBeforeTheBrowsePictureAndTheScreensaver() {
        assertEquals(SessionScreen.StillWatching, idleScreenOf(askingStillWatching = true, chosen = browsing))
        assertEquals(SessionScreen.StillWatching, idleScreenOf(askingStillWatching = true, chosen = SessionScreen.Idle))
    }

    @Test
    fun theSoundIsHeardTwoLeadsAfterItWasEncoded() {
        assertEquals(5000L, SESSION_SOUND_LEAD_MILLISECONDS)
    }
}
