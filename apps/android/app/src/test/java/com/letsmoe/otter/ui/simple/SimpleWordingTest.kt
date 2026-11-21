package com.letsmoe.otter.ui.simple

import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.ui.media.RailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimpleWordingTest {

    private fun item(mediaType: MediaType, year: Int? = 2019, subtitle: String? = null, episodeId: String? = null): RailItem {
        return RailItem(id = "title-1", mediaType = mediaType, title = "Title", year = year, posterUrl = null, subtitle = subtitle, episodeId = episodeId)
    }

    @Test
    fun episodeCodesBecomePlainWords() {
        assertEquals("Season 2, episode 5 · The Wedding", plainEpisodeLabel("S2E5 · The Wedding"))
        assertEquals("Season 1, episode 3", plainEpisodeLabel("S1E3"))
    }

    @Test
    fun otherTextStaysAsItIs() {
        assertEquals("2019", plainEpisodeLabel("2019"))
    }

    @Test
    fun anEpisodeCardNamesTheEpisode() {
        val card = item(MediaType.SERIES, subtitle = "S1E3 · Pilot", episodeId = "episode-9")
        assertEquals("Season 1, episode 3 · Pilot", simpleSubtitle(card))
    }

    @Test
    fun aTitleCardNamesItsKindAndYear() {
        assertEquals("Movie · 2019", simpleSubtitle(item(MediaType.MOVIE)))
        assertEquals("TV show · 2019", simpleSubtitle(item(MediaType.SERIES, subtitle = "2019")))
        assertEquals("Movie", simpleSubtitle(item(MediaType.MOVIE, year = null)))
    }

    @Test
    fun aMoviePlaysDirectlyAndASeriesNeedsItsEpisode() {
        assertEquals("title-1", directPlayId(item(MediaType.MOVIE)))
        assertNull(directPlayId(item(MediaType.SERIES)))
        assertEquals("episode-9", directPlayId(item(MediaType.SERIES, episodeId = "episode-9")))
    }

    @Test
    fun leavingNeedsThePinWhenThereIsOne() {
        assertEquals(LeaveCheck.PIN, leaveCheckFor(profileHasPin = true))
        assertEquals(LeaveCheck.CONFIRMATION, leaveCheckFor(profileHasPin = false))
    }
}
