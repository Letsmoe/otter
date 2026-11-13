package com.letsmoe.otter.links

import com.letsmoe.otter.ui.title.LinkedEpisode
import com.letsmoe.otter.ui.title.SeasonEpisode
import com.letsmoe.otter.ui.title.findLinkedEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinksTest {

    @Test
    fun linksCarryTheTmdbIdAndAReadableName() {
        assertEquals("https://otter.letsmoe.com/movie/603-the-matrix", movieLink(603, "The Matrix"))
        assertEquals("https://otter.letsmoe.com/tv/1396-breaking-bad/s1e1", episodeLink(1396, "Breaking Bad", 1, 1))
        assertEquals("https://otter.letsmoe.com/movie/194-amelie", movieLink(194, "Amélie"))
        assertEquals("https://otter.letsmoe.com/movie/42", movieLink(42, "千と千尋"))
    }

    @Test
    fun sharedLinksReadBack() {
        assertEquals(SharedTarget.Movie(603), parseShareLink(movieLink(603, "The Matrix")))
        assertEquals(SharedTarget.Series(1396), parseShareLink(seriesLink(1396, "Breaking Bad")))
        assertEquals(SharedTarget.Episode(1396, 2, 13), parseShareLink(episodeLink(1396, "Breaking Bad", 2, 13)))
        assertEquals(SharedTarget.Movie(603), parseShareLink("https://otter.letsmoe.com/movie/603"))
    }

    @Test
    fun otherLinksAreIgnored() {
        assertNull(parseShareLink("https://example.com/movie/603"))
        assertNull(parseShareLink("https://otter.letsmoe.com/person/500"))
        assertNull(parseShareLink("https://otter.letsmoe.com/movie/the-matrix"))
        assertNull(parseShareLink("not a link"))
    }

    @Test
    fun aSharedEpisodeIsFoundByItsNumbers() {
        val episodes = listOf(episode("episode:1", 1, 1), episode("episode:2", 2, 1), episode("episode:3", 2, 2))
        assertEquals("episode:3", findLinkedEpisode(episodes, LinkedEpisode(seasonNumber = 2, episodeNumber = 2))?.id)
        assertEquals("episode:1", findLinkedEpisode(episodes, LinkedEpisode(seasonNumber = 1, episodeId = "episode:1"))?.id)
        assertNull(findLinkedEpisode(episodes, LinkedEpisode(seasonNumber = 3, episodeNumber = 1)))
        assertNull(findLinkedEpisode(episodes, LinkedEpisode()))
    }

    private fun episode(id: String, season: Int, number: Int): SeasonEpisode {
        return SeasonEpisode(id, season, number, null, null, null, null, null, null)
    }
}
