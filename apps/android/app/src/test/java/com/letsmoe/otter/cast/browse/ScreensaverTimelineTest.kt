package com.letsmoe.otter.cast.browse

import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.ui.media.RailItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreensaverTimelineTest {

    private fun item(id: String, backdropUrl: String?) = RailItem(id, MediaType.MOVIE, "Title $id", 2020, null, backdropUrl = backdropUrl)

    @Test
    fun aSlideStaysUntilItsFadeBegins() {
        val moment = screensaverMomentAt(SLIDE_SECONDS - FADE_SECONDS - 0.1, slideCount = 3)

        assertEquals(ScreensaverMoment(slide = 0, next = 1, fade = 0f), moment)
    }

    @Test
    fun theFadeToTheNextSlideRunsFromZeroToOne() {
        val halfway = screensaverMomentAt(SLIDE_SECONDS - FADE_SECONDS / 2, slideCount = 3)
        val almostOver = screensaverMomentAt(SLIDE_SECONDS - 0.01, slideCount = 3)

        assertEquals(0.5f, halfway.fade, 0.01f)
        assertEquals(1f, almostOver.fade, 0.01f)
        assertEquals(1, halfway.next)
    }

    @Test
    fun theNextSlideIsShownAfterTheFade() {
        val moment = screensaverMomentAt(SLIDE_SECONDS + 1, slideCount = 3)

        assertEquals(ScreensaverMoment(slide = 1, next = 2, fade = 0f), moment)
    }

    @Test
    fun theLastSlideFadesBackToTheFirst() {
        val moment = screensaverMomentAt(3 * SLIDE_SECONDS - FADE_SECONDS / 2, slideCount = 3)

        assertEquals(2, moment.slide)
        assertEquals(0, moment.next)
        assertEquals(0, screensaverMomentAt(3 * SLIDE_SECONDS + 1, slideCount = 3).slide)
    }

    @Test
    fun aSingleSlideNeverFades() {
        val moment = screensaverMomentAt(SLIDE_SECONDS - 0.5, slideCount = 1)

        assertEquals(ScreensaverMoment(slide = 0, next = 0, fade = 0f), moment)
    }

    @Test
    fun slidesComeFromWhatIsWatchedThenMyListOncePerTitleWithABackdrop() {
        val watching = listOf(item("movie:1", "https://image.tmdb.org/t/p/w1280/a.jpg"), item("movie:2", null))
        val myList = listOf(item("movie:1", "https://image.tmdb.org/t/p/w1280/a.jpg"), item("series:3", "https://image.tmdb.org/t/p/w1280/c.jpg"))

        val slides = screensaverSlidesOf(watching, myList)

        assertEquals(listOf("movie:1", "series:3"), slides.map { slide -> slide.titleId })
        assertEquals("https://image.tmdb.org/t/p/original/a.jpg", slides[0].backdropUrl)
    }
}
