package com.letsmoe.otter.cast.overlay

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.letsmoe.otter.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
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

/** A request line the server understands: GET or HEAD of /overlay/<id>. */
private data class OverlayRequest(val method: String, val jobId: String)

/**
 * Serves overlay streams to the backend on loopback (OVERLAY_STREAM_URL); the
 * backend turns them into MPEG-TS for the TV. A newer request for a job stops
 * the older one: a TV that opens the stream again starts it over.
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
            val request = readRequest(socket.getInputStream())
            if (request == null) {
                respond(socket.getOutputStream(), "400 Bad Request")
                return
            }
            val job = OverlayJobs.find(request.jobId)
            val still = StillJobs.find(request.jobId)
            if (job == null && still == null) {
                respond(socket.getOutputStream(), "404 Not Found")
                return
            }
            respond(socket.getOutputStream(), "200 OK")
            if (request.method != "GET") {
                return
            }
            if (job != null) {
                streamTakingOver(job.id) { stream(job, socket.getOutputStream()) }
                return
            }
            if (still != null) {
                streamTakingOver(still.id) { streamStill(still, socket.getOutputStream()) }
            }
        }
    }

    private suspend fun streamTakingOver(jobId: String, streaming: suspend () -> Unit) {
        val current = currentJob() ?: return
        replaceRunning(jobId, current)?.cancel()
        try {
            streaming()
        } finally {
            forgetRunning(jobId, current)
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

    private suspend fun streamStill(still: StillJob, output: OutputStream) {
        val watched = WatchedOutputStream(output)
        try {
            StillEncoder(applicationContext, still).encode(watched)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (watched.failed) {
                return
            }
            Log.w(TAG, "encoding still ${still.id} failed", error)
            still.onFailure(error)
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

// Reads the request line and skips the headers; null for anything but GET/HEAD /overlay/<id>.
private fun readRequest(input: InputStream): OverlayRequest? {
    val requestLine = readLine(input) ?: return null
    while (true) {
        val header = readLine(input) ?: return null
        if (header.isEmpty()) {
            break
        }
    }
    val parts = requestLine.split(" ")
    if (parts.size < 2 || parts[0] !in setOf("GET", "HEAD") || !parts[1].startsWith(OVERLAY_PATH)) {
        return null
    }
    return OverlayRequest(parts[0], parts[1].removePrefix(OVERLAY_PATH))
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

// No length: the stream ends when the connection closes.
private fun respond(output: OutputStream, status: String) {
    val head = "HTTP/1.1 $status\r\nContent-Type: video/mp4\r\nConnection: close\r\n\r\n"
    output.write(head.toByteArray())
    output.flush()
}
