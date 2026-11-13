package com.letsmoe.otter.cast

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.letsmoe.otter.backend.EmbeddedBackend
import com.letsmoe.otter.reporting.ErrorReporting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "OtterDlna"
private const val POLL_MILLISECONDS = 1000L
private const val START_TIMEOUT_MILLISECONDS = 30_000L
// Samsungs drop a Play that arrives while they still load the URI; it is repeated this often until they start.
private const val START_RETRY_MILLISECONDS = 3000L
// The TV reports the old state for a moment after a play or pause.
private const val COMMAND_SETTLE_MILLISECONDS = 3000L
// STOPPED this long with the media's duration known means the TV read the video and refused it
// (a Philips shows "resolution not supported"); a playable one runs within a second or two.
private const val REFUSAL_MILLISECONDS = 10_000L
private const val MAX_POLL_FAILURES = 5
// A stop this close to the end counts as the end.
private const val END_TOLERANCE_SECONDS = 15.0

private val dlnaCommands = Player.Commands.Builder().addAll(
    Player.COMMAND_PLAY_PAUSE,
    Player.COMMAND_PREPARE,
    Player.COMMAND_STOP,
    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
    Player.COMMAND_SEEK_BACK,
    Player.COMMAND_SEEK_FORWARD,
    Player.COMMAND_SET_MEDIA_ITEM,
    Player.COMMAND_CHANGE_MEDIA_ITEMS,
    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
    Player.COMMAND_GET_TIMELINE,
    Player.COMMAND_GET_METADATA,
    Player.COMMAND_RELEASE,
).build()

/** The caption file the TV shows: [srtPath] is the backend path of its SRT. */
data class TelevisionSubtitleFile(val id: String, val srtPath: String)

/** What the backend says about a continuous stream before the TV gets it; [height] is null when unknown. */
private data class ContinuousProbe(val startSeconds: Double, val durationSeconds: Double, val mimeType: String, val height: Int?)

/**
 * Plays on a DLNA TV (e.g. an older Samsung) as a Media3 player, so the player
 * screen, progress saving and Watch Together drive the TV like the phone.
 * HLS goes to the TV as one continuous stream; seeking in it restarts the
 * stream at the new position. Files are seeked by the TV itself.
 */
class DlnaPlayer(private val context: Context, val renderer: DlnaRenderer) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val client = DlnaClient(renderer)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val heights = TelevisionHeights(context)

    /** The height of the link set next (from its quality, e.g. 1080 for "1080p"); null when unknown. */
    var linkHeight: Int? = null
    // The height the TV gets now: the continuous stream's variant, else the link's.
    private var streamHeight: Int? = null
    private var refusingSinceMilliseconds: Long? = null

    private var mediaItem: MediaItem? = null
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE
    private var playerError: PlaybackException? = null
    private var subtitleFile: TelevisionSubtitleFile? = null
    // Asked once per player; null until then.
    private var acceptedFormats: List<TelevisionFormat>? = null
    // What the TV was last told to play, for error reports.
    private var lastMedia: TelevisionMedia? = null

    private var isContinuous = false
    // Where the TV's 0:00 lies on the title's timeline (continuous streams).
    private var streamOffsetSeconds = 0.0
    private var durationSeconds = 0.0
    private var reportedSeconds = 0.0
    private var reportedAtMilliseconds = 0L
    private var televisionPlaying = false
    private var televisionStopped = false
    private var hasPlayed = false
    private var pendingFileSeekSeconds: Double? = null
    private var loadedAtMilliseconds = 0L
    private var lastCommandAtMilliseconds = 0L
    private var pollFailures = 0

    private var loadJob: Job? = null
    private var pollJob: Job? = null

    // State --------------------------------------------------------------------

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(dlnaCommands)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlayerError(playerError)
        val item = mediaItem ?: return builder.setPlaybackState(Player.STATE_IDLE).build()
        var state = playbackState
        if (playerError != null) {
            state = Player.STATE_IDLE
        }
        return builder
            .setPlaylist(listOf(itemData(item)))
            .setCurrentMediaItemIndex(0)
            .setPlaybackState(state)
            .setContentPositionMs { currentPositionMilliseconds() }
            .build()
    }

    private fun itemData(item: MediaItem): MediaItemData {
        var durationUs = C.TIME_UNSET
        if (durationSeconds > 0) {
            durationUs = (durationSeconds * 1_000_000).toLong()
        }
        return MediaItemData.Builder("dlna").setMediaItem(item).setDurationUs(durationUs).setIsSeekable(true).build()
    }

    private fun currentPositionMilliseconds(): Long {
        var seconds = reportedSeconds
        if (televisionPlaying) {
            seconds += (SystemClock.elapsedRealtime() - reportedAtMilliseconds) / 1000.0
        }
        if (durationSeconds > 0) {
            seconds = seconds.coerceAtMost(durationSeconds)
        }
        return (seconds * 1000).toLong()
    }

    private fun report(seconds: Double) {
        reportedSeconds = seconds
        reportedAtMilliseconds = SystemClock.elapsedRealtime()
    }

    /** Shows another caption file (or none) from the current position on; the TV loads the stream again. */
    fun showSubtitle(file: TelevisionSubtitleFile?) {
        if (file == subtitleFile) {
            return
        }
        subtitleFile = file
        if (mediaItem == null || playbackState == Player.STATE_IDLE) {
            return
        }
        startLoad(currentPositionMilliseconds() / 1000.0)
    }

    /** Whether this TV is expected to play a video [height] pixels tall; unknown heights are. */
    fun fits(height: Int?): Boolean {
        return heights.fits(renderer.id, height)
    }

    // Volume -------------------------------------------------------------------

    val supportsVolume: Boolean
        get() = renderer.volumeControl != null

    /** The TV's volume, 0 to 100; null when it can't be read. */
    suspend fun readVolume(): Int? {
        return try {
            client.volume()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "reading the TV's volume failed", error)
            null
        }
    }

    fun changeVolume(volume: Int) {
        sendCommand { client.setVolume(volume) }
    }

    // Commands -----------------------------------------------------------------

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        stopWork()
        var index = startIndex
        if (index == C.INDEX_UNSET) {
            index = 0
        }
        mediaItem = mediaItems.getOrNull(index)
        playbackState = Player.STATE_IDLE
        playerError = null
        durationSeconds = 0.0
        var startSeconds = 0.0
        if (startPositionMs != C.TIME_UNSET) {
            startSeconds = startPositionMs / 1000.0
        }
        televisionPlaying = false
        report(startSeconds)
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (mediaItem != null) {
            startLoad(reportedSeconds)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        if (playbackState == Player.STATE_IDLE || loadJob?.isActive == true) {
            // A load in progress applies playWhenReady when it finishes.
            return Futures.immediateVoidFuture()
        }
        // The TV stopped on its own (its remote): play means start again where it was.
        if (playWhenReady && televisionStopped && playbackState != Player.STATE_ENDED) {
            startLoad(currentPositionMilliseconds() / 1000.0)
            return Futures.immediateVoidFuture()
        }
        lastCommandAtMilliseconds = SystemClock.elapsedRealtime()
        // Freeze the extrapolated position while pausing.
        report(currentPositionMilliseconds() / 1000.0)
        televisionPlaying = playWhenReady && playbackState == Player.STATE_READY
        sendCommand { if (playWhenReady) client.play() else client.pause() }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        var targetSeconds = 0.0
        if (positionMs != C.TIME_UNSET) {
            targetSeconds = positionMs / 1000.0
        }
        report(targetSeconds)
        if (playbackState == Player.STATE_IDLE) {
            return Futures.immediateVoidFuture()
        }
        if (isContinuous) {
            startLoad(targetSeconds)
            return Futures.immediateVoidFuture()
        }
        if (playbackState == Player.STATE_ENDED) {
            playbackState = Player.STATE_BUFFERING
            startLoad(targetSeconds)
            return Futures.immediateVoidFuture()
        }
        lastCommandAtMilliseconds = SystemClock.elapsedRealtime()
        sendCommand { client.seek(targetSeconds) }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        stopWork()
        playbackState = Player.STATE_IDLE
        televisionPlaying = false
        sendCommand { client.stop() }
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        stopWork()
        mediaItem = null
        playbackState = Player.STATE_IDLE
        televisionPlaying = false
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        stopWork()
        scope.cancel()
        // Outlives this player: the TV should stop even after the screen is gone.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.stop()
            } catch (error: Exception) {
                Log.w(TAG, "stopping the TV failed", error)
            }
        }
        return Futures.immediateVoidFuture()
    }

    private fun sendCommand(command: suspend () -> Unit) {
        scope.launch {
            try {
                command()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.w(TAG, "TV command failed", error)
            }
        }
    }

    private fun stopWork() {
        loadJob?.cancel()
        pollJob?.cancel()
        loadJob = null
        pollJob = null
    }

    // Loading ------------------------------------------------------------------

    private fun startLoad(startSeconds: Double) {
        stopWork()
        playbackState = Player.STATE_BUFFERING
        playerError = null
        televisionPlaying = false
        televisionStopped = false
        hasPlayed = false
        refusingSinceMilliseconds = null
        invalidateState()
        loadJob = scope.launch {
            try {
                load(startSeconds)
                startPolling()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                fail(error)
            }
        }
    }

    private suspend fun load(startSeconds: Double) {
        val item = mediaItem ?: return
        val origin = lanStreamOrigin(context) ?: throw DlnaException("The phone is not on Wi-Fi.")
        val media = televisionMedia(item, origin, startSeconds)
        try {
            client.stop()
        } catch (error: Exception) {
            // Nothing was playing.
        }
        client.load(media)
        if (playWhenReady) {
            client.play()
        }
        loadedAtMilliseconds = SystemClock.elapsedRealtime()
        lastCommandAtMilliseconds = loadedAtMilliseconds
    }

    private suspend fun televisionMedia(item: MediaItem, origin: String, startSeconds: Double): TelevisionMedia {
        val playerUrl = item.localConfiguration?.uri?.toString().orEmpty()
        val title = item.mediaMetadata.title?.toString().orEmpty()
        isContinuous = item.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8
        if (isContinuous) {
            val path = TelevisionUrls.continuousPath(playerUrl, startSeconds, heights.refusedHeight(renderer.id))
                ?: throw DlnaException("This source can't be played on a TV.")
            val probe = probeContinuous(EmbeddedBackend.BASE_URL + path)
            streamHeight = probe.height ?: linkHeight
            streamOffsetSeconds = probe.startSeconds
            durationSeconds = probe.durationSeconds
            report(probe.startSeconds)
            pendingFileSeekSeconds = null
            return televisionMediaFor(origin, path, title, probe.mimeType, seekableBytes = false)
        }
        val path = TelevisionUrls.filePath(playerUrl) ?: throw DlnaException("This source can't be played on a TV.")
        streamHeight = linkHeight
        streamOffsetSeconds = 0.0
        report(startSeconds)
        pendingFileSeekSeconds = startSeconds.takeIf { seconds -> seconds >= 1 }
        var mimeType = item.localConfiguration?.mimeType
        if (mimeType == null) {
            mimeType = MimeTypes.VIDEO_MP4
        }
        return televisionMediaFor(origin, path, title, mimeType, seekableBytes = true)
    }

    // The type and profile the TV is told about, and the stream URL that sends the same.
    private suspend fun televisionMediaFor(origin: String, path: String, title: String, mimeType: String, seekableBytes: Boolean): TelevisionMedia {
        val format = announcedFormat(mimeType, televisionFormats())
        val formattedPath = TelevisionUrls.withFormat(path, mimeType, format)
        val url = origin + TelevisionUrls.withSubtitle(formattedPath, subtitleFile?.id)
        val media = TelevisionMedia(url, title, format.mimeType, seekableBytes, televisionSubtitle(origin), format.profile)
        lastMedia = media
        return media
    }

    private suspend fun televisionFormats(): List<TelevisionFormat> {
        val known = acceptedFormats
        if (known != null) {
            return known
        }
        val formats = try {
            client.acceptedFormats()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "reading the TV's formats failed", error)
            emptyList()
        }
        Log.i(TAG, "${renderer.name} accepts ${formats.size} formats: ${formats.joinToString { format -> "${format.mimeType} ${format.profile.orEmpty()}" }}")
        acceptedFormats = formats
        return formats
    }

    private fun televisionSubtitle(origin: String): TelevisionSubtitle? {
        val file = subtitleFile ?: return null
        return TelevisionSubtitle(file.id, origin + file.srtPath)
    }

    // Asked over loopback: where the stream really starts and how long the title is.
    private suspend fun probeContinuous(url: String): ContinuousProbe = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode != 200) {
                throw StreamUnavailableException("The stream could not be prepared for the TV.")
            }
            val start = connection.getHeaderField("X-Otter-Start")?.toDoubleOrNull() ?: 0.0
            val duration = connection.getHeaderField("X-Otter-Duration")?.toDoubleOrNull() ?: 0.0
            val height = connection.getHeaderField("X-Otter-Height")?.toIntOrNull()
            ContinuousProbe(start, duration, connection.contentType ?: "video/mpeg", height)
        } finally {
            connection.disconnect()
        }
    }

    private fun fail(error: Exception) {
        Log.w(TAG, "TV playback failed", error)
        reportFailure(error)
        var message = error.message
        if (message == null) {
            message = "The TV could not play this."
        }
        var errorCode = PlaybackException.ERROR_CODE_REMOTE_ERROR
        if (error is StreamUnavailableException) {
            errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        }
        if (error is TelevisionRefusedException) {
            errorCode = PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        }
        playerError = PlaybackException(message, error, errorCode)
        playbackState = Player.STATE_IDLE
        televisionPlaying = false
        stopWork()
        invalidateState()
    }

    // What a TV that refuses to play needs to be reproduced: the TV, what it accepts and what it was sent.
    private fun reportFailure(error: Exception) {
        val media = lastMedia
        val formats = acceptedFormats.orEmpty().joinToString { format -> "${format.mimeType} ${format.profile.orEmpty()}".trim() }
        ErrorReporting.capture(
            error,
            "dlna.playback",
            mapOf(
                "tv_name" to renderer.name,
                "tv_model" to renderer.model,
                "tv_description" to renderer.description,
                "tv_accepted_formats" to formats,
                "media_url" to media?.url,
                "media_mime_type" to media?.mimeType,
                "media_profile" to media?.profile,
                "media_continuous" to isContinuous,
                "media_height" to streamHeight,
                "played_before_failing" to hasPlayed,
            ),
        )
    }

    // Polling ------------------------------------------------------------------

    private fun startPolling() {
        pollFailures = 0
        pollJob = scope.launch {
            while (true) {
                poll()
                delay(POLL_MILLISECONDS)
            }
        }
    }

    private suspend fun poll() {
        try {
            val transport = client.transportState()
            val position = client.position()
            pollFailures = 0
            onTelevisionState(transport, position)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            pollFailures++
            if (pollFailures >= MAX_POLL_FAILURES) {
                fail(DlnaException("Lost the connection to the TV."))
            }
        }
    }

    private suspend fun onTelevisionState(transport: TransportState, position: TransportPosition) {
        when (transport) {
            TransportState.PLAYING -> onRunning(position, playing = true)
            TransportState.PAUSED -> onRunning(position, playing = false)
            TransportState.TRANSITIONING -> playbackState = Player.STATE_BUFFERING
            TransportState.STOPPED, TransportState.NO_MEDIA -> onStopped(position)
            TransportState.UNKNOWN -> Unit
        }
        invalidateState()
    }

    private suspend fun onRunning(position: TransportPosition, playing: Boolean) {
        televisionStopped = false
        playbackState = Player.STATE_READY
        televisionPlaying = playing
        report(streamOffsetSeconds + position.positionSeconds)
        if (!isContinuous && position.durationSeconds > 0) {
            durationSeconds = position.durationSeconds
        }
        if (playing && !hasPlayed) {
            hasPlayed = true
            streamHeight?.let { height -> heights.recordPlayed(renderer.id, height) }
        }
        // Paused before it ever played is the TV still loading, not its remote.
        if (!hasPlayed) {
            retryStart()
            return
        }
        followTelevisionRemote(playing)
        val fileSeek = pendingFileSeekSeconds
        if (playing && fileSeek != null) {
            pendingFileSeekSeconds = null
            lastCommandAtMilliseconds = SystemClock.elapsedRealtime()
            client.seek(fileSeek)
        }
    }

    // Play and pause pressed on the TV's own remote show up in the app.
    private fun followTelevisionRemote(playing: Boolean) {
        val settled = SystemClock.elapsedRealtime() - lastCommandAtMilliseconds > COMMAND_SETTLE_MILLISECONDS
        if (settled && playing != playWhenReady) {
            playWhenReady = playing
        }
    }

    // Samsungs report STOPPED for a while before they start.
    private suspend fun onNotStartedYet(position: TransportPosition) {
        if (refusedTheVideo(position)) {
            streamHeight?.let { height -> heights.recordRefused(renderer.id, height) }
            fail(TelevisionRefusedException("The TV can't play this video."))
            return
        }
        if (SystemClock.elapsedRealtime() - loadedAtMilliseconds > START_TIMEOUT_MILLISECONDS) {
            fail(TelevisionRefusedException("The TV didn't start playing."))
            return
        }
        retryStart()
    }

    private fun refusedTheVideo(position: TransportPosition): Boolean {
        if (position.durationSeconds <= 0) {
            refusingSinceMilliseconds = null
            return false
        }
        val now = SystemClock.elapsedRealtime()
        val since = refusingSinceMilliseconds
        if (since == null) {
            refusingSinceMilliseconds = now
            return false
        }
        return now - since > REFUSAL_MILLISECONDS
    }

    private suspend fun retryStart() {
        val now = SystemClock.elapsedRealtime()
        if (!playWhenReady || now - lastCommandAtMilliseconds < START_RETRY_MILLISECONDS) {
            return
        }
        lastCommandAtMilliseconds = now
        try {
            client.play()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "repeating Play failed", error)
        }
    }

    private suspend fun onStopped(position: TransportPosition) {
        televisionPlaying = false
        if (!hasPlayed) {
            onNotStartedYet(position)
            return
        }
        televisionStopped = true
        if (durationSeconds > 0 && reportedSeconds >= durationSeconds - END_TOLERANCE_SECONDS) {
            playbackState = Player.STATE_ENDED
            return
        }
        playWhenReady = false
    }
}
