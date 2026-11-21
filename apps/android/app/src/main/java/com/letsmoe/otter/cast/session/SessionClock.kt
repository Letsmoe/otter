package com.letsmoe.otter.cast.session

import kotlin.math.max

/**
 * A TV session's clock in microseconds of the session's timeline: it runs
 * while the TV plays and stands still while it is paused. [elapsedMicroseconds]
 * is the device's monotonic time.
 */
class SessionClock(private val elapsedMicroseconds: () -> Long) {

    private var accumulatedMicroseconds = 0L
    private var runningSinceMicroseconds: Long? = null

    val isRunning: Boolean
        @Synchronized get() = runningSinceMicroseconds != null

    @Synchronized
    fun resume() {
        if (runningSinceMicroseconds == null) {
            runningSinceMicroseconds = elapsedMicroseconds()
        }
    }

    @Synchronized
    fun pause() {
        val since = runningSinceMicroseconds ?: return
        accumulatedMicroseconds += elapsedMicroseconds() - since
        runningSinceMicroseconds = null
    }

    @Synchronized
    fun nowMicroseconds(): Long {
        val since = runningSinceMicroseconds ?: return accumulatedMicroseconds
        return accumulatedMicroseconds + elapsedMicroseconds() - since
    }
}

/**
 * How the phone's encoder, the session's clock and the TV's picture relate.
 * The encoder may run [the lead][leadMicroseconds] ahead of the clock; the TV
 * shows the moment the lead behind it.
 */
object SessionTimeline {

    /** How long a sample at [sampleMicroseconds] of the session waits before it is written; 0 when it may go now. */
    fun pacingWaitMicroseconds(sampleMicroseconds: Long, clockMicroseconds: Long, leadMicroseconds: Long): Long {
        return max(0L, sampleMicroseconds - (clockMicroseconds + leadMicroseconds))
    }

    /** The moment of the session the TV shows. */
    fun shownMicroseconds(clockMicroseconds: Long, leadMicroseconds: Long): Long {
        return max(0L, clockMicroseconds - leadMicroseconds)
    }

    /** Whether the TV shows a job that starts at [jobStartMicroseconds] yet; not before the job was handed out (null). */
    fun hasReached(jobStartMicroseconds: Long?, shownMicroseconds: Long): Boolean {
        if (jobStartMicroseconds == null) {
            return false
        }
        return shownMicroseconds >= jobStartMicroseconds
    }

    /** The title's second the TV shows, for a title job that plays from [titleStartSeconds]; that second until it shows. */
    fun titleSecondsShown(titleStartSeconds: Double, jobStartMicroseconds: Long?, shownMicroseconds: Long): Double {
        if (jobStartMicroseconds == null || !hasReached(jobStartMicroseconds, shownMicroseconds)) {
            return titleStartSeconds
        }
        return titleStartSeconds + (shownMicroseconds - jobStartMicroseconds) / 1_000_000.0
    }
}
