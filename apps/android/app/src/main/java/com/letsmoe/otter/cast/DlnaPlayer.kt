package com.letsmoe.otter.cast

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.letsmoe.otter.backend.EmbeddedBackend
import com.letsmoe.otter.cast.overlay.CaptionPaths
import com.letsmoe.otter.cast.overlay.OverlayJob
import com.letsmoe.otter.cast.overlay.OverlayJobs
import com.letsmoe.otter.cast.overlay.OverlayLiveInputs
import com.letsmoe.otter.cast.overlay.TelevisionHints
import com.letsmoe.otter.cast.overlay.TitleCardRequest
import com.letsmoe.otter.cast.overlay.TitleText
import com.letsmoe.otter.cast.overlay.TogetherChatLine
import com.letsmoe.otter.cast.overlay.TogetherMoments
import com.letsmoe.otter.cast.overlay.TogetherReaction
import com.letsmoe.otter.cast.overlay.endsAtLabel
import com.letsmoe.otter.together.TogetherSession
import com.letsmoe.otter.cast.overlay.loadOverlayPlan
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.reporting.ErrorReporting
import com.letsmoe.otter.settings.NightVolume
import com.letsmoe.otter.settings.PlaybackSettings
import com.letsmoe.otter.ui.player.captionLookOf
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
import java.net.SocketTimeoutException
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
// Taps on skip and scrubs within this window become one seek: TVs reject a Seek while still
// TRANSITIONING from the last one (UPnP 701) and may give up on the stream after a few.
private const val SEEK_SETTLE_MILLISECONDS = 600L
private const val SEEK_RETRY_MILLISECONDS = 1000L
private const val MAX_SEEK_ATTEMPTS = 5
private const val UPNP_TRANSITION_NOT_AVAILABLE = 701
// An ERROR_OCCURRED this recent explains a STOPPED: the TV failed, nobody pressed stop.
private const val TELEVISION_ERROR_WINDOW_MILLISECONDS = 15_000L
// How often a stream that failed on the TV after playing is loaded again at its position.
private const val MAX_RECOVERIES = 2
// A stop this close to the end counts as the end.
private const val END_TOLERANCE_SECONDS = 15.0
// The phone's encoder makes at most 1080p; the TV gets it as MPEG-TS.
private const val OVERLAY_MAX_HEIGHT = 1080
private const val OVERLAY_MIME_TYPE = "video/mpeg"

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

// Only for TVs with a volume control: a media session then routes the phone's volume keys to the TV.
private val volumeCommands = Player.Commands.Builder().addAll(
    Player.COMMAND_GET_DEVICE_VOLUME,
    Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
    Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
).build()

// UPnP volumes run from 0 to 100.
private const val MAX_VOLUME = 100

private val remoteVolumeInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(MAX_VOLUME).build()

// How much one press of a volume button changes the TV's volume (0 to 100).
private const val VOLUME_BUTTON_STEP = 3
// The TV's volume is read again this often (in polls), for changes made with its own remote.
private const val VOLUME_READ_EVERY_POLLS = 5

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
class DlnaPlayer(private val context: Context, initialRenderer: DlnaRenderer) : SimpleBasePlayer(Looper.getMainLooper()) {

    /** The TV; replaced when it is found again under another address. */
    var renderer: DlnaRenderer = initialRenderer
        private set

    private val journal = CastJournal(initialRenderer)
    private var client = DlnaClient(initialRenderer, journal)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val heights = TelevisionHeights(context)
    private val nightVolumeGuard = NightVolumeGuard(scope, ::readVolume) { volume -> client.setVolume(volume) }

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

    /** The title's skippable parts and next episode, drawn into the picture from the next load on. */
    var hints: TelevisionHints = TelevisionHints()
    /** The viewer cancelled the next episode during these credits; the picture drops its card. */
    @Volatile
    var upNextDismissed = false
    /** The viewer's caption timing in seconds, positive for later; the picture follows within seconds. */
    @Volatile
    var captionDelaySeconds = 0.0
    /** Nothing plays after this episode; the picture says so during the credits. */
    @Volatile
    var stopsAfterEpisode = false
    // A replay with captions shows them until this second of the title; null when none runs.
    @Volatile
    private var replayUntilSeconds: Double? = null
    // What a replay shows while the main captions are off.
    private var replayCaptionFile: TelevisionSubtitleFile? = null
    // A second caption language, drawn smaller under the main one (overlay streams only).
    private var secondSubtitleFile: TelevisionSubtitleFile? = null
    // The title the card last introduced: a new source for the same episode shows no card again.
    private var introducedTitle: TitleText? = null
    // The overlay stream the TV plays; null when it plays a plain one.
    private var overlayJobId: String? = null
    // Set once the phone's encoder failed: this TV gets plain streams from then on.
    private var overlaysFailed = false

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
    private var televisionVolume = 0
    private var pollsSinceVolumeRead = VOLUME_READ_EVERY_POLLS
    // The latest seek asked for and not sent yet; null when none is waiting.
    private var seekTargetSeconds: Double? = null
    private var televisionErrorAtMilliseconds: Long? = null
    private var recoveries = 0

    private var loadJob: Job? = null
    private var pollJob: Job? = null
    private var seekJob: Job? = null

    // State --------------------------------------------------------------------

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(availableCommands())
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlayerError(playerError)
        if (supportsVolume) {
            builder.setDeviceInfo(remoteVolumeInfo).setDeviceVolume(televisionVolume)
        }
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

    private fun availableCommands(): Player.Commands {
        if (!supportsVolume) {
            return dlnaCommands
        }
        return dlnaCommands.buildUpon().addAll(volumeCommands).build()
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

    /**
     * Shows captions until [untilSeconds] of the title (from [file] when the
     * main ones are off); set before seeking back, whose new stream draws them.
     */
    fun startReplay(untilSeconds: Double, file: TelevisionSubtitleFile?) {
        replayUntilSeconds = untilSeconds
        replayCaptionFile = file
    }

    /** The replay reached where it started: captions follow the viewer's choice again. */
    fun endReplay() {
        replayUntilSeconds = null
    }

    /** Draws a second caption language under the main one (or none); the TV loads the stream again. */
    fun showSecondSubtitle(file: TelevisionSubtitleFile?) {
        if (file == secondSubtitleFile) {
            return
        }
        secondSubtitleFile = file
        if (mediaItem == null || playbackState == Player.STATE_IDLE || overlayJobId == null) {
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

    /** Sets the TV's volume, never above the night's limit. */
    fun changeVolume(volume: Int) {
        val allowed = NightVolume.capped(volume, PlaybackSettings.televisionVolumeMaximum).coerceIn(0, MAX_VOLUME)
        televisionVolume = allowed
        invalidateState()
        sendCommand { client.setVolume(allowed) }
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        changeVolume(deviceVolume)
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        changeVolume(televisionVolume + VOLUME_BUTTON_STEP)
        return Futures.immediateVoidFuture()
    }

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        changeVolume(televisionVolume - VOLUME_BUTTON_STEP)
        return Futures.immediateVoidFuture()
    }

    // Reads the volume now and then, so the session shows what the TV's own remote changed.
    private suspend fun refreshVolume() {
        if (!supportsVolume) {
            return
        }
        pollsSinceVolumeRead++
        if (pollsSinceVolumeRead < VOLUME_READ_EVERY_POLLS) {
            return
        }
        pollsSinceVolumeRead = 0
        val volume = readVolume() ?: return
        if (volume != televisionVolume) {
            televisionVolume = volume
            invalidateState()
        }
    }

    // Commands -----------------------------------------------------------------

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        stopWork()
        journal.startCast()
        var index = startIndex
        if (index == C.INDEX_UNSET) {
            index = 0
        }
        mediaItem = mediaItems.getOrNull(index)
        playbackState = Player.STATE_IDLE
        playerError = null
        durationSeconds = 0.0
        recoveries = 0
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
        if (playbackState == Player.STATE_ENDED && !isContinuous) {
            playbackState = Player.STATE_BUFFERING
            startLoad(targetSeconds)
            return Futures.immediateVoidFuture()
        }
        requestSeek(targetSeconds)
        return Futures.immediateVoidFuture()
    }

    // Only the last of several quick seeks reaches the TV.
    private fun requestSeek(targetSeconds: Double) {
        seekTargetSeconds = targetSeconds
        lastCommandAtMilliseconds = SystemClock.elapsedRealtime()
        if (seekJob?.isActive == true) {
            return
        }
        seekJob = scope.launch { runSeeks() }
    }

    private suspend fun runSeeks() {
        delay(SEEK_SETTLE_MILLISECONDS)
        while (true) {
            val targetSeconds = seekTargetSeconds ?: return
            seekTargetSeconds = null
            // A continuous stream seeks by starting again at the new position.
            if (isContinuous) {
                startLoad(targetSeconds)
                return
            }
            seekUntilAccepted(targetSeconds)
        }
    }

    // Repeated while the TV is busy with the last transition; a newer target takes over.
    private suspend fun seekUntilAccepted(targetSeconds: Double) {
        repeat(MAX_SEEK_ATTEMPTS) {
            if (!trySeek(targetSeconds)) {
                return
            }
            delay(SEEK_RETRY_MILLISECONDS)
            if (seekTargetSeconds != null) {
                return
            }
        }
    }

    /** Sends one Seek; true when the TV was still transitioning and it should be repeated. */
    private suspend fun trySeek(targetSeconds: Double): Boolean {
        lastCommandAtMilliseconds = SystemClock.elapsedRealtime()
        try {
            client.seek(targetSeconds)
            return false
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: DlnaException) {
            if (error.upnpErrorCode == UPNP_TRANSITION_NOT_AVAILABLE) {
                return true
            }
            Log.w(TAG, "seeking failed", error)
            return false
        } catch (error: Exception) {
            Log.w(TAG, "seeking failed", error)
            return false
        }
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
        nightVolumeGuard.stop()
        loadJob?.cancel()
        pollJob?.cancel()
        seekJob?.cancel()
        loadJob = null
        pollJob = null
        seekJob = null
        seekTargetSeconds = null
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
        televisionErrorAtMilliseconds = null
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
        stopBeforeLoading()
        client.load(media)
        if (playWhenReady) {
            client.play()
        }
        loadedAtMilliseconds = SystemClock.elapsedRealtime()
        lastCommandAtMilliseconds = loadedAtMilliseconds
    }

    // The first command of a load also tells whether the TV is still where it was.
    private suspend fun stopBeforeLoading() {
        try {
            client.stop()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unreachable: TelevisionUnreachableException) {
            findTelevisionAgain(unreachable)
        } catch (error: Exception) {
            // Nothing was playing.
        }
    }

    // A TV that got another address from the router answers the SSDP search under it.
    private suspend fun findTelevisionAgain(unreachable: TelevisionUnreachableException) {
        val found = discoverRenderers(context).firstOrNull { candidate -> candidate.id == renderer.id }
        if (found == null || found.controlUrl == renderer.controlUrl) {
            throw unreachable
        }
        journal.note("Found the TV again at ${found.controlUrl}", mapOf("tv_previous_control_url" to renderer.controlUrl))
        renderer = found
        client = DlnaClient(found, journal)
    }

    private suspend fun televisionMedia(item: MediaItem, origin: String, startSeconds: Double): TelevisionMedia {
        val playerUrl = item.localConfiguration?.uri?.toString().orEmpty()
        val title = item.mediaMetadata.title?.toString().orEmpty()
        overlayJobId = null
        if (drawsOverlays(playerUrl)) {
            return overlayMedia(item, origin, startSeconds, title)
        }
        isContinuous = item.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8
        if (isContinuous) {
            return continuousMedia(playerUrl, origin, startSeconds, title)
        }
        return fileMedia(item, playerUrl, origin, startSeconds, title)
    }

    private suspend fun continuousMedia(playerUrl: String, origin: String, startSeconds: Double, title: String): TelevisionMedia {
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

    private suspend fun fileMedia(item: MediaItem, playerUrl: String, origin: String, startSeconds: Double, title: String): TelevisionMedia {
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
    // Without [captionHints] the TV isn't told about a caption file: they are in the picture.
    private suspend fun televisionMediaFor(
        origin: String,
        path: String,
        title: String,
        mimeType: String,
        seekableBytes: Boolean,
        captionHints: Boolean = true,
    ): TelevisionMedia {
        val format = announcedFormat(mimeType, televisionFormats())
        val formattedPath = TelevisionUrls.withFormat(path, mimeType, format)
        val castPath = TelevisionUrls.withCast(formattedPath, journal.castId)
        var url = origin + castPath
        var subtitle: TelevisionSubtitle? = null
        if (captionHints) {
            url = origin + TelevisionUrls.withSubtitle(castPath, subtitleFile?.id)
            subtitle = televisionSubtitle(origin)
        }
        val media = TelevisionMedia(url, title, format.mimeType, seekableBytes, subtitle, format.profile)
        journal.note(
            "Casting ${format.mimeType} (stream is $mimeType)",
            mapOf("media_url" to url, "media_profile" to format.profile, "media_continuous" to !seekableBytes),
        )
        lastMedia = media
        return media
    }

    // Stills -------------------------------------------------------------------

    /** The tallest picture the phone's encoder makes for this TV. */
    val stillHeight: Int
        get() = overlayMaxHeight()

    /** False once the phone's encoder failed for this TV: it gets no pictures from the phone any more. */
    val encodesPictures: Boolean
        get() = !overlaysFailed

    /** Shows the still picture of [streamPath] (an overlay stream path) on the TV; nothing may be playing. */
    suspend fun showStill(streamPath: String, title: String) {
        val origin = lanStreamOrigin(context) ?: throw DlnaException("The phone is not on Wi-Fi.")
        val media = televisionMediaFor(origin, streamPath, title, OVERLAY_MIME_TYPE, seekableBytes = false, captionHints = false)
        stopBeforeLoading()
        client.load(media)
        client.play()
    }

    /** Takes the still picture off the TV. */
    suspend fun clearStill() {
        client.stop()
    }

    // Overlays -----------------------------------------------------------------

    // Only streams the backend serves can be re-encoded; a failed encoder isn't tried again on this TV.
    private fun drawsOverlays(playerUrl: String): Boolean {
        return PlaybackSettings.televisionOverlays && !overlaysFailed && playerUrl.startsWith(EmbeddedBackend.BASE_URL)
    }

    // The phone re-encodes the title with captions and hints drawn in; the TV gets one continuous stream.
    private suspend fun overlayMedia(item: MediaItem, origin: String, startSeconds: Double, title: String): TelevisionMedia {
        val playerUrl = item.localConfiguration?.uri?.toString().orEmpty()
        val mimeType = item.localConfiguration?.mimeType
        val maxHeight = overlayMaxHeight()
        durationSeconds = overlayDurationSeconds(playerUrl, mimeType, startSeconds)
        isContinuous = true
        streamOffsetSeconds = startSeconds
        streamHeight = minOf(linkHeight ?: maxHeight, maxHeight)
        report(startSeconds)
        pendingFileSeekSeconds = null
        val captionPaths = CaptionPaths(subtitleFile?.srtPath, secondSubtitleFile?.srtPath, replayCaptionPath())
        val plan = loadOverlayPlan(context, hints, captionPaths, titleCardRequest(startSeconds), durationSeconds)
        val brightens = PlaybackSettings.brightenDarkScenes
        val job = OverlayJob(playerUrl, mimeType, startSeconds, maxHeight, plan, liveInputs(), brightens, ::onOverlayFailed)
        overlayJobId = OverlayJobs.register(job)
        journal.note("Drawing into the picture", mapOf("overlay_job" to job.id, "overlay_captions" to plan.captions.size))
        return televisionMediaFor(origin, "/api/stream/overlay?job=${job.id}", title, OVERLAY_MIME_TYPE, seekableBytes = false, captionHints = false)
    }

    private fun liveInputs(): OverlayLiveInputs {
        return OverlayLiveInputs(
            captionLook = { captionLookOf(ProfileSession.activeProfile.value) },
            upNextDismissed = { upNextDismissed },
            captionDelaySeconds = { captionDelaySeconds },
            voiceBoost = { ProfileSession.activeProfile.value?.voiceBoost == true },
            together = ::togetherMoments,
            evenVolume = { PlaybackSettings.evenVolumeActive },
            replayUntilSeconds = { replayUntilSeconds },
            stopsAfterEpisode = { stopsAfterEpisode },
        )
    }

    // Only needed while a replay runs and the main captions are off.
    private fun replayCaptionPath(): String? {
        if (replayUntilSeconds == null || subtitleFile != null) {
            return null
        }
        return replayCaptionFile?.srtPath
    }

    // The first stream of a title shows what plays and when it ends; seeks and new links for the same title don't.
    private fun titleCardRequest(startSeconds: Double): TitleCardRequest? {
        val text = hints.titleText ?: return null
        if (!PlaybackSettings.televisionTitleCard || text == introducedTitle) {
            return null
        }
        introducedTitle = text
        return TitleCardRequest(startSeconds, endsAtLabel(context, durationSeconds - startSeconds))
    }

    private fun togetherMoments(): TogetherMoments {
        if (TogetherSession.room.value == null) {
            return TogetherMoments()
        }
        val reactions = TogetherSession.reactions.value.map { reaction -> TogetherReaction(reaction.id, reaction.emoji, reaction.from.name) }
        val chat = TogetherSession.chat.value.map { line -> TogetherChatLine(line.id, line.from.name, line.text) }
        return TogetherMoments(reactions, chat)
    }

    private fun overlayMaxHeight(): Int {
        val refused = heights.refusedHeight(renderer.id) ?: return OVERLAY_MAX_HEIGHT
        return minOf(refused - 1, OVERLAY_MAX_HEIGHT)
    }

    // The title's length: HLS from the backend's continuous probe, files from their header.
    private suspend fun overlayDurationSeconds(playerUrl: String, mimeType: String?, startSeconds: Double): Double {
        if (mimeType != MimeTypes.APPLICATION_M3U8) {
            return fileDurationSeconds(playerUrl)
        }
        val path = TelevisionUrls.continuousPath(playerUrl, startSeconds, null) ?: return 0.0
        return probeContinuous(EmbeddedBackend.BASE_URL + path).durationSeconds
    }

    // Unknown (0) when the file can't be read; the TV still plays it.
    private suspend fun fileDurationSeconds(url: String): Double = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(url, emptyMap())
            val milliseconds = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            (milliseconds ?: 0L) / 1000.0
        } catch (error: RuntimeException) {
            Log.w(TAG, "reading the file's duration failed", error)
            0.0
        } finally {
            retriever.release()
        }
    }

    // Called from the encoder's thread. The TV gets the plain stream from where it is now.
    private fun onOverlayFailed(job: OverlayJob, error: Exception) {
        scope.launch {
            if (job.id != overlayJobId) {
                return@launch
            }
            overlaysFailed = true
            journal.note("Drawing into the picture failed; casting the plain stream", mapOf("overlay_error" to error.toString()))
            ErrorReporting.capture(error, "dlna.overlay", mapOf("cast_id" to journal.castId, "tv_name" to renderer.name, "tv_exchanges" to journal.text()))
            startLoad(currentPositionMilliseconds() / 1000.0)
        }
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
        val formatList = formats.joinToString { format -> "${format.mimeType} ${format.profile.orEmpty()}".trim() }
        Log.i(TAG, "${renderer.name} accepts ${formats.size} formats: $formatList")
        journal.note("TV accepts ${formats.size} formats", mapOf("tv_accepted_formats" to formatList, "tv_description" to renderer.description))
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
        // Cancelled when another link is picked while the backend still opens this one.
        disconnectingOnCancel(connection) { readProbe(connection) }
    }

    private fun readProbe(connection: HttpURLConnection): ContinuousProbe {
        try {
            if (connection.responseCode != 200) {
                throw StreamUnavailableException("The stream could not be prepared for the TV. ${backendReason(connection)}".trim())
            }
            val start = connection.getHeaderField("X-Otter-Start")?.toDoubleOrNull() ?: 0.0
            val duration = connection.getHeaderField("X-Otter-Duration")?.toDoubleOrNull() ?: 0.0
            val height = connection.getHeaderField("X-Otter-Height")?.toIntOrNull()
            return ContinuousProbe(start, duration, connection.contentType ?: "video/mpeg", height)
        } finally {
            connection.disconnect()
        }
    }

    // A HEAD answer has no body, so the backend's reason is only the status.
    private fun backendReason(connection: HttpURLConnection): String {
        return "(HTTP ${connection.responseCode} ${connection.responseMessage.orEmpty()})"
    }

    private fun fail(error: Exception) {
        Log.w(TAG, "TV playback failed", error)
        reportFailure(error)
        val message = failureMessage(error)
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

    // Socket errors read "timeout" or "Socket closed"; the user gets what that means for the TV.
    private fun failureMessage(error: Exception): String {
        if (error is SocketTimeoutException) {
            return "The TV stopped answering."
        }
        val message = error.message
        if (message == null) {
            return "The TV could not play this."
        }
        return message
    }

    // What a TV that refuses to play needs to be reproduced: the TV, what it accepts and what it was sent.
    private fun reportFailure(error: Exception) {
        val media = lastMedia
        val formats = acceptedFormats.orEmpty().joinToString { format -> "${format.mimeType} ${format.profile.orEmpty()}".trim() }
        ErrorReporting.capture(
            error,
            "dlna.playback",
            mapOf(
                "cast_id" to journal.castId,
                "tv_exchanges" to journal.text(),
                "tv_name" to renderer.name,
                "tv_model" to renderer.model,
                "tv_description" to renderer.description,
                "tv_accepted_formats" to formats,
                "media_url" to media?.url,
                "media_mime_type" to media?.mimeType,
                "media_profile" to media?.profile,
                "media_continuous" to isContinuous,
                "media_overlay" to (overlayJobId != null),
                "media_height" to streamHeight,
                "played_before_failing" to hasPlayed,
            ),
        )
    }

    // Polling ------------------------------------------------------------------

    private fun startPolling() {
        pollFailures = 0
        nightVolumeGuard.start()
        pollJob = scope.launch {
            while (true) {
                poll()
                delay(POLL_MILLISECONDS)
            }
        }
    }

    private suspend fun poll() {
        try {
            val transport = client.transportInfo()
            val position = client.position()
            pollFailures = 0
            refreshVolume()
            if (transport.errorOccurred) {
                televisionErrorAtMilliseconds = SystemClock.elapsedRealtime()
            }
            onTelevisionState(transport.state, position)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            pollFailures++
            if (pollFailures >= MAX_POLL_FAILURES) {
                fail(TelevisionUnreachableException("Lost the connection to the TV."))
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
        // The TV still reports the old position until a waiting seek reaches it.
        if (seekJob?.isActive != true) {
            report(streamOffsetSeconds + position.positionSeconds)
        }
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
            requestSeek(fileSeek)
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
        // The TV said it failed to open the stream: no need to wait out the start timeout.
        if (reportedErrorRecently()) {
            fail(TelevisionRefusedException("The TV couldn't open this video."))
            return
        }
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
        if (reportedErrorRecently()) {
            recoverFromTelevisionError()
            return
        }
        playWhenReady = false
    }

    private fun reportedErrorRecently(): Boolean {
        val errorAt = televisionErrorAtMilliseconds ?: return false
        return SystemClock.elapsedRealtime() - errorAt < TELEVISION_ERROR_WINDOW_MILLISECONDS
    }

    // Stopped by an error after playing (e.g. after seeks): loaded again where it was, not paused as if by its remote.
    private fun recoverFromTelevisionError() {
        if (recoveries >= MAX_RECOVERIES) {
            fail(DlnaException("The TV keeps stopping with an error."))
            return
        }
        recoveries++
        journal.note("TV stopped with an error; loading again at ${formatTime(reportedSeconds)}", emptyMap())
        startLoad(reportedSeconds)
    }
}
