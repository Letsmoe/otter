package com.letsmoe.otter.cast

import android.content.Context
import android.media.MediaMetadataRetriever
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
import com.letsmoe.otter.cast.session.SessionJob
import com.letsmoe.otter.cast.session.SessionScreen
import com.letsmoe.otter.cast.session.SessionTimeline
import com.letsmoe.otter.cast.session.TelevisionSession
import com.letsmoe.otter.cast.session.TelevisionSessions
import com.letsmoe.otter.together.TogetherSession
import com.letsmoe.otter.cast.overlay.loadOverlayPlan
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.reporting.ErrorReporting
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

/** The caption file the TV shows: [srtPath] is the backend path of its SRT. */
data class TelevisionSubtitleFile(val id: String, val srtPath: String)

/** What the backend says about a continuous stream before the TV gets it; [height] is null when unknown. */
private data class ContinuousProbe(val startSeconds: Double, val durationSeconds: Double, val mimeType: String, val height: Int?)

/**
 * Plays on a DLNA TV (e.g. an older Samsung) as a Media3 player, so the player
 * screen, progress saving and Watch Together drive the TV like the phone.
 * HLS goes to the TV as one continuous stream; seeking in it restarts the
 * stream at the new position. Files are seeked by the TV itself.
 *
 * With captions drawn into the picture, the TV instead gets one stream for
 * as long as it is connected (a [TelevisionSession]): titles, seeks and
 * caption changes replace what the phone encodes into it, and the position
 * comes from the session's clock instead of the TV.
 */
class DlnaPlayer(private val context: Context, initialRenderer: DlnaRenderer) : SimpleBasePlayer(Looper.getMainLooper()) {

    /** The TV; replaced when it is found again under another address. */
    var renderer: DlnaRenderer = initialRenderer
        private set

    private val journal = CastJournal(initialRenderer)
    private var client = DlnaClient(initialRenderer, journal)
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

    // The one stream the TV plays while overlays are drawn (see TelevisionSession); null without one.
    private var session: TelevisionSession? = null
    // The session's job showing the title; null while it shows the idle or a loading screen.
    private var sessionTitle: SessionJob? = null
    // Set once the TV didn't play a session's stream: titles reach it one stream each, as before.
    private var sessionsFailed = false
    private var sessionPlayed = false
    // Whether the TV was last told to play the session (it plays from the start).
    private var sessionPlaying = true
    private var sessionStartedAtMilliseconds = 0L
    // Opens the session on the TV, then follows the TV's state.
    private var sessionJob: Job? = null
    // What the session shows while no title plays (see showIdleScreen); read on the encoder's thread too.
    @Volatile
    private var chosenIdleScreen: SessionScreen = SessionScreen.Idle

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
        val sessionSeconds = sessionTitleSeconds()
        if (sessionSeconds != null) {
            seconds = sessionSeconds
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
        if (mediaItem == null || playbackState == Player.STATE_IDLE) {
            return
        }
        if (overlayJobId == null && session == null) {
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
        journal.startCast()
        var index = startIndex
        if (index == C.INDEX_UNSET) {
            index = 0
        }
        mediaItem = mediaItems.getOrNull(index)
        playbackState = Player.STATE_IDLE
        playerError = null
        // The last title may still show in the session until this one replaces it; its position isn't this one's.
        sessionTitle = null
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
        if (session != null) {
            setSessionPlaying(playWhenReady)
            return Futures.immediateVoidFuture()
        }
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
        // The TV keeps its stream and shows the idle screen in it.
        if (showIdleInSession()) {
            return Futures.immediateVoidFuture()
        }
        sendCommand { client.stop() }
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        stopWork()
        mediaItem = null
        playbackState = Player.STATE_IDLE
        televisionPlaying = false
        showIdleInSession()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        stopWork()
        endSession()
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
        seekJob?.cancel()
        loadJob = null
        pollJob = null
        seekJob = null
        seekTargetSeconds = null
    }

    // Loading ------------------------------------------------------------------

    private fun startLoad(startSeconds: Double) {
        // The browse picture and the screensaver step aside for titles; they ask again once nothing plays.
        chosenIdleScreen = SessionScreen.Idle
        if (playsInSession()) {
            startSessionLoad(startSeconds)
            return
        }
        // The TV is handed this title's own stream instead.
        endSession()
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

    // Idle screens -------------------------------------------------------------

    /** The height of the pictures the phone draws for this TV. */
    val screenHeight: Int
        get() = overlayMaxHeight()

    /** False once the phone's encoder failed or the TV refused its session stream: it gets no pictures from the phone. */
    val showsScreens: Boolean
        get() = !overlaysFailed && !sessionsFailed

    /**
     * Shows [screen] (the title being browsed, the screensaver or the idle screen) in the TV's
     * session whenever no title plays there; the TV keeps its stream. Opens a session for it when
     * the TV has none and plays nothing, e.g. with "Draw on the TV picture" off.
     */
    fun showIdleScreen(screen: SessionScreen) {
        chosenIdleScreen = screen
        if (!showsScreens || playsPlainStream()) {
            return
        }
        // Without "Draw on the TV picture" the session only exists for these pictures.
        if (screen == SessionScreen.Idle && !usesSessions()) {
            endPictureSession()
            return
        }
        ensureSession()
        refreshIdleScreen()
    }

    private fun idleScreen(): SessionScreen {
        return chosenIdleScreen
    }

    private fun playsPlainStream(): Boolean {
        return session == null && mediaItem != null && playbackState != Player.STATE_IDLE
    }

    private fun playsTitleInSession(): Boolean {
        return sessionTitle != null || loadJob?.isActive == true
    }

    // Puts the idle screen wanted now into the session, unless a title or its loading screen runs there.
    private fun refreshIdleScreen() {
        val current = session ?: return
        if (playsTitleInSession()) {
            return
        }
        showIfOther(current, idleScreen())
    }

    private fun showIfOther(current: TelevisionSession, screen: SessionScreen) {
        if (current.currentJob.screen != screen) {
            current.show(screen)
        }
    }

    private fun endPictureSession() {
        if (session == null || playsTitleInSession()) {
            return
        }
        endSession()
        sendCommand { client.stop() }
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
        val job = overlayJob(item, startSeconds, maxHeight)
        overlayJobId = OverlayJobs.register(job)
        return televisionMediaFor(origin, "/api/stream/overlay?job=${job.id}", title, OVERLAY_MIME_TYPE, seekableBytes = false, captionHints = false)
    }

    // The title from [startSeconds] with the captions, hints and live settings drawn into its picture.
    private suspend fun overlayJob(item: MediaItem, startSeconds: Double, maxHeight: Int): OverlayJob {
        val playerUrl = item.localConfiguration?.uri?.toString().orEmpty()
        val mimeType = item.localConfiguration?.mimeType
        val captionPaths = CaptionPaths(subtitleFile?.srtPath, secondSubtitleFile?.srtPath, replayCaptionPath())
        val plan = loadOverlayPlan(context, hints, captionPaths, titleCardRequest(startSeconds))
        val brightens = PlaybackSettings.brightenDarkScenes
        val job = OverlayJob(playerUrl, mimeType, startSeconds, maxHeight, plan, liveInputs(), brightens, ::onOverlayFailed)
        journal.note("Drawing into the picture", mapOf("overlay_job" to job.id, "overlay_captions" to plan.captions.size))
        return job
    }

    private fun liveInputs(): OverlayLiveInputs {
        return OverlayLiveInputs(
            captionLook = { captionLookOf(ProfileSession.activeProfile.value) },
            upNextDismissed = { upNextDismissed },
            captionDelaySeconds = { captionDelaySeconds },
            voiceBoost = { ProfileSession.activeProfile.value?.voiceBoost == true },
            together = ::togetherMoments,
            evenVolume = { PlaybackSettings.evenVolume },
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

    // TV session ---------------------------------------------------------------

    /**
     * Hands the TV its one stream for the whole connection when it gets overlays: the idle
     * screen first, then every title, seek and caption change inside it.
     */
    fun connect() {
        if (!usesSessions()) {
            return
        }
        ensureSession()
    }

    /** Whether the TV plays the session's stream: the phone keeps encoding while nothing plays. */
    val streamsContinuously: Boolean
        get() = session != null

    /** The TV shows the loading screen (with [title] when known) until the next title starts; no-op without a session. */
    fun showLoading(title: String?) {
        val current = session ?: return
        sessionTitle = null
        if (current.currentJob.screen is SessionScreen.Loading) {
            return
        }
        current.show(SessionScreen.Loading(title))
    }

    private fun usesSessions(): Boolean {
        return PlaybackSettings.televisionOverlays && !overlaysFailed && !sessionsFailed
    }

    private fun playsInSession(): Boolean {
        val item = mediaItem ?: return false
        return usesSessions() && drawsOverlays(item.localConfiguration?.uri?.toString().orEmpty())
    }

    // The TV is handed the session's URL once; a session the TV stopped is replaced by a new one.
    private fun ensureSession(): TelevisionSession {
        val existing = session
        if (existing != null) {
            return existing
        }
        val created = TelevisionSession(overlayMaxHeight(), ::onSessionJobFailed, idleScreen = ::idleScreen)
        TelevisionSessions.register(created)
        keepPhoneAwake()
        session = created
        sessionPlayed = false
        sessionPlaying = true
        televisionStopped = false
        sessionJob = scope.launch { runSession(created) }
        return created
    }

    // The phone encodes the session's stream for as long as it lives, also with its screen off.
    private fun keepPhoneAwake() {
        try {
            CastKeepAliveService.start(context, renderer.name)
        } catch (notAllowed: IllegalStateException) {
            // Android refuses a foreground service started from the background; the TV may lose the stream then.
            Log.w(TAG, "keeping the phone awake for the TV failed", notAllowed)
        }
    }

    private suspend fun runSession(created: TelevisionSession) {
        try {
            openSessionOnTelevision(created)
            followSession(created)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            onSessionError(error)
        }
    }

    private suspend fun openSessionOnTelevision(created: TelevisionSession) {
        val origin = lanStreamOrigin(context) ?: throw DlnaException("The phone is not on Wi-Fi.")
        val path = "/api/stream/tv?session=${created.id}"
        val media = televisionMediaFor(origin, path, "Otter", OVERLAY_MIME_TYPE, seekableBytes = false, captionHints = false)
        stopBeforeLoading()
        client.load(media)
        client.play()
        created.setPlaying(true)
        sessionStartedAtMilliseconds = SystemClock.elapsedRealtime()
        lastCommandAtMilliseconds = sessionStartedAtMilliseconds
        journal.note("Handed the TV its session stream", mapOf("tv_session" to created.id))
    }

    // Polls the TV until it stops the stream; a TV that stops answering ends in onSessionError.
    private suspend fun followSession(created: TelevisionSession) {
        pollFailures = 0
        while (sessionKeepsPlaying(created)) {
            updateSessionTitle(created)
            invalidateState()
            delay(POLL_MILLISECONDS)
        }
        onSessionStoppedByTelevision()
    }

    // False once the TV stopped the stream after playing it.
    private suspend fun sessionKeepsPlaying(created: TelevisionSession): Boolean {
        val transport = try {
            client.transportInfo()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            pollFailures++
            if (pollFailures >= MAX_POLL_FAILURES) {
                throw TelevisionUnreachableException("Lost the connection to the TV.")
            }
            return true
        }
        pollFailures = 0
        return when (transport.state) {
            TransportState.PLAYING -> onSessionRunning(created, playing = true)
            TransportState.PAUSED -> onSessionRunning(created, playing = false)
            TransportState.STOPPED, TransportState.NO_MEDIA -> onSessionNotRunning()
            TransportState.TRANSITIONING, TransportState.UNKNOWN -> true
        }
    }

    private suspend fun onSessionRunning(created: TelevisionSession, playing: Boolean): Boolean {
        televisionStopped = false
        if (!sessionPlayed && !playing) {
            // Paused before it ever played is the TV still loading.
            retrySessionStart()
            return true
        }
        if (!sessionPlayed) {
            sessionPlayed = true
            heights.recordPlayed(renderer.id, created.pictureHeight)
        }
        followSessionRemote(created, playing)
        return true
    }

    // Play and pause pressed on the TV's own remote show up in the app; the session's clock follows the TV.
    private fun followSessionRemote(created: TelevisionSession, playing: Boolean) {
        val settled = SystemClock.elapsedRealtime() - lastCommandAtMilliseconds > COMMAND_SETTLE_MILLISECONDS
        if (!settled || playing == sessionPlaying) {
            return
        }
        sessionPlaying = playing
        created.setPlaying(playing)
        if (mediaItem != null) {
            playWhenReady = playing
        }
    }

    // Samsungs report STOPPED for a while before they start; one that never starts refuses the stream.
    private suspend fun onSessionNotRunning(): Boolean {
        if (sessionPlayed) {
            return false
        }
        if (SystemClock.elapsedRealtime() - sessionStartedAtMilliseconds > START_TIMEOUT_MILLISECONDS) {
            throw TelevisionRefusedException("The TV didn't start playing.")
        }
        retrySessionStart()
        return true
    }

    private suspend fun retrySessionStart() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCommandAtMilliseconds < START_RETRY_MILLISECONDS) {
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

    // The title is ready once the TV shows its job, and ends once it showed all of it.
    private fun updateSessionTitle(created: TelevisionSession) {
        val title = sessionTitle ?: return
        val shown = created.shownMicroseconds()
        val end = title.endMicroseconds
        if (end != null && shown >= end) {
            report(currentPositionMilliseconds() / 1000.0)
            sessionTitle = null
            playbackState = Player.STATE_ENDED
            // The session moved on when the title's encoding ended; what it should show may have changed since.
            refreshIdleScreen()
            return
        }
        val loading = loadJob?.isActive == true || seekJob?.isActive == true
        if (playbackState == Player.STATE_BUFFERING && !loading && SessionTimeline.hasReached(title.startMicroseconds, shown)) {
            playbackState = Player.STATE_READY
        }
    }

    // The title's second the TV shows; null when no title job runs or a seek waits.
    private fun sessionTitleSeconds(): Double? {
        val title = sessionTitle ?: return null
        val current = session ?: return null
        val screen = title.screen
        if (screen !is SessionScreen.Title || seekJob?.isActive == true) {
            return null
        }
        return SessionTimeline.titleSecondsShown(screen.overlay.startSeconds, title.startMicroseconds, current.shownMicroseconds())
    }

    // Seeks, caption changes and new links replace the session's job; the TV keeps its stream.
    private fun startSessionLoad(startSeconds: Double) {
        stopWork()
        playbackState = Player.STATE_BUFFERING
        playerError = null
        televisionPlaying = false
        isContinuous = true
        streamOffsetSeconds = startSeconds
        report(startSeconds)
        invalidateState()
        val current = ensureSession()
        loadJob = scope.launch {
            try {
                playInSession(current, startSeconds)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                showIdleInSession()
                fail(error)
            }
        }
    }

    // The loading screen shows while the title's length is read the first time.
    private suspend fun playInSession(current: TelevisionSession, startSeconds: Double) {
        val item = mediaItem ?: return
        if (durationSeconds <= 0) {
            showLoading(loadingTitle(item))
            val playerUrl = item.localConfiguration?.uri?.toString().orEmpty()
            durationSeconds = overlayDurationSeconds(playerUrl, item.localConfiguration?.mimeType, startSeconds)
        }
        val overlay = overlayJob(item, startSeconds, current.pictureHeight)
        streamHeight = current.pictureHeight
        sessionTitle = current.show(SessionScreen.Title(overlay))
    }

    private fun loadingTitle(item: MediaItem): String? {
        val text = hints.titleText
        if (text != null) {
            return text.title
        }
        return item.mediaMetadata.title?.toString()
    }

    private fun setSessionPlaying(playing: Boolean) {
        val current = session ?: return
        lastCommandAtMilliseconds = SystemClock.elapsedRealtime()
        sessionPlaying = playing
        current.setPlaying(playing)
        sendCommand { if (playing) client.play() else client.pause() }
    }

    // True when a session shows the idle screen now; false without one.
    private fun showIdleInSession(): Boolean {
        val current = session ?: return false
        sessionTitle = null
        showIfOther(current, idleScreen())
        return true
    }

    // The TV stopped the stream (its remote, or it gave up): as with a stopped TV, play loads the title again.
    private fun onSessionStoppedByTelevision() {
        report(currentPositionMilliseconds() / 1000.0)
        sessionJob = null
        closeSession()
        televisionStopped = true
        journal.note("TV stopped the session stream", emptyMap())
        if (mediaItem != null && playbackState != Player.STATE_ENDED) {
            playWhenReady = false
        }
        invalidateState()
    }

    // A TV that stopped answering is lost; one that never played the stream gets titles one stream each.
    private fun onSessionError(error: Exception) {
        val resumeSeconds = currentPositionMilliseconds() / 1000.0
        sessionJob = null
        closeSession()
        if (error is TelevisionUnreachableException) {
            failIfPlaying(error)
            return
        }
        sessionsFailed = true
        journal.note("TV session failed; casting titles one stream each", mapOf("session_error" to error.toString()))
        ErrorReporting.capture(error, "dlna.session", mapOf("cast_id" to journal.castId, "tv_name" to renderer.name, "tv_exchanges" to journal.text()))
        restartTitleOutsideSession(resumeSeconds)
    }

    // Called from the encoder's thread. The TV gets the title's plain stream from where it is now.
    private fun onSessionJobFailed(job: SessionJob, error: Exception) {
        scope.launch {
            val current = session ?: return@launch
            if (current.currentJob !== job) {
                return@launch
            }
            overlaysFailed = true
            journal.note("Drawing into the picture failed; casting the plain stream", mapOf("overlay_error" to error.toString()))
            ErrorReporting.capture(error, "dlna.overlay", mapOf("cast_id" to journal.castId, "tv_name" to renderer.name, "tv_exchanges" to journal.text()))
            val resumeSeconds = currentPositionMilliseconds() / 1000.0
            endSession()
            restartTitleOutsideSession(resumeSeconds)
        }
    }

    private fun restartTitleOutsideSession(resumeSeconds: Double) {
        if (mediaItem != null && playbackState != Player.STATE_IDLE) {
            startLoad(resumeSeconds)
            return
        }
        sendCommand { client.stop() }
    }

    private fun failIfPlaying(error: Exception) {
        if (mediaItem == null) {
            return
        }
        fail(error)
    }

    private fun endSession() {
        sessionJob?.cancel()
        sessionJob = null
        closeSession()
    }

    // Ends the session's stream without stopping what follows the TV (it may be what ends it).
    private fun closeSession() {
        session?.close()
        session = null
        sessionTitle = null
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
                "media_session" to (session != null),
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
            val transport = client.transportInfo()
            val position = client.position()
            pollFailures = 0
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
