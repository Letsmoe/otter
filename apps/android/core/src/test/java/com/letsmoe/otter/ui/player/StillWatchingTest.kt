package com.letsmoe.otter.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StillWatchingTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var timeouts = 0
    private val stillWatching = StillWatching(scope, answerMilliseconds = 50) { timeouts++ }

    @After
    fun stopTimers() {
        scope.cancel()
    }

    // An episode that ends without a touch; true when the next one is held back.
    private fun untouchedEpisodeEnds(): Boolean {
        stillWatching.episodeStarted()
        return stillWatching.holdsBackNext()
    }

    @Test
    fun theThirdEpisodeInARowWithoutATouchAsksBeforeTheNext() {
        assertFalse(untouchedEpisodeEnds())
        assertFalse(untouchedEpisodeEnds())
        assertTrue(untouchedEpisodeEnds())
        assertTrue(stillWatching.asking.value)
    }

    @Test
    fun aTouchDuringAnEpisodeStartsTheCountAgain() {
        assertFalse(untouchedEpisodeEnds())
        assertFalse(untouchedEpisodeEnds())
        stillWatching.episodeStarted()
        stillWatching.touched()
        assertFalse(stillWatching.holdsBackNext())

        assertFalse(untouchedEpisodeEnds())
        assertFalse(untouchedEpisodeEnds())
        assertTrue(untouchedEpisodeEnds())
    }

    @Test
    fun keepingWatchingLetsTheEpisodeStartAndCountsAgainFromNothing() {
        repeat(3) { untouchedEpisodeEnds() }

        stillWatching.confirm()

        assertFalse(stillWatching.asking.value)
        assertFalse(stillWatching.holdsBackNext())
        assertFalse(untouchedEpisodeEnds())
        assertFalse(untouchedEpisodeEnds())
        assertTrue(untouchedEpisodeEnds())
    }

    @Test
    fun theNextEpisodeStaysHeldBackWhileTheQuestionIsOpen() {
        repeat(3) { untouchedEpisodeEnds() }

        assertTrue(stillWatching.holdsBackNext())
        stillWatching.touched()
        assertTrue(stillWatching.holdsBackNext())
        assertTrue(stillWatching.asking.value)
    }

    @Test
    fun decliningClosesTheQuestionWithoutPlayingOn() {
        repeat(3) { untouchedEpisodeEnds() }

        stillWatching.decline()

        assertFalse(stillWatching.asking.value)
        assertEquals(0, timeouts)
    }

    @Test
    fun noAnswerStopsPlayback() {
        repeat(3) { untouchedEpisodeEnds() }

        Thread.sleep(300)

        assertFalse(stillWatching.asking.value)
        assertEquals(1, timeouts)
    }

    @Test
    fun anAnswerInTimeCancelsTheStop() {
        repeat(3) { untouchedEpisodeEnds() }
        stillWatching.confirm()

        Thread.sleep(300)

        assertEquals(0, timeouts)
    }
}
