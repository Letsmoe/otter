package com.letsmoe.otter.ui.media

import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.AddToWatchlistMutation
import com.letsmoe.otter.graphql.ClearRatingMutation
import com.letsmoe.otter.graphql.FindStreamsMutation
import com.letsmoe.otter.graphql.InWatchlistQuery
import com.letsmoe.otter.graphql.RemoveFromWatchlistMutation
import com.letsmoe.otter.graphql.SetNotifyingMutation
import com.letsmoe.otter.graphql.SetRatingMutation

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

    suspend fun setNotifying(profileId: String, mediaId: String, notifying: Boolean) {
        OtterApi.client.mutation(SetNotifyingMutation(profileId, mediaId, notifying)).execute().dataOrThrow()
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
