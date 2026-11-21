package com.letsmoe.otter.cast

import android.util.Log
import android.os.SystemClock
import com.letsmoe.otter.settings.NightVolume
import com.letsmoe.otter.settings.PlaybackSettings
import com.letsmoe.otter.settings.TELEVISION_VOLUME_RANGE
import com.letsmoe.otter.toasts.Toasts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "OtterNightVolume"
private const val CHECK_MILLISECONDS = 5000L
// A viewer who keeps turning the TV up on its remote gets told once a minute, not every check.
private const val NOTICE_MILLISECONDS = 60_000L

/**
 * Brings a TV that got louder than the night's limit back to it, whoever turned it up (the phone's
 * remote or the TV's own). It only acts while the limit applies.
 */
class NightVolumeGuard(
    private val scope: CoroutineScope,
    private val readVolume: suspend () -> Int?,
    private val setVolume: suspend (Int) -> Unit,
) {
    private var job: Job? = null
    private var lastNoticeAtMilliseconds: Long? = null

    fun start() {
        stop()
        job = scope.launch {
            while (true) {
                lowerToLimit()
                delay(CHECK_MILLISECONDS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun lowerToLimit() {
        val maximum = PlaybackSettings.televisionVolumeMaximum
        if (maximum >= TELEVISION_VOLUME_RANGE) {
            return
        }
        val volume = readVolume() ?: return
        if (!NightVolume.exceeds(volume, maximum)) {
            return
        }
        try {
            setVolume(maximum)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "lowering the TV's volume failed", error)
            return
        }
        announce(maximum)
    }

    private fun announce(maximum: Int) {
        val now = SystemClock.elapsedRealtime()
        val lastNotice = lastNoticeAtMilliseconds
        if (lastNotice != null && now - lastNotice < NOTICE_MILLISECONDS) {
            return
        }
        lastNoticeAtMilliseconds = now
        Toasts.push("It's bedtime: the TV's volume is limited to $maximum")
    }
}
