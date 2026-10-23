package dev.neoworks.otter.ui.media

import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.graphql.AddToWatchlistMutation
import dev.neoworks.otter.graphql.ClearRatingMutation
import dev.neoworks.otter.graphql.FindStreamsMutation
import dev.neoworks.otter.graphql.InWatchlistQuery
import dev.neoworks.otter.graphql.RemoveFromWatchlistMutation
import dev.neoworks.otter.graphql.SetRatingMutation

/** Watchlist, rating and stream-lookup writes on one movie, series or episode. */
object TitleActions {

    suspend fun isInWatchlist(profileId: String, mediaId: String): Boolean {
        val data = OtterApi.client.query(InWatchlistQuery(mediaId, profileId)).execute().dataOrThrow()
        val title = data.title
        if (title == null) {
            return false
        }
        return title.inWatchlist
    }

    suspend fun setInWatchlist(profileId: String, mediaId: String, inWatchlist: Boolean) {
        if (inWatchlist) {
            OtterApi.client.mutation(AddToWatchlistMutation(profileId, mediaId)).execute().dataOrThrow()
            return
        }
        OtterApi.client.mutation(RemoveFromWatchlistMutation(profileId, mediaId)).execute().dataOrThrow()
    }

    suspend fun setRating(profileId: String, mediaId: String, rating: Double) {
        OtterApi.client.mutation(SetRatingMutation(profileId, mediaId, rating)).execute().dataOrThrow()
    }

    suspend fun clearRating(profileId: String, mediaId: String) {
        OtterApi.client.mutation(ClearRatingMutation(profileId, mediaId)).execute().dataOrThrow()
    }

    /** Asks every streams plugin again; results arrive through the streams subscription. */
    suspend fun findStreams(mediaId: String) {
        OtterApi.client.mutation(FindStreamsMutation(mediaId)).execute().dataOrThrow()
    }
}
