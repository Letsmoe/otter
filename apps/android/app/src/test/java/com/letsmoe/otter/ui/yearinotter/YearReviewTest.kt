package com.letsmoe.otter.ui.yearinotter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class YearReviewTest {

    private val sample = SampleYear.review

    private fun title(name: String, isSeries: Boolean = true) = YearTitle(name, null, isSeries)

    @Test
    fun aFullYearHasEveryCardInOrder() {
        val cards = yearCards(sample, "Alex")
        assertEquals(
            listOf("Intro", "Hours", "Finished", "TopSeries", "Genres", "Binge", "Rewatched", "FirstAndLast"),
            cards.map { card -> card::class.simpleName },
        )
        assertEquals(YearCard.Intro(2026, "Alex"), cards.first())
    }

    @Test
    fun cardsWithoutDataAreLeftOut() {
        val sparse = sample.copy(topSeries = null, topGenres = emptyList(), binge = null, rewatched = null, first = null, last = null)
        assertEquals(listOf("Intro", "Hours", "Finished"), yearCards(sparse, "Alex").map { card -> card::class.simpleName })
    }

    @Test
    fun bookendsNeedBothTitles() {
        val onlyFirst = sample.copy(last = null)
        assertFalse(yearCards(onlyFirst, "Alex").any { card -> card is YearCard.FirstAndLast })
    }

    @Test
    fun aYearWithoutWatchingIsOneQuietCard() {
        val quiet = sample.copy(hasActivity = false)
        assertEquals(listOf<YearCard>(YearCard.Quiet(2026)), yearCards(quiet, "Alex"))
    }

    @Test
    fun hoursReadAsWholeNumbersExceptInShortYears() {
        assertEquals("187", hoursHeadline(187.4))
        assertEquals("188", hoursHeadline(187.6))
        assertEquals("3.5", hoursHeadline(3.5))
    }

    @Test
    fun hoursCaptionPicksDaysOrFilms() {
        assertEquals("That's about 8 days, back to back.", hoursCaption(187.4))
        assertEquals("That's about 10 films' worth.", hoursCaption(20.0))
        assertEquals("A good start.", hoursCaption(1.5))
    }

    @Test
    fun countsAreSingularForOne() {
        assertEquals("1 episode", finishedLine(1, "episode", "episodes"))
        assertEquals("12 episodes", finishedLine(12, "episode", "episodes"))
        assertEquals("0 episodes", finishedLine(0, "episode", "episodes"))
    }

    @Test
    fun datesReadAsDayAndMonth() {
        assertEquals("14 March", shortDate("2026-03-14", Locale.ENGLISH))
        assertEquals("not a date", shortDate("not a date", Locale.ENGLISH))
    }

    @Test
    fun rewatchTextDependsOnFilmOrSeries() {
        assertTrue(rewatchLine(YearReview.Rewatched(title("Heat", isSeries = false), 1)).startsWith("You came back"))
        assertEquals("You went back to 14 episodes of it.", rewatchLine(YearReview.Rewatched(title("The Office"), 14)))
        assertEquals("You went back to 1 episode of it.", rewatchLine(YearReview.Rewatched(title("The Office"), 1)))
    }

    @Test
    fun decemberAndJanuaryOfferTheReview() {
        assertTrue(isReviewSeason(LocalDate.of(2026, 12, 1)))
        assertTrue(isReviewSeason(LocalDate.of(2027, 1, 31)))
        assertFalse(isReviewSeason(LocalDate.of(2026, 6, 15)))
    }

    @Test
    fun januaryLooksBackOnTheYearThatEnded() {
        assertEquals(2026, reviewYear(LocalDate.of(2026, 12, 20)))
        assertEquals(2026, reviewYear(LocalDate.of(2027, 1, 5)))
        assertEquals(2026, reviewYear(LocalDate.of(2026, 7, 1)))
    }
}
