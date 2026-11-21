package com.letsmoe.otter.cast.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowsePreviewTest {

    @Test
    fun theTelevisionGetsTheOriginalSizeOfThePageBackdrop() {
        val bannerUrl = "https://image.tmdb.org/t/p/w1280/8sNiAPPYU14PUepFNeSNGUTiHW.jpg"

        assertEquals("https://image.tmdb.org/t/p/original/8sNiAPPYU14PUepFNeSNGUTiHW.jpg", televisionBackdropUrl(bannerUrl))
    }

    @Test
    fun aPosterKeepsItsSize() {
        val posterUrl = "https://image.tmdb.org/t/p/w342/abc.jpg"

        assertEquals(posterUrl, televisionBackdropUrl(posterUrl))
    }

    @Test
    fun noBannerMeansNoBackdrop() {
        assertNull(televisionBackdropUrl(null))
    }

    @Test
    fun theFactsReadAsOneLine() {
        val preview = BrowsePreview("movie:157336", "Interstellar", listOf("2014", "2h 49m", "★ 8.5"), null, null)

        assertEquals("2014 · 2h 49m · ★ 8.5", preview.factsLine)
    }

    @Test
    fun theAgeRatingLeadsTheFactsWhenKnown() {
        val facts = listOf("2014", "2h 49m")

        assertEquals(listOf("FSK 12", "2014", "2h 49m"), withAgeRating(facts, "FSK 12"))
        assertEquals(facts, withAgeRating(facts, null))
        assertEquals(facts, withAgeRating(facts, ""))
    }
}
