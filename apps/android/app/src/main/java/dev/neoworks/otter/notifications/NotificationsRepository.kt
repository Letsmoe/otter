package dev.neoworks.otter.notifications

import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.graphql.MarkNotificationsReadMutation
import dev.neoworks.otter.graphql.NotificationsQuery
import kotlin.coroutines.cancellation.CancellationException

/** Per-profile notifications from the backend's watchlist watcher. Mirrors packages/otter-app/src/notifications.ts. */
object NotificationsRepository {

    /** Newest first; empty when the backend can't be reached. */
    suspend fun fetch(profileId: String, limit: Int): List<NotificationItem> {
        return try {
            val data = OtterApi.client.query(NotificationsQuery(profileId, limit)).execute().dataOrThrow()
            data.notifications.map { notification -> notification.notificationFields.toNotificationItem() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            emptyList()
        }
    }

    suspend fun markAllRead(profileId: String) {
        try {
            OtterApi.client.mutation(MarkNotificationsReadMutation(profileId)).execute()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Reading again later marks them.
        }
    }
}
