package com.letsmoe.otter.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.CheckNotificationsMutation
import com.letsmoe.otter.graphql.NotificationsQuery
import com.letsmoe.otter.graphql.ProfilesQuery
import com.letsmoe.otter.graphql.fragment.ProfileFields
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "my-list-news"
private const val CHECK_INTERVAL_HOURS = 3L
private const val BACKEND_WAIT_MILLISECONDS = 60_000L
private const val FETCH_LIMIT = 30

/**
 * Every few hours, even while the app is closed: starts the backend (the process starts with
 * the work), has it check every title on My List, and posts what's new as phone notifications.
 */
class NotificationCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        if (!PhoneNotifications.allowed(applicationContext)) {
            return Result.success()
        }
        return try {
            val backendUp = withTimeoutOrNull(BACKEND_WAIT_MILLISECONDS) { OtterApi.awaitBackend() }
            if (backendUp == null) {
                return Result.retry()
            }
            OtterApi.client.mutation(CheckNotificationsMutation()).execute().dataOrThrow()
            postNewNotifications()
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.retry()
        }
    }

    private suspend fun postNewNotifications() {
        val profiles = OtterApi.client.query(ProfilesQuery()).execute().dataOrThrow().profiles.map { profile -> profile.profileFields }
        for (profile in profiles) {
            // Not NotificationsRepository.fetch: an empty list on failure would forget what was posted.
            val data = OtterApi.client.query(NotificationsQuery(profile.id, FETCH_LIMIT)).execute().dataOrThrow()
            val items = data.notifications.map { notification -> notification.notificationFields.toNotificationItem() }
            val fresh = PostedNotifications.takeUnposted(applicationContext, profile.id, items)
            for (item in fresh.reversed()) {
                PhoneNotifications.post(applicationContext, item, profileLabel(profiles, profile))
            }
        }
    }

    private fun profileLabel(profiles: List<ProfileFields>, profile: ProfileFields): String? {
        if (profiles.size < 2) {
            return null
        }
        return profile.name
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<NotificationCheckWorker>(CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
