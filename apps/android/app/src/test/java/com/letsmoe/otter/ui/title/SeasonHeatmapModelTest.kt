package com.letsmoe.otter.ui.title

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonHeatmapModelTest {

    private fun episode(season: Int, number: Int, vote: Double = 0.0, progress: Float? = null) =
        SeasonEpisode("episode:$season$number", season, number, "Episode $number", null, null, null, progress, null, vote)

    @Test
    fun brighterColoursMeanHigherRatings() {
        val votes = listOf(6.0, 6.8, 7.5, 8.2, 8.8, 9.5)

        val luminances = votes.map { vote -> heatColor(vote).luminance() }

        assertEquals(luminances.sorted(), luminances)
        assertTrue("steps are visible: $luminances", luminances.zipWithNext().all { (low, high) -> high - low > 0.02f })
    }

    @Test
    fun ratingsOutsideTheScaleKeepItsEndColours() {
        assertEquals(heatColor(LOWEST_VOTE), heatColor(3.0))
        assertEquals(heatColor(HIGHEST_VOTE), heatColor(10.0))
    }

    @Test
    fun theLowestTileStillShowsOnTheDarkBackground() {
        assertTrue(heatColor(LOWEST_VOTE).luminance() > 0.03f)
    }

    @Test
    fun theMarkContrastsWithTheTile() {
        assertEquals(Color.White, markColorOn(heatColor(LOWEST_VOTE)))
        assertFalse(markColorOn(heatColor(HIGHEST_VOTE)) == Color.White)
    }

    @Test
    fun seasonsComeInOrderWithoutSpecialsAndTheirEpisodesToo() {
        val episodes = listOf(episode(2, 2), episode(0, 1), episode(1, 3), episode(2, 1), episode(1, 1))

        val seasons = heatmapSeasons(episodes)

        assertEquals(listOf(1, 2), seasons.map { season -> season.seasonNumber })
        assertEquals(listOf(1, 3), seasons[0].episodes.map { episode -> episode.episodeNumber })
        assertEquals(listOf(1, 2), seasons[1].episodes.map { episode -> episode.episodeNumber })
    }

    @Test
    fun anEpisodeCountsAsWatchedFromNinetyPercent() {
        assertTrue(isWatched(episode(1, 1, progress = 0.95f)))
        assertFalse(isWatched(episode(1, 1, progress = 0.5f)))
        assertFalse(isWatched(episode(1, 1, progress = null)))
    }

    @Test
    fun aHeatmapNeedsAFewRatedEpisodes() {
        val unrated = heatmapSeasons(listOf(episode(1, 1), episode(1, 2), episode(1, 3), episode(1, 4)))
        val rated = heatmapSeasons(listOf(episode(1, 1, 8.0), episode(1, 2, 7.5), episode(1, 3, 9.0)))

        assertFalse(worthShowing(unrated))
        assertTrue(worthShowing(rated))
    }

    @Test
    fun theRatingReadsWithOneDecimalOrSaysItIsMissing() {
        assertEquals("★ 8.7", heatmapRatingText(episode(1, 1, 8.7249)))
        assertEquals("★ 8.0", heatmapRatingText(episode(1, 1, 8.0)))
        assertEquals("Not rated yet", heatmapRatingText(episode(1, 1, 0.0)))
    }
}
