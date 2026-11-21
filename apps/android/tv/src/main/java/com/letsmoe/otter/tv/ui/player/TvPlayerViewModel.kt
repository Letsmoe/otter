package com.letsmoe.otter.tv.ui.player

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.letsmoe.otter.profiles.PreferenceChange
import com.letsmoe.otter.profiles.ProfilePreferences
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.settings.PlaybackSettings
import com.letsmoe.otter.ui.player.PlaybackPosition
import com.letsmoe.otter.ui.player.PlayerEpisode
import com.letsmoe.otter.ui.player.PlayerMedia
import com.letsmoe.otter.ui.player.PlayerSource
import com.letsmoe.otter.ui.player.PlayerSubtitle
import com.letsmoe.otter.ui.player.PlayerUiState
import com.letsmoe.otter.ui.player.PlayingSource
import com.letsmoe.otter.ui.player.ProgressReporter
import com.letsmoe.otter.ui.player.SkipJump
import com.letsmoe.otter.ui.player.SkipTracker
import com.letsmoe.otter.ui.player.StillWatching
import com.letsmoe.otter.ui.player.StreamPlayback
import com.letsmoe.otter.ui.player.StreamPlaybackState
import com.letsmoe.otter.ui.player.TrackCatalog
import com.letsmoe.otter.ui.player.TrackEntry
import com.letsmoe.otter.ui.player.UpNext
import com.letsmoe.otter.ui.player.UpNextCard
import com.letsmoe.otter.ui.player.applyCaptionSelection
import com.letsmoe.otter.ui.player.audio.EvenVolumeAudioProcessor
import com.letsmoe.otter.ui.player.audio.VoiceBoostAudioProcessor
import com.letsmoe.otter.ui.player.audio.VoiceBoostRenderersFactory
import com.letsmoe.otter.ui.player.captionLookOf
import com.letsmoe.otter.ui.player.fetchResumePosition
import com.letsmoe.otter.ui.player.isHls
import com.letsmoe.otter.ui.player.loadPlayerMedia
import com.letsmoe.otter.ui.player.loadSubtitles
import com.letsmoe.otter.ui.player.matchesTrackId
import com.letsmoe.otter.ui.player.readTrackCatalog
import com.letsmoe.otter.ui.player.selectAudioTrack
import com.letsmoe.otter.ui.player.selectSubtitleTrack
import com.letsmoe.otter.ui.player.sourceDetails
import com.letsmoe.otter.ui.player.sourceLabel
import com.letsmoe.otter.ui.player.toMediaItem
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val POSITION_POLL_MILLISECONDS = 250L
// Playback starts without captions when the backend needs longer than this to find them.
private const val SUBTITLE_WAIT_MILLISECONDS = 8_000L
// After the last episode's end, the next one starts this much later (the end screen's countdown).
private const val NEXT_EPISODE_DELAY_MILLISECONDS = 8_000L

/**
 * The TV's player: finds and resolves links (falling back to the next one when a link dies),
 * resumes where the profile stopped, loads captions, skips intros and offers the next episode.
 * Built from the phone player's shared parts, without its casting and Watch Together.
 */
@OptIn(UnstableApi::class)
class TvPlayerViewModel(application: Application, mediaId: String, private val streamId: String?) : AndroidViewModel(application) {

    private val voiceBoost = VoiceBoostAudioProcessor()
    private val evenVolume = EvenVolumeAudioProcessor().apply { enabled = PlaybackSettings.evenVolume }

    val player: ExoPlayer = ExoPlayer.Builder(application, VoiceBoostRenderersFactory(application, voiceBoost, evenVolume)).build()

    private val mutableState = MutableStateFlow(PlayerUiState(mediaId = mediaId, evenVolume = PlaybackSettings.evenVolume))
    val state: StateFlow<PlayerUiState> = mutableState

    private val mutablePosition = MutableStateFlow(PlaybackPosition())
    val position: StateFlow<PlaybackPosition> = mutablePosition

    private val streamPlayback = StreamPlayback(viewModelScope)
    private val progressReporter = ProgressReporter()
    private val skipTracker = SkipTracker()
    private val upNextOffer = UpNext(viewModelScope, ::playNext)
    private val stillWatching = StillWatching(viewModelScope, onTimeout = { setStopAfterEpisode(true) })

    /** The next episode's card while the credits run. */
    val upNext: StateFlow<UpNextCard?> = upNextOffer.card

    /** The next episode waits for the answer to "Still watching?". */
    val askingStillWatching: StateFlow<Boolean> = stillWatching.asking

    private val mutableSkipButton = MutableStateFlow<SkipJump?>(null)
    /** The intro or recap playing now, for its skip button. */
    val skipButton: StateFlow<SkipJump?> = mutableSkipButton

    private var trackCatalog = TrackCatalog.Empty
    private var subtitles: List<PlayerSubtitle> = emptyList()
    private var subtitlesLoad: Deferred<List<PlayerSubtitle>>? = null
    private var playing: PlayingSource? = null
    private var media: PlayerMedia? = null
    private var attachJob: Job? = null
    private var mediaJob: Job? = null
    private var nextEpisodeJob: Job? = null
    // Where a switched or replaced link continues; 0 uses the profile's saved position.
    private var resumeOverrideSeconds = 0.0
    // A link that never got ready is dropped as soon as another one is picked.
    private var sourceStarted = false

    private val currentMediaId: String
        get() = mutableState.value.mediaId

    private val positionSeconds: Double
        get() = player.currentPosition / 1000.0

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = onPlayerStateChanged(playbackState)

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = onPlayWhenReadyChanged(playWhenReady)

        override fun onPlayerError(error: PlaybackException) = onError(error)

        override fun onTracksChanged(tracks: Tracks) = onTracksRead(tracks)
    }

    init {
        player.addListener(playerListener)
        viewModelScope.launch { streamPlayback.state.collect(::onStreamState) }
        viewModelScope.launch { pollPosition() }
        viewModelScope.launch {
            ProfileSession.activeProfile.collect { profile ->
                voiceBoost.enabled = profile?.voiceBoost == true
                mutableState.update { current -> current.copy(voiceBoost = voiceBoost.enabled, captionLook = captionLookOf(profile)) }
            }
        }
        openMedia(mediaId, initialStreamId = streamId)
    }

    // Media ------------------------------------------------------------------

    private fun openMedia(mediaId: String, initialStreamId: String?) {
        saveProgress(force = true)
        progressReporter.reset()
        playing = null
        resumeOverrideSeconds = 0.0
        nextEpisodeJob?.cancel()
        upNextOffer.reset()
        stillWatching.episodeStarted()
        skipTracker.reset(emptyList())
        mutableSkipButton.value = null
        player.stop()
        player.clearMediaItems()
        trackCatalog = TrackCatalog.Empty
        startSubtitleLoad(mediaId)
        mutableState.update { previous -> freshState(mediaId, previous) }
        mutablePosition.value = PlaybackPosition()
        loadMediaInfo(mediaId)
        startStreams(mediaId, initialStreamId)
    }

    // What carries over to the next episode: the episode list, speed and the sound settings.
    private fun freshState(mediaId: String, previous: PlayerUiState): PlayerUiState {
        return PlayerUiState(
            mediaId = mediaId,
            speed = previous.speed,
            episodes = previous.episodes,
            voiceBoost = previous.voiceBoost,
            evenVolume = previous.evenVolume,
            captionLook = previous.captionLook,
        )
    }

    private fun loadMediaInfo(mediaId: String) {
        mediaJob?.cancel()
        mediaJob = viewModelScope.launch {
            val profileId = ProfileSession.activeProfile.value?.id ?: return@launch
            val loaded = loadPlayerMedia(mediaId, profileId) ?: return@launch
            media = loaded
            skipTracker.reset(loaded.skipSegments)
            mutableState.update { current ->
                current.copy(title = loaded.title, episodes = loaded.episodes, hasNextEpisode = loaded.nextEpisodeId != null)
            }
        }
    }

    private fun startStreams(mediaId: String, initialStreamId: String?) {
        streamPlayback.follow(mediaId)
        if (initialStreamId != null) {
            streamPlayback.playSource(initialStreamId)
            return
        }
        streamPlayback.play()
    }

    /** Another episode of the series, picked in the episode list. */
    fun openEpisode(episodeId: String) {
        if (episodeId == currentMediaId) {
            return
        }
        openMedia(episodeId, initialStreamId = null)
    }

    fun playNext() {
        val nextId = media?.nextEpisodeId ?: return
        if (stillWatching.holdsBackNext()) {
            return
        }
        openMedia(nextId, initialStreamId = null)
    }

    /** Any key on the remote: someone is watching. */
    fun remoteUsed() {
        stillWatching.touched()
    }

    fun keepWatching() {
        stillWatching.confirm()
        playNext()
    }

    fun stopWatching() {
        stillWatching.decline()
        setStopAfterEpisode(true)
    }

    private fun setStopAfterEpisode(stopping: Boolean) {
        mutableState.update { current -> current.copy(stopsAfterEpisode = stopping) }
        if (stopping) {
            upNextOffer.cancel()
            nextEpisodeJob?.cancel()
        }
    }

    // Sources ----------------------------------------------------------------

    private fun onStreamState(streamState: StreamPlaybackState) {
        val sources = streamState.streams.map { stream -> PlayerSource(stream.id, sourceLabel(stream), sourceDetails(stream), stream.plugin, stream.playable) }
        mutableState.update { current ->
            current.copy(
                sources = sources,
                searchingSources = streamState.searching,
                currentSourceId = streamState.playing?.sourceId,
                chosenSourceId = streamState.chosenId,
                loadError = streamState.error,
                statusText = statusTextOf(streamState),
            )
        }
        val newPlaying = streamState.playing
        if (newPlaying != null && newPlaying != playing) {
            playing = newPlaying
            attachSource(newPlaying)
        }
    }

    private fun statusTextOf(streamState: StreamPlaybackState): String {
        if (streamState.loadingId != null) {
            return "Loading…"
        }
        return "Finding sources…"
    }

    /** A link picked in the sources list; the playing one again only reloads after an error. */
    fun chooseSource(sourceId: String) {
        val current = mutableState.value
        if (sourceId == current.currentSourceId && current.shownError == null) {
            return
        }
        reloadSource(sourceId)
    }

    /** After an error: the failed link again where it stopped, or every link, or a new search. */
    fun retryAfterError() {
        val current = mutableState.value
        val sourceId = current.currentSourceId
        if (sourceId != null && current.playbackError != null) {
            reloadSource(sourceId)
            return
        }
        if (current.sources.isEmpty()) {
            viewModelScope.launch { streamPlayback.searchAgain() }
            return
        }
        mutableState.update { state -> state.copy(playbackError = null) }
        streamPlayback.retryAll()
    }

    private fun reloadSource(sourceId: String) {
        resumeOverrideSeconds = positionSeconds
        playing = null
        if (!sourceStarted) {
            attachJob?.cancel()
            player.stop()
        }
        mutableState.update { state -> state.copy(playbackError = null, isBuffering = true) }
        streamPlayback.switchSource(sourceId)
    }

    // A link that died in the player is replaced by the next playing one, where it stopped.
    private fun onError(error: PlaybackException) {
        if (mutableState.value.currentSourceId != null) {
            resumeOverrideSeconds = positionSeconds
            mutableState.update { current -> current.copy(isBuffering = true, playbackError = null) }
            streamPlayback.fallBack()
            return
        }
        mutableState.update { current -> current.copy(playbackError = "This link stopped playing: ${error.errorCodeName}") }
    }

    private fun attachSource(source: PlayingSource) {
        sourceStarted = false
        attachJob?.cancel()
        attachJob = viewModelScope.launch {
            val resumeSeconds = resumeSecondsFor(currentMediaId)
            subtitles = awaitSubtitles()
            applyCaptionSelection(player, ProfileSession.activeProfile.value)
            player.setMediaItem(mediaItemOf(source), (resumeSeconds * 1000).toLong())
            player.prepare()
            mutableState.update { current ->
                current.copy(hasSource = true, isBuffering = true, hasEnded = false, playbackError = null, wantPlaying = true, previewable = !source.isHls())
            }
            player.playWhenReady = true
        }
    }

    private fun mediaItemOf(source: PlayingSource): MediaItem {
        return source.toMediaItem(subtitles).buildUpon()
            .setMediaMetadata(MediaMetadata.Builder().setTitle(mutableState.value.title).build())
            .build()
    }

    private suspend fun resumeSecondsFor(mediaId: String): Double {
        if (resumeOverrideSeconds > 0) {
            val switched = resumeOverrideSeconds
            resumeOverrideSeconds = 0.0
            return switched
        }
        val profileId = ProfileSession.activeProfile.value?.id ?: return 0.0
        return fetchResumePosition(mediaId, profileId)
    }

    // Playback ---------------------------------------------------------------

    private fun onPlayerStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> mutableState.update { current -> current.copy(isBuffering = true) }
            Player.STATE_READY -> onReady()
            Player.STATE_ENDED -> onEnded()
        }
    }

    private fun onReady() {
        sourceStarted = true
        val duration = player.duration
        mutableState.update { current ->
            var durationSeconds = current.durationSeconds
            if (duration > 0) {
                durationSeconds = duration / 1000.0
            }
            current.copy(isBuffering = false, durationSeconds = durationSeconds)
        }
    }

    private fun onEnded() {
        saveProgress(force = true)
        mutableState.update { current -> current.copy(hasEnded = true, isPlaying = false) }
        if (media?.nextEpisodeId == null || mutableState.value.stopsAfterEpisode) {
            return
        }
        nextEpisodeJob?.cancel()
        nextEpisodeJob = viewModelScope.launch {
            delay(NEXT_EPISODE_DELAY_MILLISECONDS)
            playNext()
        }
    }

    private fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
        mutableState.update { current -> current.copy(isPlaying = playWhenReady, wantPlaying = playWhenReady) }
        if (!playWhenReady) {
            saveProgress(force = true)
        }
    }

    fun togglePlay() {
        if (mutableState.value.hasEnded) {
            seekTo(0.0)
        }
        player.playWhenReady = !player.playWhenReady
    }

    fun skip(seconds: Int) {
        seekTo(positionSeconds + seconds)
    }

    fun seekTo(seconds: Double) {
        val duration = mutableState.value.durationSeconds
        var target = seconds.coerceAtLeast(0.0)
        if (duration > 0) {
            target = target.coerceAtMost(duration)
        }
        player.seekTo((target * 1000).toLong())
    }

    /** Dialogue boost on or off for every title of the profile; the profile's update switches it here. */
    fun toggleVoiceBoost() {
        ProfilePreferences.change(PreferenceChange(voiceBoost = !voiceBoost.enabled))
    }

    /** Even volume on or off on this TV. */
    fun toggleEvenVolume() {
        val enabled = !evenVolume.enabled
        evenVolume.enabled = enabled
        PlaybackSettings.evenVolume = enabled
        mutableState.update { current -> current.copy(evenVolume = enabled) }
    }

    // Tracks -----------------------------------------------------------------

    private fun onTracksRead(tracks: Tracks) {
        trackCatalog = readTrackCatalog(tracks, subtitles)
        mutableState.update { current ->
            current.copy(
                audioTracks = trackCatalog.audioOptions,
                subtitleTracks = trackCatalog.subtitleOptions,
                selectedAudioIndex = trackCatalog.selectedAudioIndex,
                selectedSubtitleIndex = trackCatalog.selectedSubtitleIndex,
            )
        }
    }

    fun chooseAudio(index: Int) {
        val entry = trackCatalog.audio.getOrNull(index) ?: return
        selectAudioTrack(player, entry)
        mutableState.update { current -> current.copy(selectedAudioIndex = index) }
    }

    /** -1 turns captions off. */
    fun chooseSubtitle(index: Int) {
        val entry = trackCatalog.subtitles.getOrNull(index)
        selectSubtitleTrack(player, entry)
        mutableState.update { current -> current.copy(selectedSubtitleIndex = index) }
        rememberCaptionChoice(subtitleOf(entry))
    }

    private fun subtitleOf(entry: TrackEntry?): PlayerSubtitle? {
        if (entry == null) {
            return null
        }
        return subtitles.firstOrNull { subtitle -> subtitle.matchesTrackId(entry.formatId) }
    }

    // The next titles start the same way: on in this language, or off.
    private fun rememberCaptionChoice(subtitle: PlayerSubtitle?) {
        if (subtitle == null) {
            ProfilePreferences.change(PreferenceChange(subtitlesEnabled = false))
            return
        }
        ProfilePreferences.change(PreferenceChange(subtitlesEnabled = true, subtitleLanguage = subtitle.language))
    }

    private fun startSubtitleLoad(mediaId: String) {
        subtitlesLoad?.cancel()
        subtitles = emptyList()
        subtitlesLoad = viewModelScope.async { loadSubtitlesOfProfile(mediaId) }
    }

    private suspend fun loadSubtitlesOfProfile(mediaId: String): List<PlayerSubtitle> {
        val profile = ProfileSession.activeProfile.value ?: return emptyList()
        return loadSubtitles(mediaId, profile.id)
    }

    private suspend fun awaitSubtitles(): List<PlayerSubtitle> {
        val load = subtitlesLoad ?: return emptyList()
        return withTimeoutOrNull(SUBTITLE_WAIT_MILLISECONDS) { load.await() }.orEmpty()
    }

    // Position, skipping, up next, progress ----------------------------------

    private suspend fun pollPosition() {
        while (true) {
            val position = positionSeconds
            mutablePosition.value = PlaybackPosition(position, player.bufferedPosition / 1000.0)
            followSegments(position)
            if (player.playWhenReady) {
                saveProgress(force = false)
            }
            delay(POSITION_POLL_MILLISECONDS)
        }
    }

    private fun followSegments(positionSeconds: Double) {
        mutableSkipButton.value = skipTracker.jumpAt(positionSeconds)
        upNextOffer.follow(skipTracker.isInCredits(positionSeconds), player.isPlaying, offeredNextEpisode())
        skipIntroAt(positionSeconds)
    }

    // Only while playing: a paused or loading player keeps whatever the viewer picked.
    private fun skipIntroAt(positionSeconds: Double) {
        if (!player.isPlaying || !PlaybackSettings.skipsIntroAutomatically(false)) {
            return
        }
        val target = skipTracker.autoSkipTarget(positionSeconds) ?: return
        seekTo(target)
    }

    // None when the viewer stops after this episode: the card isn't offered.
    private fun offeredNextEpisode(): PlayerEpisode? {
        if (mutableState.value.stopsAfterEpisode) {
            return null
        }
        val loaded = media ?: return null
        val nextId = loaded.nextEpisodeId ?: return null
        return loaded.episodes.firstOrNull { episode -> episode.id == nextId }
    }

    /** The skip button: jumps to the end of the intro or recap playing now. */
    fun skipSegment() {
        val jump = mutableSkipButton.value ?: return
        mutableSkipButton.value = null
        seekTo(jump.endSeconds)
    }

    fun cancelUpNext() {
        upNextOffer.cancel()
    }

    fun playUpNextNow() {
        upNextOffer.playNow()
    }

    private fun saveProgress(force: Boolean) {
        val current = mutableState.value
        if (!current.hasSource) {
            return
        }
        progressReporter.save(current.mediaId, positionSeconds, current.durationSeconds, force)
    }

    override fun onCleared() {
        saveProgress(force = true)
        streamPlayback.stop()
        player.removeListener(playerListener)
        player.release()
    }
}
