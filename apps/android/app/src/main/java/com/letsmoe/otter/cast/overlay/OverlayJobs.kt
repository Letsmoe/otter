package com.letsmoe.otter.cast.overlay

import com.letsmoe.otter.ui.player.CaptionLook
import java.util.UUID

// A TV plays one stream at a time; the last few stay for TVs that open a stream twice.
private const val KEPT_JOBS = 4

/**
 * One overlay stream a TV was handed: the title's stream on loopback
 * ([sourceUrl], played from [startSeconds]), at most [maxHeight] lines tall,
 * with [plan] drawn in. The live inputs are asked while encoding, so a
 * changed setting reaches the TV without a restart. [onFailure] hears when
 * encoding fails, so the TV can get the plain stream instead.
 */
class OverlayJob(
    val sourceUrl: String,
    val sourceMimeType: String?,
    val startSeconds: Double,
    val maxHeight: Int,
    val plan: OverlayPlan,
    val live: OverlayLiveInputs = OverlayLiveInputs(),
    val brightensDarkScenes: Boolean = false,
    val onFailure: (OverlayJob, Exception) -> Unit,
) {
    val id: String = UUID.randomUUID().toString()

    fun captionLook(): CaptionLook = live.captionLook()

    fun upNextDismissed(): Boolean = live.upNextDismissed()

    fun captionDelaySeconds(): Double = live.captionDelaySeconds()

    fun voiceBoost(): Boolean = live.voiceBoost()

    fun together(): TogetherMoments = live.together()

    fun evenVolume(): Boolean = live.evenVolume()

    fun replayUntilSeconds(): Double? = live.replayUntilSeconds()

    fun stopsAfterEpisode(): Boolean = live.stopsAfterEpisode()

    /** The same title and picture from [seconds] on, as a new job. */
    fun restartedAt(seconds: Double): OverlayJob {
        return OverlayJob(sourceUrl, sourceMimeType, seconds, maxHeight, plan, live, brightensDarkScenes, onFailure)
    }
}

/**
 * What can change while a stream plays: the profile's caption look, a cancelled next-episode
 * card, the viewer's caption timing (positive: later), voice boost, even volume, the Watch
 * Together room, a replay with captions (until that second of the title) and stopping after
 * this episode.
 */
class OverlayLiveInputs(
    val captionLook: () -> CaptionLook = { CaptionLook.DEFAULT },
    val upNextDismissed: () -> Boolean = { false },
    val captionDelaySeconds: () -> Double = { 0.0 },
    val voiceBoost: () -> Boolean = { false },
    val together: () -> TogetherMoments = { TogetherMoments() },
    val evenVolume: () -> Boolean = { false },
    val replayUntilSeconds: () -> Double? = { null },
    val stopsAfterEpisode: () -> Boolean = { false },
)

/** The overlay streams the loopback server can encode, by id. */
object OverlayJobs {

    private val jobs = LinkedHashMap<String, OverlayJob>()

    @Synchronized
    fun register(job: OverlayJob): String {
        jobs[job.id] = job
        while (jobs.size > KEPT_JOBS) {
            jobs.remove(jobs.keys.first())
        }
        return job.id
    }

    @Synchronized
    fun find(id: String): OverlayJob? {
        return jobs[id]
    }
}
