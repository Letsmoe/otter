package com.letsmoe.otter.ui.home

import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.TimeSlotQuery
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.media.toRailItem
import com.letsmoe.otter.ui.media.withEpisode

/** One thing that fits the minutes the viewer has. */
data class TimeSlotSuggestion(
    val item: RailItem,
    /** The movie or episode to play. */
    val mediaId: String,
    val minutes: Int,
    val reason: String,
)

object TimeSlotRepository {

    /** What fits into [minutes]; throws when the backend can't be asked. */
    suspend fun suggestions(profileId: String, minutes: Int): List<TimeSlotSuggestion> {
        val data = OtterApi.client.query(TimeSlotQuery(profileId, minutes)).execute().dataOrThrow()
        return data.timeSlot.map { entry ->
            var item = entry.title.titleCard.toRailItem()
            var mediaId = item.id
            if (entry.episode != null) {
                item = item.withEpisode(entry.episode.episodeRef)
                mediaId = entry.episode.episodeRef.id
            }
            TimeSlotSuggestion(item, mediaId, entry.minutes, entry.reason)
        }
    }
}
