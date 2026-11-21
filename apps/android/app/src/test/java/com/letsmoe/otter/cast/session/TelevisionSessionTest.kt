package com.letsmoe.otter.cast.session

import com.letsmoe.otter.cast.overlay.OverlayJob
import com.letsmoe.otter.cast.overlay.OverlayPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TelevisionSessionTest {

    private var elapsedMicroseconds = 0L
    private val failures = mutableListOf<SessionJob>()
    private val session = TelevisionSession(1080, { job, _ -> failures.add(job) }, SessionClock { elapsedMicroseconds })

    private fun titleJob(startSeconds: Double): SessionScreen.Title {
        return SessionScreen.Title(OverlayJob("http://127.0.0.1/title.m3u8", null, startSeconds, 1080, OverlayPlan(), onFailure = { _, _ -> }))
    }

    private fun take(atMicroseconds: Long): SessionJob? = runBlocking { session.takeNext(atMicroseconds) }

    @Test
    fun aNewSessionShowsTheIdleScreenFromItsStart() {
        val job = take(0)

        assertEquals(SessionScreen.Idle, job?.screen)
        assertEquals(0L, job?.startMicroseconds)
    }

    @Test
    fun theClockStartsWhenTheBackendTakesTheFirstJob() {
        elapsedMicroseconds = 1_000_000
        assertEquals(0L, session.clockMicroseconds())

        take(0)
        elapsedMicroseconds = 3_000_000

        assertEquals(2_000_000L, session.clockMicroseconds())
    }

    @Test
    fun aPausedTelevisionStopsTheClock() {
        take(0)
        elapsedMicroseconds = 2_000_000
        session.setPlaying(false)
        elapsedMicroseconds = 9_000_000

        assertEquals(2_000_000L, session.clockMicroseconds())
    }

    @Test
    fun aShownScreenReplacesTheJobAndStopsItsEncoder() {
        val idle = take(0)!!
        val encoder = Job()
        session.onEncoderStarted(idle, encoder)

        val loading = session.show(SessionScreen.Loading("Severance"))
        val next = take(1_200_000)

        assertTrue(encoder.isCancelled)
        assertSame(loading, next)
        assertEquals(1_200_000L, next?.startMicroseconds)
    }

    @Test
    fun aTitleThatEndsIsFollowedByTheIdleScreen() {
        take(0)
        val title = session.show(titleJob(0.0))
        take(1_000_000)

        session.onCompleted(title, 60_000_000)
        val next = take(60_000_000)

        assertEquals(60_000_000L, title.endMicroseconds)
        assertEquals(SessionScreen.Idle, next?.screen)
    }

    @Test
    fun aStillScreenThatEndsStartsAgain() {
        take(0)
        val loading = session.show(SessionScreen.Loading(null))
        take(500_000)

        session.onCompleted(loading, 120_500_000)
        val next = take(120_500_000)

        assertEquals(SessionScreen.Loading(null), next?.screen)
        assertFalse(next === loading)
    }

    @Test
    fun aReplacedJobThatEndsChangesNothing() {
        val idle = take(0)!!
        val title = session.show(titleJob(30.0))

        session.onCompleted(idle, 5_000_000)

        assertSame(title, session.currentJob)
    }

    @Test
    fun aSecondReaderGetsTheTitleAgainFromWhereTheTelevisionIs() {
        take(0)
        session.show(titleJob(600.0))
        val first = take(0)!!
        session.onEncoderStarted(first, Job())
        elapsedMicroseconds = 12_500_000

        val again = take(0)

        val screen = again?.screen as SessionScreen.Title
        assertEquals(610.0, screen.overlay.startSeconds, 0.001)
    }

    @Test
    fun aJobWhoseReaderLeftIsHandedOutAgain() {
        take(0)
        val title = session.show(titleJob(10.0))
        take(0)

        session.onReaderGone(title)
        val again = take(0)

        assertTrue(again?.screen is SessionScreen.Title)
        assertFalse(again === title)
    }

    @Test
    fun onlyTheCurrentJobsFailureIsReported() {
        val idle = take(0)!!
        val title = session.show(titleJob(0.0))

        session.onEncoderFailed(idle, IllegalStateException("old"))
        session.onEncoderFailed(title, IllegalStateException("new"))

        assertEquals(listOf(title), failures)
    }

    @Test
    fun aJobsFirstSampleSitsWhereTheJobStartsWhateverItsTimestamp() {
        take(0)
        val pacing = JobPacing(session, jobStartMicroseconds = 4_000_000)
        // The clock at 2 s lets the encoder write up to 4.5 s of the session.
        elapsedMicroseconds = 2_000_000

        assertTrue(pacing.awaitTurn(600_000_000))
        assertTrue(pacing.awaitTurn(600_400_000))
        assertEquals(4_400_000L, pacing.lastSessionMicroseconds)
    }

    @Test
    fun aCancelledJobDropsItsSamples() {
        take(0)
        val pacing = JobPacing(session, jobStartMicroseconds = 60_000_000)

        pacing.cancel()

        assertFalse(pacing.awaitTurn(0))
    }

    @Test
    fun aClosedSessionHandsOutNothing() {
        session.close()

        assertNull(take(0))
    }
}
