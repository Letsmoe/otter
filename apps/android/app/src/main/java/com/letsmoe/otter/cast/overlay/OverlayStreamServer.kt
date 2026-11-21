package com.letsmoe.otter.cast.overlay

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.letsmoe.otter.BuildConfig
import com.letsmoe.otter.cast.session.SessionEncoder
import com.letsmoe.otter.cast.session.SESSION_TAG
import com.letsmoe.otter.cast.session.SessionJob
import com.letsmoe.otter.cast.session.SessionScreen
import com.letsmoe.otter.cast.session.TelevisionSession
import com.letsmoe.otter.cast.session.TelevisionSessions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "OtterOverlay"
private const val OVERLAY_PATH = "/overlay/"
private const val MAX_REQUEST_LINE = 2048
private const val LOOPBACK_ADDRESS = "127.0.0.1"
// GET /session/<id>/next?at=<microseconds>: the TV session's next job, from that moment of the session.
private val sessionPathPattern = Regex("""^/session/([A-Za-z0-9-]{1,64})/next\?at=(\d{1,15})$""")
// GET /session/<id>/bridge: the session's second of spinner, for the backend to fill slow switches with.
private val bridgePathPattern = Regex("""^/session/([A-Za-z0-9-]{1,64})/bridge$""")
// GET /session/<id>/started?job=<job id>&at=<microseconds>: where the backend really put a job's first sample.
private val startedPathPattern = Regex("""^/session/([A-Za-z0-9-]{1,64})/started\?job=([A-Za-z0-9-]{1,64})&at=(\d{1,15})$""")
// Names the job a session answer streams, so the backend can say where it started.
private const val JOB_HEADER = "Otter-Job"

/** A request the server understands. */
private sealed interface LoopbackRequest

/** GET or HEAD of /overlay/<id>. */
private data class OverlayRequest(val method: String, val jobId: String) : LoopbackRequest

/** The backend's long poll for a TV session's next job. */
private data class SessionJobRequest(val sessionId: String, val atMicroseconds: Long) : LoopbackRequest

/** The backend fetching a TV session's bridge clip. */
private data class SessionBridgeRequest(val sessionId: String) : LoopbackRequest

/** The backend saying where a job's first sample went. */
private data class SessionStartedRequest(val sessionId: String, val jobId: String, val atMicroseconds: Long) : LoopbackRequest

/**
 * Serves overlay streams to the backend on loopback (OVERLAY_STREAM_URL); the
 * backend turns them into MPEG-TS for the TV. A newer request for a job stops
 * the older one: a TV that opens the stream again starts it over. TV sessions
 * get their jobs one after the other here too (see [TelevisionSession]).
 */
@UnstableApi
object OverlayStreamServer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = mutableMapOf<String, Job>()
    private lateinit var applicationContext: Context

    fun start(context: Context) {
        applicationContext = context.applicationContext
        scope.launch {
            try {
                serve()
            } catch (error: IOException) {
                Log.w(TAG, "overlay server stopped", error)
            }
        }
    }

    private suspend fun serve() {
        // IPv4 on purpose: the backend dials 127.0.0.1, and Android's default loopback is ::1.
        ServerSocket(BuildConfig.OVERLAY_PORT, 8, InetAddress.getByName(LOOPBACK_ADDRESS)).use { server ->
            while (true) {
                val socket = runInterruptible { server.accept() }
                scope.launch { answer(socket) }
            }
        }
    }

    private suspend fun answer(socket: Socket) {
        socket.use {
            when (val request = readRequest(socket.getInputStream())) {
                is OverlayRequest -> answerOverlay(socket, request)
                is SessionJobRequest -> answerSessionJob(socket, request)
                is SessionBridgeRequest -> answerBridge(socket, request)
                is SessionStartedRequest -> answerStarted(socket, request)
                null -> respond(socket.getOutputStream(), "400 Bad Request")
            }
        }
    }

    private suspend fun answerOverlay(socket: Socket, request: OverlayRequest) {
        val job = OverlayJobs.find(request.jobId)
        if (job == null) {
            respond(socket.getOutputStream(), "404 Not Found")
            return
        }
        respond(socket.getOutputStream(), "200 OK")
        if (request.method == "GET") {
            streamTakingOver(job, socket.getOutputStream())
        }
    }

    // Waits until the session has a job the backend doesn't have yet; 404 once the session is over.
    private suspend fun answerSessionJob(socket: Socket, request: SessionJobRequest) {
        val session = TelevisionSessions.find(request.sessionId)
        val job = session?.takeNext(request.atMicroseconds)
        if (session == null || job == null) {
            respond(socket.getOutputStream(), "404 Not Found")
            return
        }
        respond(socket.getOutputStream(), "200 OK", "$JOB_HEADER: ${job.id}\r\n")
        val encoderJob = currentJob() ?: return
        session.onEncoderStarted(job, encoderJob)
        streamSessionJob(session, job, socket.getOutputStream())
    }

    // Encoded on the first request and kept for the session's later streams.
    private suspend fun answerBridge(socket: Socket, request: SessionBridgeRequest) {
        val session = TelevisionSessions.find(request.sessionId)
        if (session == null) {
            respond(socket.getOutputStream(), "404 Not Found")
            return
        }
        val clip = try {
            bridgeClipOf(session)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "encoding the TV session's bridge failed", error)
            respond(socket.getOutputStream(), "500 Internal Server Error")
            return
        }
        respond(socket.getOutputStream(), "200 OK")
        socket.getOutputStream().write(clip)
        socket.getOutputStream().flush()
    }

    private suspend fun bridgeClipOf(session: TelevisionSession): ByteArray {
        val encoded = session.bridgeClip
        if (encoded != null) {
            return encoded
        }
        val job = SessionJob(SessionScreen.Bridge)
        job.startMicroseconds = 0
        val output = ByteArrayOutputStream()
        SessionEncoder(applicationContext, session, job).encode(output)
        val clip = output.toByteArray()
        session.bridgeClip = clip
        Log.i(SESSION_TAG, "bridge clip encoded, ${clip.size / 1024} KiB, ${job.millisecondsSinceShown()} ms")
        return clip
    }

    private fun answerStarted(socket: Socket, request: SessionStartedRequest) {
        TelevisionSessions.find(request.sessionId)?.onJobStarted(request.jobId, request.atMicroseconds)
        respond(socket.getOutputStream(), "204 No Content")
    }

    // A job that ends on its own is followed by the next; a backend that hung up gets it again when it asks.
    private suspend fun streamSessionJob(session: TelevisionSession, job: SessionJob, output: OutputStream) {
        val watched = WatchedOutputStream(output)
        try {
            val endMicroseconds = SessionEncoder(applicationContext, session, job).encode(watched)
            session.onCompleted(job, endMicroseconds)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (watched.failed) {
                session.onReaderGone(job)
                return
            }
            Log.w(TAG, "encoding TV session job ${job.id} failed", error)
            session.onEncoderFailed(job, error)
        }
    }

    private suspend fun streamTakingOver(job: OverlayJob, output: OutputStream) {
        val current = currentJob() ?: return
        replaceRunning(job.id, current)?.cancel()
        try {
            stream(job, output)
        } finally {
            forgetRunning(job.id, current)
        }
    }

    private suspend fun currentJob(): Job? {
        return currentCoroutineContext()[Job]
    }

    @Synchronized
    private fun replaceRunning(jobId: String, replacement: Job): Job? {
        return running.put(jobId, replacement)
    }

    @Synchronized
    private fun forgetRunning(jobId: String, finished: Job) {
        if (running[jobId] == finished) {
            running.remove(jobId)
        }
    }

    // Failures with the TV still listening make the TV get the plain stream; a TV that hung up isn't one.
    private suspend fun stream(job: OverlayJob, output: OutputStream) {
        val watched = WatchedOutputStream(output)
        try {
            OverlayEncoder(applicationContext, job).encode(watched)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (watched.failed) {
                return
            }
            Log.w(TAG, "encoding overlay job ${job.id} failed", error)
            job.onFailure(job, error)
        }
    }
}

/** Remembers whether writing failed: then the reader went away, not the encoder. */
private class WatchedOutputStream(output: OutputStream) : FilterOutputStream(output) {

    @Volatile
    var failed = false
        private set

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        try {
            out.write(buffer, offset, length)
        } catch (error: IOException) {
            failed = true
            throw error
        }
    }

    override fun write(byte: Int) {
        try {
            out.write(byte)
        } catch (error: IOException) {
            failed = true
            throw error
        }
    }
}

// Reads the request line and skips the headers; null for anything but GET/HEAD /overlay/<id> and a session's next job.
private fun readRequest(input: InputStream): LoopbackRequest? {
    val requestLine = readLine(input) ?: return null
    while (true) {
        val header = readLine(input) ?: return null
        if (header.isEmpty()) {
            break
        }
    }
    val parts = requestLine.split(" ")
    if (parts.size < 2 || parts[0] !in setOf("GET", "HEAD")) {
        return null
    }
    if (parts[1].startsWith(OVERLAY_PATH)) {
        return OverlayRequest(parts[0], parts[1].removePrefix(OVERLAY_PATH))
    }
    if (parts[0] != "GET") {
        return null
    }
    return sessionRequest(parts[1])
}

private fun sessionRequest(target: String): LoopbackRequest? {
    val jobRequest = sessionJobRequest(target)
    if (jobRequest != null) {
        return jobRequest
    }
    val bridge = bridgePathPattern.matchEntire(target)
    if (bridge != null) {
        return SessionBridgeRequest(bridge.groupValues[1])
    }
    return sessionStartedRequest(target)
}

private fun sessionJobRequest(target: String): SessionJobRequest? {
    val match = sessionPathPattern.matchEntire(target) ?: return null
    val atMicroseconds = match.groupValues[2].toLongOrNull() ?: return null
    return SessionJobRequest(match.groupValues[1], atMicroseconds)
}

private fun sessionStartedRequest(target: String): SessionStartedRequest? {
    val match = startedPathPattern.matchEntire(target) ?: return null
    val atMicroseconds = match.groupValues[3].toLongOrNull() ?: return null
    return SessionStartedRequest(match.groupValues[1], match.groupValues[2], atMicroseconds)
}

private fun readLine(input: InputStream): String? {
    val line = StringBuilder()
    while (line.length < MAX_REQUEST_LINE) {
        val next = input.read()
        if (next < 0) {
            return null
        }
        if (next == '\n'.code) {
            return line.toString().trimEnd('\r')
        }
        line.append(next.toChar())
    }
    return null
}

// No length: the stream ends when the connection closes. [headers] are extra lines, each ending in \r\n.
private fun respond(output: OutputStream, status: String, headers: String = "") {
    val head = "HTTP/1.1 $status\r\nContent-Type: video/mp4\r\n${headers}Connection: close\r\n\r\n"
    output.write(head.toByteArray())
    output.flush()
}
