package dev.neoworks.otter.ui.player

import dev.neoworks.otter.together.PlaybackPayload
import dev.neoworks.otter.together.RemotePlayback
import dev.neoworks.otter.together.TogetherSession
import dev.neoworks.otter.together.catchUpRate
import dev.neoworks.otter.together.expectedPosition
import dev.neoworks.otter.together.needsSeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Watch Together: the host's position goes out this often.
private const val HEARTBEAT_MILLISECONDS = 1000L
// After a sync jump, heartbeats wait this long so the stream can settle.
private const val SEEK_SETTLE_MILLISECONDS = 2500L
// A catch-up rate ends on its own when heartbeats stop arriving.
private const val CATCH_UP_TIMEOUT_MILLISECONDS = 2500L
// After pressing a control, heartbeats that crossed it on the way are ignored.
private const val LOCAL_ACTION_GRACE_MILLISECONDS = 2000L
// Buffering shorter than this doesn't pause the others.
private const val STALL_REPORT_MILLISECONDS = 800L

/** What the player lets Watch Together read and drive. */
interface TogetherPlayerHost {
    val currentMediaId: String
    val positionSeconds: Double
    val durationSeconds: Double
    val isBuffering: Boolean
    val isHolding: Boolean
    fun seekToSeconds(seconds: Double)
    fun setCatchUpFactor(factor: Float)
    fun followPlaying(shouldPlay: Boolean)
}

/** Keeps the player in step with the room: reports its state, follows the others' play, pause and seeks. */
class PlayerTogetherSync(private val scope: CoroutineScope, private val host: TogetherPlayerHost) {

    private var localActionUntil = 0L
    private var settlingUntil = 0L
    private var catchUpJob: Job? = null
    private var stateReportJob: Job? = null
    private var wasHolding = false

    fun start() {
        scope.launch { sendHeartbeats() }
        scope.launch { followRemotePlayback() }
    }

    fun stop() {
        catchUpJob?.cancel()
        stateReportJob?.cancel()
    }

    /** The user pressed play, pause or seeked: the others follow, and their echoes are ignored for a moment. */
    fun announceLocalAction() {
        localActionUntil = System.currentTimeMillis() + LOCAL_ACTION_GRACE_MILLISECONDS
        TogetherSession.announcePlayback()
    }

    /** Tells the others whether this player is ready; brief stalls aren't reported. */
    fun reportLoading(loading: Boolean) {
        stateReportJob?.cancel()
        val mediaId = host.currentMediaId
        if (!loading) {
            TogetherSession.announcePlayerState(mediaId, false)
            return
        }
        stateReportJob = scope.launch {
            delay(STALL_REPORT_MILLISECONDS)
            TogetherSession.announcePlayerState(mediaId, true)
        }
    }

    /** Playback holds while someone is still loading; once everyone is ready the host sends its position. */
    fun onHoldingChanged(isHolding: Boolean) {
        val room = TogetherSession.room.value
        if (wasHolding && !isHolding && room?.hosting == true) {
            TogetherSession.announcePlayback()
        }
        wasHolding = isHolding
    }

    private suspend fun sendHeartbeats() {
        while (true) {
            delay(HEARTBEAT_MILLISECONDS)
            val room = TogetherSession.room.value
            if (room?.hosting == true && host.durationSeconds > 0 && !host.isHolding) {
                TogetherSession.announcePlayback(heartbeat = true)
            }
        }
    }

    private suspend fun followRemotePlayback() {
        TogetherSession.remotePlayback.collect { remote ->
            if (remote != null) {
                onRemotePlayback(remote)
            }
        }
    }

    // Follows the others' play, pause and seeks; waits for the metadata first.
    private fun onRemotePlayback(remote: RemotePlayback) {
        val payload = remote.payload
        if (host.durationSeconds <= 0 || payload.mediaId != host.currentMediaId) {
            return
        }
        if (payload.heartbeat && System.currentTimeMillis() < localActionUntil) {
            return
        }
        syncPosition(remoteTarget(remote), payload.heartbeat)
        host.followPlaying(payload.playing)
    }

    // Nobody advances while holding, so the sent position is still current.
    private fun remoteTarget(remote: RemotePlayback): Double {
        if (host.isHolding) {
            return remote.payload.position
        }
        return expectedPosition(remote.payload, remote.receivedAtMilliseconds, System.currentTimeMillis())
    }

    // Jumps past 1s of drift; closes smaller drift by playing a bit faster or slower.
    private fun syncPosition(target: Double, heartbeat: Boolean) {
        val now = System.currentTimeMillis()
        if (heartbeat && (host.isBuffering || now < settlingUntil)) {
            return
        }
        if (needsSeek(host.positionSeconds, target)) {
            host.seekToSeconds(target)
            settlingUntil = now + SEEK_SETTLE_MILLISECONDS
            setCatchUp(1f)
            return
        }
        setCatchUp(catchUpRate(host.positionSeconds, target))
    }

    private fun setCatchUp(factor: Float) {
        host.setCatchUpFactor(factor)
        catchUpJob?.cancel()
        if (factor != 1f) {
            catchUpJob = scope.launch {
                delay(CATCH_UP_TIMEOUT_MILLISECONDS)
                host.setCatchUpFactor(1f)
            }
        }
    }
}
