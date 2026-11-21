package com.letsmoe.otter.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkBadgesTest {

    private fun report(
        claimedHeight: Int = 1080,
        measuredHeight: Int = 1080,
        overclaimed: Boolean = false,
        bitrateBps: Int = 0,
        stalls: Int = 0,
        stallMillis: Int = 0,
        firstFrameMillis: Int = 3_000,
        plays: Int = 1,
        roughPlays: Int = 0,
    ): LinkReportInfo {
        return LinkReportInfo(claimedHeight, measuredHeight, overclaimed, bitrateBps, stalls, stallMillis, firstFrameMillis, plays, roughPlays)
    }

    private fun texts(report: LinkReportInfo?): List<String> = linkBadges(report).map { badge -> badge.text }

    @Test
    fun aLinkNobodyPlayedHasNoBadges() {
        assertTrue(linkBadges(null).isEmpty())
        assertTrue(linkBadges(report(plays = 0)).isEmpty())
    }

    @Test
    fun aLinkThatDeliveredWhatItClaimedShowsItsResolutionAndNoStalls() {
        assertEquals(listOf("1080p", "No stalls"), texts(report()))
    }

    @Test
    fun anOverclaimNamesBothResolutions() {
        val badges = linkBadges(report(measuredHeight = 720, overclaimed = true))
        assertEquals("720p, not 1080p", badges.first().text)
        assertEquals(BadgeTone.WARNING, badges.first().tone)
    }

    @Test
    fun stallsAreCountedWithTheirTime() {
        assertEquals("3 stalls (12 s)", texts(report(stalls = 3, stallMillis = 12_000))[1])
        assertEquals("1 stall (4 s)", texts(report(stalls = 1, stallMillis = 4_000))[1])
    }

    @Test
    fun aLinkThatStalledBeforeIsMarkedEvenWhenTheLastPlayWasSmooth() {
        assertEquals("Stalled before", texts(report(roughPlays = 1, plays = 2))[1])
    }

    @Test
    fun onlyQuickAndSlowStartsGetABadge() {
        assertTrue(texts(report(firstFrameMillis = 1_000)).contains("Quick start"))
        assertTrue(texts(report(firstFrameMillis = 9_000)).contains("Slow start, 9 s"))
        assertEquals(2, texts(report(firstFrameMillis = 3_000)).size)
    }

    @Test
    fun theBitrateShowsInMegabits() {
        assertTrue(texts(report(bitrateBps = 2_500_000)).contains("2.5 Mbit/s"))
    }

    @Test
    fun resolutionsRoundToTheStandardSteps() {
        assertEquals("1080p", resolutionLabel(1080))
        assertEquals("1080p", resolutionLabel(1040))
        assertEquals("720p", resolutionLabel(720))
        assertEquals("480p", resolutionLabel(480))
        assertEquals("4K", resolutionLabel(2160))
        assertEquals("240p", resolutionLabel(240))
    }

    @Test
    fun smoothLinksSortBeforeUnknownOnesBeforeRoughOnes() {
        assertEquals(0, linkReportRank(report()))
        assertEquals(1, linkReportRank(null))
        assertEquals(2, linkReportRank(report(roughPlays = 1)))
        assertEquals(2, linkReportRank(report(overclaimed = true)))
    }
}
