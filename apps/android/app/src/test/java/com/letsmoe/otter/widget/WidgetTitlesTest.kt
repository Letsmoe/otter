package com.letsmoe.otter.widget

import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.ui.media.RailItem
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTitlesTest {

    private fun item(id: String, episodeId: String? = null) = RailItem(
        id = id,
        mediaType = MediaType.SERIES,
        title = "Bosch",
        year = 2014,
        posterUrl = null,
        episodeId = episodeId,
    )

    @Test
    fun aMovieIsPlayedByItsOwnId() {
        assertEquals("movie:603", widgetPlayMediaId(item("movie:603")))
    }

    @Test
    fun aSeriesContinuesWithTheEpisodeItLeftOffAt() {
        assertEquals("episode:7", widgetPlayMediaId(item("series:1", episodeId = "episode:7")))
    }
}
