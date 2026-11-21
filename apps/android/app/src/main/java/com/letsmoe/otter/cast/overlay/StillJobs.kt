package com.letsmoe.otter.cast.overlay

import java.io.File
import java.util.UUID

// A TV shows one still at a time; the last few stay for TVs that open a stream twice.
private const val KEPT_STILLS = 3

/**
 * A picture the TV is shown as video: [imageFile] (a JPEG of the frame) held
 * for [durationSeconds]. [onFailure] hears when encoding fails.
 */
class StillJob(
    val imageFile: File,
    val durationSeconds: Int,
    val onFailure: (Exception) -> Unit,
) {
    val id: String = UUID.randomUUID().toString()
}

/** The still pictures the loopback server can encode, by id (the same /overlay/<id> path as overlay jobs). */
object StillJobs {

    private val jobs = LinkedHashMap<String, StillJob>()

    @Synchronized
    fun register(job: StillJob): String {
        jobs[job.id] = job
        while (jobs.size > KEPT_STILLS) {
            val oldest = jobs.keys.first()
            jobs.remove(oldest)?.imageFile?.delete()
        }
        return job.id
    }

    @Synchronized
    fun find(id: String): StillJob? {
        return jobs[id]
    }
}
