package com.letsmoe.otter.downloads

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.profiles.ProfileSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

private const val WHILE_CHARGING_WORK = "smart-downloads-charging"
private const val WHILE_IDLE_WORK = "smart-downloads-idle"
private const val RUN_INTERVAL_HOURS = 6L
private const val BACKEND_WAIT_MILLISECONDS = 60_000L

/** One run of Smart Downloads, started by WorkManager on an unmetered network (see [SmartDownloadScheduler]). */
class SmartDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val profileId = ProfileSession.activeProfile.value?.id
        if (!SmartDownloadStore.enabled || profileId == null) {
            return Result.success()
        }
        return try {
            val backendUp = withTimeoutOrNull(BACKEND_WAIT_MILLISECONDS) { OtterApi.awaitBackend() }
            if (backendUp == null) {
                return Result.retry()
            }
            SmartDownloadRunner.run(profileId)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.retry()
        }
    }
}

/**
 * Smart Downloads run on Wi-Fi (an unmetered network) while the phone charges, or while it is idle.
 * WorkManager ANDs constraints, so each of the two conditions is its own schedule; the runner
 * handles both firing together.
 */
object SmartDownloadScheduler {

    fun apply(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (!SmartDownloadStore.enabled) {
            workManager.cancelUniqueWork(WHILE_CHARGING_WORK)
            workManager.cancelUniqueWork(WHILE_IDLE_WORK)
            return
        }
        schedule(workManager, WHILE_CHARGING_WORK, unmeteredConstraints().setRequiresCharging(true).build())
        schedule(workManager, WHILE_IDLE_WORK, unmeteredConstraints().setRequiresDeviceIdle(true).setRequiresBatteryNotLow(true).build())
    }

    private fun unmeteredConstraints(): Constraints.Builder {
        return Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)
    }

    private fun schedule(workManager: WorkManager, name: String, constraints: Constraints) {
        val request = PeriodicWorkRequestBuilder<SmartDownloadWorker>(RUN_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
