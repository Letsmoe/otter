package com.letsmoe.otter.cast.session

import android.os.SystemClock
import com.letsmoe.otter.cast.overlay.LiveFrames
import com.letsmoe.otter.cast.overlay.OverlayJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

// The encoder runs this far ahead of the TV's picture: what the TV has buffered, and how long a switch takes to show.
const val SESSION_LEAD_MICROSECONDS = 2_500_000L

// The TV plays a sample this long after the encoder took it: it is written a lead ahead of the clock and shown a lead behind.
const val SESSION_SOUND_LEAD_MILLISECONDS = 2 * SESSION_LEAD_MICROSECONDS / 1000

/** What one job of a TV session shows. */
sealed interface SessionScreen {
    /** Otter's "Connected" screen, while nothing plays. */
    data object Idle : SessionScreen

    /** A spinner while a link resolves, with the title when it is known. */
    data class Loading(val title: String?) : SessionScreen

    /** The title itself, with captions and hints drawn in. */
    data class Title(val overlay: OverlayJob) : SessionScreen

    /** A picture the phone drew, e.g. the title being browsed ([imageFile], a JPEG of the whole frame). */
    data class Picture(val imageFile: File) : SessionScreen

    /** Backdrops that change while the TV watches, painted frame by frame by [frames]. */
    data class Screensaver(val frames: LiveFrames) : SessionScreen

    /** "Still watching?" while the next episode waits for an answer on the phone. */
    data object StillWatching : SessionScreen
}

/**
 * What a session shows while no title or loading screen runs: the question "Still watching?"
 * while it waits for an answer, else the screen [chosen] for it (the idle screen, the title being
 * browsed or the screensaver).
 */
fun idleScreenOf(askingStillWatching: Boolean, chosen: SessionScreen): SessionScreen {
    if (askingStillWatching) {
        return SessionScreen.StillWatching
    }
    return chosen
}

/**
 * One job of a TV session: [screen], encoded from [startMicroseconds] of the
 * session's timeline on (set when the backend takes the job). [endMicroseconds]
 * is set when the job ran to its end on its own.
 */
class SessionJob(val screen: SessionScreen) {
    val id: String = UUID.randomUUID().toString()

    @Volatile
    var startMicroseconds: Long? = null

    @Volatile
    var endMicroseconds: Long? = null
}

/**
 * The one stream a DLNA TV plays while it is connected (backend:
 * /api/stream/tv?session=<id>). It shows one job at a time; [show] replaces
 * the job, which stops the current job's encoder, and the backend's next
 * request gets the new one. A job that ends on its own is followed by another,
 * so the stream never runs dry: a title by [idleScreen] (asked at that moment:
 * the idle screen, the title being browsed or the screensaver), every other
 * screen by itself. [pictureHeight] is the height every job is encoded at.
 * [onFailure] hears when the phone can't encode a job.
 */
class TelevisionSession(
    val pictureHeight: Int,
    private val onFailure: (SessionJob, Exception) -> Unit,
    private val clock: SessionClock = SessionClock { SystemClock.elapsedRealtimeNanos() / 1000 },
    private val idleScreen: () -> SessionScreen = { SessionScreen.Idle },
) {
    val id: String = UUID.randomUUID().toString()

    // Counts every change; waiting backend requests look again when it moves.
    private val changes = MutableStateFlow(0L)
    private var current = SessionJob(idleScreen())
    private var handedOut: SessionJob? = null
    private var encoder: Job? = null
    private var closed = false
    private var started = false
    private var playing = true

    val currentJob: SessionJob
        @Synchronized get() = current

    val isClosed: Boolean
        @Synchronized get() = closed

    /** Shows [screen] from now on and returns its job. */
    fun show(screen: SessionScreen): SessionJob {
        val job = SessionJob(screen)
        replace(job)
        return job
    }

    /** Whether the TV plays; the clock, and with it the encoder, stand still while it is paused. */
    @Synchronized
    fun setPlaying(isPlaying: Boolean) {
        playing = isPlaying
        updateClock()
    }

    fun clockMicroseconds(): Long {
        return clock.nowMicroseconds()
    }

    /** The moment of the session the TV shows now. */
    fun shownMicroseconds(): Long {
        return SessionTimeline.shownMicroseconds(clock.nowMicroseconds(), SESSION_LEAD_MICROSECONDS)
    }

    /** How long a sample at [sessionMicroseconds] waits before the encoder may write it. */
    fun pacingWaitMicroseconds(sessionMicroseconds: Long): Long {
        return SessionTimeline.pacingWaitMicroseconds(sessionMicroseconds, clock.nowMicroseconds(), SESSION_LEAD_MICROSECONDS)
    }

    /**
     * The job the backend streams next, starting at [atMicroseconds] of the session; waits until
     * there is one the backend doesn't have yet. Null once the session is over.
     */
    suspend fun takeNext(atMicroseconds: Long): SessionJob? {
        while (true) {
            val seen = changes.value
            if (isClosed) {
                return null
            }
            val job = tryTake(atMicroseconds)
            if (job != null) {
                return job
            }
            changes.first { change -> change != seen }
        }
    }

    @Synchronized
    private fun tryTake(atMicroseconds: Long): SessionJob? {
        // A second request while a job streams: the TV opened the stream again, so it starts over.
        if (handedOut === current && encoder?.isActive == true) {
            restartCurrent()
        }
        if (handedOut === current) {
            return null
        }
        current.startMicroseconds = atMicroseconds
        handedOut = current
        started = true
        updateClock()
        return current
    }

    /** [encoderJob] encodes [job]; it is stopped when the job is replaced. */
    @Synchronized
    fun onEncoderStarted(job: SessionJob, encoderJob: Job) {
        if (job !== current || closed) {
            encoderJob.cancel()
            return
        }
        encoder = encoderJob
    }

    /** [job] ran to its end, its samples up to [endMicroseconds] of the session. */
    @Synchronized
    fun onCompleted(job: SessionJob, endMicroseconds: Long) {
        job.endMicroseconds = endMicroseconds
        if (job !== current) {
            return
        }
        encoder = null
        current = SessionJob(screenAfter(job.screen))
        notifyChange()
    }

    /** The backend stopped reading [job]: whoever asks next gets it again from where the TV is. */
    @Synchronized
    fun onReaderGone(job: SessionJob) {
        if (job !== current) {
            return
        }
        encoder = null
        restartCurrent()
    }

    /** Encoding [job] failed while the backend still read it. */
    fun onEncoderFailed(job: SessionJob, error: Exception) {
        if (currentJob !== job) {
            return
        }
        onFailure(job, error)
    }

    /** Ends the session: the backend's stream to the TV ends too. */
    fun close() {
        synchronized(this) {
            closed = true
            encoder?.cancel()
            encoder = null
            clock.pause()
            notifyChange()
        }
        TelevisionSessions.remove(this)
    }

    private fun replace(job: SessionJob) {
        synchronized(this) {
            if (closed) {
                return
            }
            current = job
            encoder?.cancel()
            encoder = null
            notifyChange()
        }
    }

    // The same screen once more; a title from the second the TV shows.
    private fun restartCurrent() {
        val previous = current
        encoder?.cancel()
        encoder = null
        current = SessionJob(restartedScreen(previous))
        notifyChange()
    }

    private fun restartedScreen(job: SessionJob): SessionScreen {
        val screen = job.screen
        if (screen !is SessionScreen.Title) {
            return screen
        }
        val seconds = SessionTimeline.titleSecondsShown(screen.overlay.startSeconds, job.startMicroseconds, shownMicroseconds())
        return SessionScreen.Title(screen.overlay.restartedAt(seconds))
    }

    private fun screenAfter(screen: SessionScreen): SessionScreen {
        if (screen is SessionScreen.Title) {
            return idleScreen()
        }
        return screen
    }

    private fun updateClock() {
        if (started && playing && !closed) {
            clock.resume()
            return
        }
        clock.pause()
    }

    private fun notifyChange() {
        changes.value = changes.value + 1
    }
}

/** The sessions the loopback server hands jobs out for, by id. */
object TelevisionSessions {

    private val sessions = HashMap<String, TelevisionSession>()

    @Synchronized
    fun register(session: TelevisionSession) {
        sessions[session.id] = session
    }

    @Synchronized
    fun find(id: String): TelevisionSession? {
        return sessions[id]
    }

    @Synchronized
    fun remove(session: TelevisionSession) {
        if (sessions[session.id] === session) {
            sessions.remove(session.id)
        }
    }
}
