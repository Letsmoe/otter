package com.letsmoe.otter.filler

import com.letsmoe.otter.graphql.type.FillerKind
import com.letsmoe.otter.ui.player.PlayerEpisode
import com.letsmoe.otter.ui.player.skippedFillerText
import com.letsmoe.otter.ui.player.upNextLabel
import com.letsmoe.otter.ui.title.SeasonEpisode
import com.letsmoe.otter.ui.title.fillerLabelOf
import com.letsmoe.otter.ui.title.upNextEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun playerEpisode(id: String, season: Int, number: Int, filler: FillerKind?): PlayerEpisode {
    return PlayerEpisode(id, season, number, null, null, null, null, filler = filler)
}

private fun seasonEpisode(id: String, season: Int, number: Int, filler: FillerKind?, progress: Float? = null): SeasonEpisode {
    return SeasonEpisode(id, season, number, null, null, null, null, progress, null, filler = filler)
}

class FillerSkipTest {

    // Season 1: canon, filler, filler; season 2: mixed, canon, filler.
    private val episodes = listOf(
        playerEpisode("e2-2", 2, 2, FillerKind.CANON),
        playerEpisode("e1-2", 1, 2, FillerKind.FILLER),
        playerEpisode("e1-1", 1, 1, FillerKind.CANON),
        playerEpisode("e2-3", 2, 3, FillerKind.FILLER),
        playerEpisode("e1-3", 1, 3, FillerKind.FILLER),
        playerEpisode("e2-1", 2, 1, FillerKind.MIXED),
        playerEpisode("special", 0, 1, null),
    )

    @Test
    fun withoutSkippingTheNextEpisodeIsTheNextInOrderFillerOrNot() {
        val following = episodes.following("e1-1", skipFiller = false)!!

        assertEquals("e1-2", following.episode.id)
        assertEquals(0, following.skippedFiller)
    }

    @Test
    fun skippingJumpsOverFillerAcrossSeasonsAndCountsIt() {
        val following = episodes.following("e1-1", skipFiller = true)!!

        assertEquals("e2-1", following.episode.id)
        assertEquals(2, following.skippedFiller)
    }

    @Test
    fun aMixedEpisodeIsKept() {
        assertEquals("e2-1", episodes.following("e1-3", skipFiller = true)?.episode?.id)
    }

    @Test
    fun nothingFollowsWhenOnlyFillerIsLeft() {
        assertNull(episodes.following("e2-2", skipFiller = true))
        assertEquals("e2-3", episodes.following("e2-2", skipFiller = false)?.episode?.id)
    }

    @Test
    fun aSpecialOrUnknownEpisodeHasNoFollowing() {
        assertNull(episodes.following("special", skipFiller = true))
        assertNull(episodes.following("missing", skipFiller = false))
    }

    @Test
    fun theFirstEpisodeToWatchPassesOverFillerOnlyWhenAsked() {
        val filler = listOf(playerEpisode("a", 1, 1, FillerKind.FILLER), playerEpisode("b", 1, 2, FillerKind.CANON))

        assertEquals("a", filler.firstToWatch(skipFiller = false)?.id)
        assertEquals("b", filler.firstToWatch(skipFiller = true)?.id)
    }

    @Test
    fun playOnASeriesPageStartsTheNextEpisodeThatIsNotFiller() {
        val page = listOf(
            seasonEpisode("e1", 1, 1, FillerKind.CANON, progress = 1f),
            seasonEpisode("e2", 1, 2, FillerKind.FILLER),
            seasonEpisode("e3", 1, 3, FillerKind.CANON),
        ).map { episode -> episode.copy(nextEpisodeId = nextOf(episode.id)) }

        assertEquals("e2", upNextEpisode(page, skipFiller = false)?.episode?.id)
        assertEquals("e3", upNextEpisode(page, skipFiller = true)?.episode?.id)
    }

    private fun nextOf(id: String): String? {
        return mapOf("e1" to "e2", "e2" to "e3")[id]
    }

    @Test
    fun theCardNamesWhatWasSkipped() {
        val next = playerEpisode("e2-1", 2, 1, FillerKind.MIXED).copy(name = "Return")

        assertEquals("S2:E1 · Return", upNextLabel(next, 0))
        assertEquals("S2:E1 · Return · skips 2 filler", upNextLabel(next, 2))
        assertNull(skippedFillerText(0))
        assertEquals("Skipping 1 filler episode", skippedFillerText(1))
        assertEquals("Skipping 3 filler episodes", skippedFillerText(3))
    }

    @Test
    fun theEpisodeListMarksFillerAndMixedOnly() {
        assertEquals("Filler", fillerLabelOf(FillerKind.FILLER))
        assertEquals("Mixed canon/filler", fillerLabelOf(FillerKind.MIXED))
        assertNull(fillerLabelOf(FillerKind.CANON))
        assertNull(fillerLabelOf(null))
    }
}
