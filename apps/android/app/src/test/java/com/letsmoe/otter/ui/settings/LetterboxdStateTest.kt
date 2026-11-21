package com.letsmoe.otter.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterboxdStateTest {

    @Test
    fun theSummaryCountsWhatWasAdded() {
        val progress = ImportProgress(phase = ImportPhase.DONE, watched = 312, rated = 1, watchlisted = 45, unmatchedCount = 7)
        assertEquals(
            listOf("312 films marked as watched", "1 rating set", "45 films added to My List", "7 films not matched"),
            progress.summaryLines(),
        )
    }

    @Test
    fun theSummaryLeavesOutTheNotMatchedLineWhenEverythingMatched() {
        val lines = ImportProgress(phase = ImportPhase.DONE, watched = 1).summaryLines()
        assertEquals(3, lines.size)
        assertEquals("1 film marked as watched", lines[0])
    }

    @Test
    fun progressShowsHowFarTheMatchingIs() {
        val running = ImportProgress(phase = ImportPhase.RUNNING, total = 340, processed = 120)
        assertEquals("Matching films with TMDB: 120 of 340", running.progressText())
        assertEquals(120f / 340f, running.progressFraction(), 0.0001f)
        assertEquals("Reading the export…", ImportProgress(phase = ImportPhase.RUNNING).progressText())
        assertEquals(0f, ImportProgress().progressFraction(), 0f)
    }

    @Test
    fun filmsAreNamedWithTheirYear() {
        assertEquals("Heat (1995)", MissingFilm("Heat", 1995, "").label())
        assertEquals("Heat", MissingFilm("Heat", null, "").label())
    }

    @Test
    fun aFollowedAccountSaysHowItsLastSyncWent() {
        assertEquals("Not read yet", FollowedAccount("ann", null, null, 0, 0).syncLine())
        assertEquals("Last sync: 2 new watched, 1 new rated", FollowedAccount("ann", "2026-11-21T10:00:00Z", null, 2, 1).syncLine())
        assertEquals("Letterboxd answered HTTP 403", FollowedAccount("ann", "2026-11-21T10:00:00Z", "Letterboxd answered HTTP 403", 0, 0).syncLine())
    }
}
