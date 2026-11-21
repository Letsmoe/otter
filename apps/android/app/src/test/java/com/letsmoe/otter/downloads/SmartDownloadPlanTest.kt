package com.letsmoe.otter.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartDownloadPlanTest {

    private val gigabyte = gigabytesToBytes(1)

    private fun candidate(mediaId: String, gigabytes: Double) = SmartCandidate(mediaId, (gigabytes * gigabyte).toLong())

    @Test
    fun anHourOfVideoTakesAboutAGigabyteAt720pAndTwoAndAHalfAt1080p() {
        assertEquals(gigabyte, estimatedBytes(60, SmartQuality.HD720))
        assertEquals((2.5 * gigabyte).toLong(), estimatedBytes(60, SmartQuality.HD1080))
    }

    @Test
    fun theBudgetIsShownAsPlaytime() {
        assertEquals(10.0, playtimeHours(10, SmartQuality.HD720), 0.001)
        assertEquals(4.0, playtimeHours(10, SmartQuality.HD1080), 0.001)
    }

    @Test
    fun watchedSmartDownloadsAreDeleted() {
        val existing = listOf(SmartDownload("d1", "episode:1", gigabyte), SmartDownload("d2", "episode:2", gigabyte))

        val plan = planSmartDownloads(10 * gigabyte, existing, setOf("episode:1"), emptyList(), emptySet())

        assertEquals(listOf("d1"), plan.delete.map { download -> download.downloadId })
    }

    @Test
    fun candidatesAreAddedInOrderWhileTheyFitTheBudget() {
        val existing = listOf(SmartDownload("d1", "episode:1", 2 * gigabyte))
        val candidates = listOf(candidate("episode:2", 1.0), candidate("episode:3", 1.0), candidate("movie:9", 2.5), candidate("movie:8", 0.5))

        val plan = planSmartDownloads(5 * gigabyte, existing, emptySet(), candidates, emptySet())

        // 3 GB are left: the two episodes fit, the long film does not, the short one does after them.
        assertEquals(listOf("episode:2", "episode:3"), plan.start.take(2).map { candidate -> candidate.mediaId })
        assertTrue(plan.start.none { candidate -> candidate.mediaId == "movie:9" })
        assertEquals(listOf("episode:2", "episode:3", "movie:8"), plan.start.map { candidate -> candidate.mediaId })
    }

    @Test
    fun spaceOfADeletedWatchedDownloadIsFreeForNewOnes() {
        val existing = listOf(SmartDownload("d1", "episode:1", 4 * gigabyte))

        val plan = planSmartDownloads(5 * gigabyte, existing, setOf("episode:1"), listOf(candidate("episode:2", 4.0)), emptySet())

        assertEquals(listOf("episode:2"), plan.start.map { candidate -> candidate.mediaId })
    }

    @Test
    fun mediaThatAlreadyHasADownloadOrWasRemovedIsNotDownloadedAgain() {
        val candidates = listOf(candidate("episode:2", 1.0), candidate("episode:3", 1.0))

        val plan = planSmartDownloads(10 * gigabyte, emptyList(), emptySet(), candidates, skipped = setOf("episode:2"))

        assertEquals(listOf("episode:3"), plan.start.map { candidate -> candidate.mediaId })
    }

    @Test
    fun aCandidateListedTwiceIsStartedOnce() {
        val candidates = listOf(candidate("movie:1", 1.0), candidate("movie:1", 1.0))

        val plan = planSmartDownloads(10 * gigabyte, emptyList(), emptySet(), candidates, emptySet())

        assertEquals(1, plan.start.size)
    }

    @Test
    fun candidatesAreTheNextEpisodesOfSeriesInProgressThenRecommendedFilms() {
        val continuing = listOf(
            ContinueSource(isSeries = true, mediaId = "series:1", runtimeMinutes = null, episodeId = "episode:5", episodeMinutes = 60, nextEpisodeId = "episode:6", nextEpisodeMinutes = 60),
            ContinueSource(isSeries = false, mediaId = "movie:7", runtimeMinutes = 120),
        )
        val recommended = listOf(RecommendedMovie("movie:1", 90), RecommendedMovie("movie:2", null), RecommendedMovie("movie:3", 100), RecommendedMovie("movie:4", 100))

        val candidates = smartCandidates(continuing, recommended, SmartQuality.HD720)

        assertEquals(
            listOf("episode:5", "episode:6", "movie:7", "movie:1", "movie:2", "movie:3"),
            candidates.map { candidate -> candidate.mediaId },
        )
        assertEquals(gigabyte, candidates[0].estimatedBytes)
        // An unknown runtime counts as a film's usual 100 minutes.
        assertEquals(estimatedBytes(100, SmartQuality.HD720), candidates[4].estimatedBytes)
    }

    @Test
    fun theLinkWithTheBestQualityWithinTheChosenOneIsPicked() {
        val links = listOf(
            DownloadLink("a", "480p", true),
            DownloadLink("b", "1080p", null),
            DownloadLink("c", "720p", true),
            DownloadLink("d", "720p", false),
            DownloadLink("e", "4K", true),
            DownloadLink("f", "HLS", true),
        )

        assertEquals("c", pickLinkFor(links, 720)?.id)
        assertEquals("b", pickLinkFor(links, 1080)?.id)
        assertNull(pickLinkFor(listOf(DownloadLink("f", "HLS", true)), 1080))
    }

    @Test
    fun qualityNamesAreReadAsHeights() {
        assertEquals(1080, heightOfQuality("1080p"))
        assertEquals(2160, heightOfQuality("4K"))
        assertNull(heightOfQuality("HLS"))
        assertNull(heightOfQuality(null))
    }
}
