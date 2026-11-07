package com.letsmoe.otter.ui.title

import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.SeriesEpisodesQuery
import com.letsmoe.otter.profiles.ProfileSession
import kotlinx.coroutines.CancellationException

/** The episode a series' Play starts, as on its page; null when it has none or the lookup fails. */
suspend fun fetchUpNextEpisodeId(seriesId: String): String? {
    val profileId = ProfileSession.activeProfile.value?.id.orEmpty()
    return try {
        OtterApi.awaitBackend()
        val data = OtterApi.client.query(SeriesEpisodesQuery(seriesId, profileId)).execute().dataOrThrow()
        upNextEpisode(data.title?.toEpisodes().orEmpty())?.episode?.id
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        null
    }
}
