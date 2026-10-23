package dev.neoworks.otter.ui.player

import dev.neoworks.otter.AppScope
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.graphql.PlayerRecordProgressMutation
import dev.neoworks.otter.profiles.ProfileSession
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

/** Progress is saved at most this often while playing, plus on pause and close. */
private const val SAVE_INTERVAL_SECONDS = 15.0

/** Records playback progress for resume and recommendations. Best-effort. */
class ProgressReporter {

    private var lastSavedAtSeconds = 0.0

    fun reset() {
        lastSavedAtSeconds = 0.0
    }

    fun save(mediaId: String, positionSeconds: Double, durationSeconds: Double, force: Boolean) {
        val profileId = ProfileSession.activeProfile.value?.id ?: return
        // Progress needs a known duration (live streams have none).
        if (durationSeconds <= 0 || durationSeconds.isInfinite()) {
            return
        }
        if (!force && abs(positionSeconds - lastSavedAtSeconds) < SAVE_INTERVAL_SECONDS) {
            return
        }
        lastSavedAtSeconds = positionSeconds
        val mutation = PlayerRecordProgressMutation(profileId, mediaId, floor(positionSeconds), floor(durationSeconds))
        AppScope.launch {
            try {
                OtterApi.client.mutation(mutation).execute()
            } catch (error: Exception) {
                // Progress tracking must never interrupt playback.
            }
        }
    }
}
