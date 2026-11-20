package com.letsmoe.otter.cast.overlay

import java.util.UUID

// A TV plays one stream at a time; the last few stay for TVs that open a stream twice.
private const val KEPT_JOBS = 4

/**
 * One overlay stream a TV was handed: the title's stream on loopback
 * ([sourceUrl], played from [startSeconds]), at most [maxHeight] lines tall,
 * with [plan] drawn in. [onFailure] hears when encoding fails, so the TV can
 * get the plain stream instead.
 */
class OverlayJob(
    val sourceUrl: String,
    val sourceMimeType: String?,
    val startSeconds: Double,
    val maxHeight: Int,
    val plan: OverlayPlan,
    val onFailure: (OverlayJob, Exception) -> Unit,
) {
    val id: String = UUID.randomUUID().toString()
}

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
