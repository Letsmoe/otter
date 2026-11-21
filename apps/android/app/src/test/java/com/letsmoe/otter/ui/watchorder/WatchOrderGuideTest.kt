package com.letsmoe.otter.ui.watchorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchOrderGuideTest {

    private fun part(number: Int, status: PartStatus, progress: Float = 0f, released: Boolean = true, year: Int? = 2000 + number) =
        GuidePart(number, "movie:$number", "Film $number", year, null, status, progress, released)

    private fun guide(vararg parts: GuidePart, nextNumber: Int? = null, name: String = "Trilogy Collection") =
        WatchOrderGuide("collection:1", name, null, null, null, parts.toList(), nextNumber)

    @Test
    fun headlineDropsTheCollectionSuffix() {
        assertEquals("The Matrix", franchiseHeadline("The Matrix Collection"))
        assertEquals("Harry Potter", franchiseHeadline("Harry Potter - Kollektion"))
        assertEquals("Die Tribute von Panem", franchiseHeadline("Die Tribute von Panem Filmreihe"))
        assertEquals("Collection", franchiseHeadline("Collection"))
        assertEquals("Star Wars Saga", franchiseHeadline("Star Wars Saga"))
    }

    @Test
    fun yearSpanCoversFirstToLastFilm() {
        assertEquals("2001–2011", yearSpanOf(listOf(2003, 2001, 2011)))
        assertEquals("1999", yearSpanOf(listOf(1999)))
        assertNull(yearSpanOf(emptyList()))
    }

    @Test
    fun watchedCountAndStartedFollowTheParts() {
        val untouched = guide(part(1, PartStatus.NOT_YET), part(2, PartStatus.NOT_YET))
        assertFalse(untouched.isStarted)
        val started = guide(part(1, PartStatus.WATCHED), part(2, PartStatus.IN_PROGRESS, 0.3f), part(3, PartStatus.NOT_YET), nextNumber = 2)
        assertTrue(started.isStarted)
        assertEquals(1, started.watchedCount)
        assertEquals(2, started.next?.number)
    }

    @Test
    fun continueButtonStartsOrContinues() {
        val untouched = guide(part(1, PartStatus.NOT_YET), part(2, PartStatus.NOT_YET), nextNumber = 1)
        assertEquals("Start with Film 1", continueLabel(untouched))
        val started = guide(part(1, PartStatus.WATCHED), part(2, PartStatus.NOT_YET), nextNumber = 2)
        assertEquals("Continue with Film 2", continueLabel(started))
    }

    @Test
    fun noContinueButtonWhenEverythingOutIsWatched() {
        val finished = guide(part(1, PartStatus.WATCHED), part(2, PartStatus.NOT_YET, released = false), nextNumber = null)
        assertNull(continueLabel(finished))
    }

    @Test
    fun progressTextCountsWatchedFilms() {
        val started = guide(part(1, PartStatus.WATCHED), part(2, PartStatus.NOT_YET), part(3, PartStatus.NOT_YET), part(4, PartStatus.NOT_YET))
        assertEquals("1 of 4 films watched", progressLabel(started))
        assertEquals(0.25f, watchedFraction(started), 0.001f)
        assertEquals(0f, watchedFraction(guide()), 0f)
    }

    @Test
    fun rowStatusSaysWhereTheProfileIs() {
        assertEquals("Watched", statusLabel(part(1, PartStatus.WATCHED)))
        assertEquals("In progress · 40% watched", statusLabel(part(1, PartStatus.IN_PROGRESS, 0.4f)))
        assertEquals("Not watched yet", statusLabel(part(1, PartStatus.NOT_YET)))
        assertEquals("Coming soon", statusLabel(part(1, PartStatus.NOT_YET, released = false)))
    }

    @Test
    fun rowFactsMarkTheNextFilm() {
        assertEquals("2003", partFacts(part(3, PartStatus.NOT_YET, year = 2003), isNext = false))
        assertEquals("2003 · Up next", partFacts(part(3, PartStatus.NOT_YET, year = 2003), isNext = true))
        assertEquals("Up next", partFacts(part(3, PartStatus.NOT_YET, year = null), isNext = true))
    }
}
