package com.letsmoe.otter.ui.watchorder

import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.FranchisesQuery
import com.letsmoe.otter.graphql.WatchOrderQuery
import kotlin.coroutines.cancellation.CancellationException

private const val FRANCHISE_ROW_SIZE = 12

/** Franchise guides from the backend. */
object WatchOrderRepository {

    /** One franchise's guide; throws when the backend can't load it. */
    suspend fun guide(collectionId: String, profileId: String): WatchOrderGuide {
        val data = OtterApi.client.query(WatchOrderQuery(collectionId, profileId)).execute().dataOrThrow()
        val collection = data.collection ?: throw IllegalStateException("This franchise is not available")
        return collection.collectionGuide.toGuide()
    }

    /** The franchises the library knows, those the profile is in the middle of first; empty when they can't be loaded. */
    suspend fun franchises(profileId: String, limit: Int = FRANCHISE_ROW_SIZE): List<WatchOrderGuide> {
        try {
            val data = OtterApi.client.query(FranchisesQuery(profileId, limit)).execute().dataOrThrow()
            return data.franchises.map { franchise -> franchise.collectionGuide.toGuide() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return emptyList()
        }
    }
}
