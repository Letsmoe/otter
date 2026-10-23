package dev.neoworks.otter.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import dev.neoworks.otter.cast.CastKeepAliveService
import dev.neoworks.otter.cast.CastCaptionSelector
import dev.neoworks.otter.cast.CastSetup
import dev.neoworks.otter.cast.DlnaPlayer
import dev.neoworks.otter.cast.DlnaRenderer
import dev.neoworks.otter.cast.LanMediaItemConverter
import dev.neoworks.otter.cast.TelevisionSubtitleFile
import dev.neoworks.otter.downloads.DownloadItem
import dev.neoworks.otter.downloads.DownloadsRepository
import dev.neoworks.otter.graphql.fragment.ProfileFields
import dev.neoworks.otter.graphql.type.SubtitleSize
import dev.neoworks.otter.profiles.captionLanguage
import dev.neoworks.otter.profiles.PreferenceChange
import dev.neoworks.otter.profiles.ProfilePreferences
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.settings.PlaybackSettings
import dev.neoworks.otter.together.MediaPayload
import dev.neoworks.otter.together.PlayerReport
import dev.neoworks.otter.together.TogetherSession
import dev.neoworks.otter.ui.player.audio.VoiceBoostAudioProcessor
import dev.neoworks.otter.ui.player.audio.VoiceBoostRenderersFactory
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val POSITION_POLL_MILLISECONDS = 250L
// Matches the end screen's countdown.
private const val NEXT_EPISODE_DELAY_MILLISECONDS = 8000L
// Playback starts without captions when the backend needs longer than this to find them.
private const val SUBTITLE_WAIT_MILLISECONDS = 8000L

/** Owns the ExoPlayer so playback survives rotation, and everything around it: sources, resume, progress, Watch Together. */
class PlayerViewModel(application: Application, private val request: PlayerRequest) :
    AndroidViewModel(application), TogetherPlayerHost {

    private val voiceBoost = VoiceBoostAudioProcessor()

    // Plays on the phone, or on a Chromecast once one is picked in the cast menu.
    val player: Player = buildPlayer(application, voiceBoost)

    // Set while a DLNA TV plays; every control then goes to it.
    private var televisionPlayer: DlnaPlayer? = null
    private val activePlayer: Player
        get() = televisionPlayer ?: player

    // Direct files show the frame under the finger while scrubbing; created on first use.
    private val framePreview = FramePreview(application)
    val previewPlayer: ExoPlayer
        get() = framePreview.player

    private val mutableState = MutableStateFlow(PlayerUiState(mediaId = request.mediaId))
    val state: StateFlow<PlayerUiState> = mutableState

    private val mutablePosition = MutableStateFlow(PlaybackPosition())
    val position: StateFlow<PlaybackPosition> = mutablePosition

    private val streamPlayback = StreamPlayback(viewModelScope)
    private val progressReporter = ProgressReporter()
    private val togetherSync = PlayerTogetherSync(viewModelScope, this)
    private val skipTracker = SkipTracker()
    private val upNextOffer = UpNext(viewModelScope, ::playNext)

    /** The next episode's card while the credits run. */
    val upNext: StateFlow<UpNextCard?> = upNextOffer.card

    private val mutableSkipButton = MutableStateFlow<SkipJump?>(null)
    /** The intro or recap playing now, for its skip button. */
    val skipButton: StateFlow<SkipJump?> = mutableSkipButton

    private var trackCatalog = TrackCatalog.Empty
    private var subtitles: List<PlayerSubtitle> = emptyList()
    private var subtitlesLoad: Deferred<List<PlayerSubtitle>>? = null
    // The captions on screen (phone, Chromecast) or on the TV; null when off.
    private var shownSubtitle: PlayerSubtitle? = null
    private var playing: PlayingSource? = null
    private var media: PlayerMedia? = null
    private var attachJob: Job? = null
    private var mediaJob: Job? = null
    private var nextEpisodeJob: Job? = null
    private var sourceSwitchResumeSeconds = 0.0
    // One fresh link per TV failure: a dead link fails again, a new one may not.
    private var televisionLinkRenewed = false
    private var catchUpFactor = 1f

    override val currentMediaId: String
        get() = mutableState.value.mediaId

    override val positionSeconds: Double
        get() = activePlayer.currentPosition / 1000.0

    override val durationSeconds: Double
        get() = mutableState.value.durationSeconds

    override val isBuffering: Boolean
        get() = mutableState.value.isBuffering

    override val isHolding: Boolean
        get() = mutableState.value.isHolding

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = onPlayerStateChanged(playbackState)

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = onPlayWhenReadyChanged(playWhenReady)

        override fun onPlayerError(error: PlaybackException) {
            if (renewTelevisionLink(error)) {
                return
            }
            if (fallBackToAnotherSource()) {
                return
            }
            mutableState.update { current -> current.copy(playbackError = playbackErrorText(error)) }
        }

        override fun onTracksChanged(tracks: Tracks) = onTracksRead(tracks)

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) = onOutputChanged(deviceInfo)
    }

    init {
        player.addListener(playerListener)
        viewModelScope.launch { streamPlayback.state.collect(::onStreamState) }
        viewModelScope.launch { pollPosition() }
        viewModelScope.launch { followHolding() }
        viewModelScope.launch { followRemoteMedia() }
        viewModelScope.launch { ProfileSession.activeProfile.collect(::onProfileChanged) }
        togetherSync.start()
        openMedia(request.mediaId, initial = true)
    }

    // Media ------------------------------------------------------------------

    private fun openMedia(mediaId: String, initial: Boolean) {
        saveProgress(force = true)
        TogetherSession.detachPlayer(currentMediaId)
        progressReporter.reset()
        playing = null
        sourceSwitchResumeSeconds = 0.0
        nextEpisodeJob?.cancel()
        upNextOffer.reset()
        skipTracker.reset(emptyList())
        mutableSkipButton.value = null
        clearPlayers()
        trackCatalog = TrackCatalog.Empty
        startSubtitleLoad(mediaId)
        mutableState.update { fresh(mediaId, it) }
        mutablePosition.value = PlaybackPosition()
        loadMediaInfo(mediaId)
        startStreams(mediaId, initial)
    }

    // Keeps what carries over between titles: the episode list, the speed, the TV.
    private fun fresh(mediaId: String, previous: PlayerUiState): PlayerUiState {
        return PlayerUiState(
            mediaId = mediaId,
            speed = previous.speed,
            episodes = previous.episodes,
            castDeviceName = previous.castDeviceName,
            castsToTelevision = previous.castsToTelevision,
            voiceBoost = previous.voiceBoost,
            subtitleFraction = previous.subtitleFraction,
        )
    }

    private fun clearPlayers() {
        player.stop()
        player.clearMediaItems()
        televisionPlayer?.let { television ->
            television.stop()
            television.clearMediaItems()
        }
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
            announceToTogether()
        }
    }

    private fun startStreams(mediaId: String, initial: Boolean) {
        val download = downloadFor(initial)
        if (download != null) {
            streamPlayback.stop()
            val localUrl = download.url.orEmpty()
            mutableState.update { current -> current.copy(title = downloadTitle(download)) }
            streamPlayback.playUrl(localUrl, download.mimeType.orEmpty())
            return
        }
        streamPlayback.follow(mediaId)
        val shared = TogetherSession.takeRemoteMedia(mediaId)
        if (shared != null) {
            streamPlayback.playShared(shared.source)
            return
        }
        val streamId = request.streamId
        if (initial && streamId != null) {
            streamPlayback.playSource(streamId)
            return
        }
        streamPlayback.play()
    }

    private fun downloadFor(initial: Boolean): DownloadItem? {
        val downloadId = request.downloadId
        if (!initial || downloadId == null) {
            return null
        }
        return DownloadsRepository.findById(downloadId)
    }

    private fun downloadTitle(item: DownloadItem): String {
        val episode = item.episode ?: return item.titleName
        return "${item.titleName} · ${episodeCode(episode.seasonNumber, episode.episodeNumber)} ${episode.name.orEmpty()}".trim()
    }

    fun openEpisode(episodeId: String) {
        if (episodeId == currentMediaId) {
            return
        }
        openMedia(episodeId, initial = false)
    }

    fun playNext() {
        val nextId = media?.nextEpisodeId ?: return
        openMedia(nextId, initial = false)
    }

    /** The end screen's button or countdown; ignored once the next episode already started. */
    fun playNextAfterEnd() {
        if (!mutableState.value.hasEnded) {
            return
        }
        playNext()
    }

    // Sources ----------------------------------------------------------------

    private fun onStreamState(streamState: StreamPlaybackState) {
        val sources = streamState.streams.map { stream -> PlayerSource(stream.id, sourceLabel(stream), sourceDetails(stream), stream.plugin) }
        mutableState.update { current ->
            current.copy(
                sources = sources,
                currentSourceId = streamState.playing?.sourceId,
                loadError = streamState.error,
                statusText = statusTextFor(streamState),
            )
        }
        val newPlaying = streamState.playing
        if (newPlaying != null && newPlaying != playing) {
            playing = newPlaying
            attachSource(newPlaying)
        }
    }

    private fun statusTextFor(streamState: StreamPlaybackState): String {
        if (streamState.loadingId != null) {
            return "Loading…"
        }
        return "Finding sources…"
    }

    /**
     * A link that died in the player is replaced by the next playing one, where it stopped.
     * The DLNA TV renews its own link; downloaded files have no other links.
     */
    private fun fallBackToAnotherSource(): Boolean {
        if (televisionPlayer != null || mutableState.value.currentSourceId == null) {
            return false
        }
        sourceSwitchResumeSeconds = positionSeconds
        mutableState.update { current -> current.copy(isBuffering = true, playbackError = null) }
        streamPlayback.fallBack()
        return true
    }

    fun chooseSource(sourceId: String) {
        if (sourceId == mutableState.value.currentSourceId) {
            return
        }
        // Continue the new link where the old one was.
        sourceSwitchResumeSeconds = positionSeconds
        streamPlayback.switchSource(sourceId)
    }

    private fun attachSource(source: PlayingSource) {
        attachJob?.cancel()
        attachJob = viewModelScope.launch {
            val resumeSeconds = resumeSecondsFor(currentMediaId)
            subtitles = awaitSubtitles()
            val mediaItem = source.toMediaItem(subtitles).buildUpon()
                .setMediaMetadata(MediaMetadata.Builder().setTitle(mutableState.value.title).build())
                .build()
            shownSubtitle = defaultSubtitle()
            applyCaptionSelection(player, ProfileSession.activeProfile.value)
            loadIntoPlayers(mediaItem, (resumeSeconds * 1000).toLong())
            mutableState.update { current ->
                current.copy(hasSource = true, isBuffering = true, hasEnded = false, playbackError = null, wantPlaying = true, previewable = !source.isHls())
            }
            applyPlaybackRate()
            applyIntent()
            announceToTogether()
        }
    }

    // The phone's player always holds the title, so stopping the TV resumes it; it only loads while it plays.
    private fun loadIntoPlayers(mediaItem: MediaItem, startMilliseconds: Long) {
        player.setMediaItem(mediaItem, startMilliseconds)
        val television = televisionPlayer
        if (television == null) {
            player.prepare()
            return
        }
        television.setMediaItem(mediaItem, startMilliseconds)
        television.showSubtitle(televisionSubtitleFile())
        television.prepare()
        showTelevisionTracks()
    }

    private suspend fun resumeSecondsFor(mediaId: String): Double {
        if (sourceSwitchResumeSeconds > 0) {
            val switched = sourceSwitchResumeSeconds
            sourceSwitchResumeSeconds = 0.0
            return switched
        }
        val profileId = ProfileSession.activeProfile.value?.id ?: return 0.0
        return fetchResumePosition(mediaId, profileId)
    }

    // Playback ---------------------------------------------------------------

    private fun onPlayerStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> setBuffering(true)
            Player.STATE_READY -> onReady()
            Player.STATE_ENDED -> onEnded()
        }
    }

    private fun setBuffering(buffering: Boolean) {
        mutableState.update { current -> current.copy(isBuffering = buffering) }
        togetherSync.reportLoading(buffering || !mutableState.value.hasSource)
    }

    private fun onReady() {
        televisionLinkRenewed = false
        val duration = activePlayer.duration
        if (duration > 0) {
            mutableState.update { current -> current.copy(durationSeconds = duration / 1000.0) }
        }
        setBuffering(false)
    }

    private fun onEnded() {
        saveProgress(force = true)
        mutableState.update { current -> current.copy(hasEnded = true, isPlaying = false) }
        continueWithNextEpisode()
    }

    // The end screen counts down in animation frames, which stop while the phone is locked;
    // the next episode starts regardless, on the phone or on a TV.
    private fun continueWithNextEpisode() {
        if (media?.nextEpisodeId == null) {
            return
        }
        nextEpisodeJob?.cancel()
        nextEpisodeJob = viewModelScope.launch {
            delay(NEXT_EPISODE_DELAY_MILLISECONDS)
            playNextAfterEnd()
        }
    }

    private fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
        if (playWhenReady) {
            mutableState.update { current -> current.copy(isPlaying = true, wantPlaying = true, hasEnded = false) }
            return
        }
        mutableState.update { current -> current.copy(isPlaying = false, wantPlaying = keepsWantingToPlay(current)) }
        saveProgress(force = true)
    }

    // A pause while Watch Together holds is ours; any other pause is the user's.
    private fun keepsWantingToPlay(current: PlayerUiState): Boolean {
        if (current.isHolding) {
            return current.wantPlaying
        }
        return false
    }

    private fun applyIntent() {
        val current = mutableState.value
        if (!current.hasSource) {
            return
        }
        if (!current.wantPlaying || current.isHolding) {
            activePlayer.playWhenReady = false
            return
        }
        if (!activePlayer.playWhenReady && !current.hasEnded) {
            activePlayer.playWhenReady = true
        }
    }

    // While Watch Together holds, the video is paused but may be meant to play.
    private fun intendedPlaying(): Boolean {
        val current = mutableState.value
        if (current.isHolding || !current.hasSource) {
            return current.wantPlaying
        }
        return activePlayer.playWhenReady
    }

    fun togglePlay() {
        val shouldPlay = !intendedPlaying()
        mutableState.update { current -> current.copy(wantPlaying = shouldPlay) }
        applyIntent()
        togetherSync.announceLocalAction()
    }

    fun skip(seconds: Int) {
        seekToSeconds(positionSeconds + seconds)
        togetherSync.announceLocalAction()
    }

    fun seekTo(seconds: Double) {
        seekToSeconds(seconds)
        togetherSync.announceLocalAction()
    }

    override fun seekToSeconds(seconds: Double) {
        val duration = durationSeconds
        var target = seconds.coerceAtLeast(0.0)
        if (duration > 0) {
            target = target.coerceAtMost(duration)
        }
        activePlayer.seekTo((target * 1000).toLong())
    }

    fun chooseSpeed(speed: Float) {
        mutableState.update { current -> current.copy(speed = speed) }
        applyPlaybackRate()
    }

    override fun setCatchUpFactor(factor: Float) {
        catchUpFactor = factor
        applyPlaybackRate()
    }

    private fun applyPlaybackRate() {
        activePlayer.setPlaybackSpeed(mutableState.value.speed * catchUpFactor)
    }

    override fun followPlaying(shouldPlay: Boolean) {
        mutableState.update { current -> current.copy(wantPlaying = shouldPlay) }
        applyIntent()
    }

    // A TV keeps playing while the phone is locked.
    fun pauseForBackground() {
        if (mutableState.value.isCasting) {
            return
        }
        activePlayer.playWhenReady = false
    }

    // Casting ------------------------------------------------------------------

    private fun onOutputChanged(deviceInfo: DeviceInfo) {
        if (televisionPlayer != null) {
            return
        }
        val application = getApplication<Application>()
        // Each output keeps its own track selection, so the profile's caption choice is handed over.
        applyCaptionSelection(player, ProfileSession.activeProfile.value)
        if (deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            CastKeepAliveService.stop(application)
            mutableState.update { current -> current.copy(castDeviceName = null) }
            return
        }
        val deviceName = CastSetup.connectedDeviceName(application) ?: "TV"
        CastKeepAliveService.start(application, deviceName)
        mutableState.update { current -> current.copy(castDeviceName = deviceName) }
    }

    /** Moves playback to a DLNA TV, where it is right now. */
    fun playOnTelevision(renderer: DlnaRenderer) {
        stopTelevision()
        val application = getApplication<Application>()
        val mediaItem = player.currentMediaItem ?: return
        val positionMilliseconds = player.currentPosition
        // Unhooked first: pausing the phone's player isn't the user pausing.
        player.removeListener(playerListener)
        player.playWhenReady = false
        // Stopped, not just paused: a paused player keeps buffering from the same source.
        player.stop()
        val television = DlnaPlayer(application, renderer)
        televisionLinkRenewed = false
        television.addListener(playerListener)
        televisionPlayer = television
        mutableState.update { current ->
            current.copy(castDeviceName = renderer.name, castsToTelevision = true, playbackError = null, isBuffering = true)
        }
        CastKeepAliveService.start(application, renderer.name)
        television.setMediaItem(mediaItem, positionMilliseconds)
        television.showSubtitle(televisionSubtitleFile())
        television.prepare()
        showTelevisionTracks()
        applyIntent()
    }

    /** Brings playback back to the phone, where the TV was. */
    fun stopTelevision() {
        val television = televisionPlayer ?: return
        val positionMilliseconds = television.currentPosition
        television.removeListener(playerListener)
        television.release()
        televisionPlayer = null
        player.addListener(playerListener)
        CastKeepAliveService.stop(getApplication())
        mutableState.update { current -> current.copy(castDeviceName = null, castsToTelevision = false, playbackError = null) }
        player.seekTo(positionMilliseconds)
        applyCaptionSelection(player, ProfileSession.activeProfile.value)
        player.prepare()
        applyIntent()
    }

    /**
     * Links carry tokens that expire or are bound to the VPN's exit IP, so after
     * a VPN reconnect the TV's link can be dead: resolve the source again and
     * continue where the TV was.
     */
    private fun renewTelevisionLink(error: PlaybackException): Boolean {
        if (televisionPlayer == null || televisionLinkRenewed) {
            return false
        }
        if (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            return false
        }
        val sourceId = mutableState.value.currentSourceId ?: return false
        televisionLinkRenewed = true
        sourceSwitchResumeSeconds = positionSeconds
        streamPlayback.switchSource(sourceId)
        return true
    }

    private fun playbackErrorText(error: PlaybackException): String {
        if (televisionPlayer == null) {
            return "This source could not be played."
        }
        return "The TV could not play this: ${error.message}"
    }

    // Tracks -----------------------------------------------------------------

    private fun onTracksRead(tracks: Tracks) {
        if (televisionPlayer != null) {
            showTelevisionTracks()
            return
        }
        trackCatalog = readTrackCatalog(tracks, subtitles)
        if (trackCatalog.subtitles.isNotEmpty()) {
            shownSubtitle = subtitleOf(trackCatalog.subtitles.getOrNull(trackCatalog.selectedSubtitleIndex))
        }
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
        selectAudioTrack(activePlayer, entry)
        mutableState.update { current -> current.copy(selectedAudioIndex = index) }
    }

    fun chooseSubtitle(index: Int) {
        val television = televisionPlayer
        if (television != null) {
            chooseTelevisionSubtitle(television, index)
            return
        }
        val entry = trackCatalog.subtitles.getOrNull(index)
        selectSubtitleTrack(activePlayer, entry)
        mutableState.update { current -> current.copy(selectedSubtitleIndex = index) }
        rememberCaptionChoice(subtitleOf(entry))
    }

    private fun chooseTelevisionSubtitle(television: DlnaPlayer, index: Int) {
        val subtitle = subtitles.getOrNull(index)
        television.showSubtitle(subtitleFile(subtitle))
        mutableState.update { current -> current.copy(selectedSubtitleIndex = index) }
        rememberCaptionChoice(subtitle)
    }

    // The TV has no tracks of its own: the caption files are the choices.
    private fun showTelevisionTracks() {
        val selectedIndex = subtitles.indexOfFirst { subtitle -> subtitle.id == shownSubtitle?.id }
        mutableState.update { current ->
            current.copy(
                audioTracks = emptyList(),
                subtitleTracks = subtitles.mapIndexed { index, subtitle -> TrackOption(index, subtitle.label) },
                selectedAudioIndex = 0,
                selectedSubtitleIndex = selectedIndex,
            )
        }
    }

    private fun subtitleOf(entry: TrackEntry?): PlayerSubtitle? {
        if (entry == null) {
            return null
        }
        return subtitles.firstOrNull { subtitle -> subtitle.matchesTrackId(entry.formatId) }
    }

    // The next titles start the same way: on in this language, or off.
    private fun rememberCaptionChoice(subtitle: PlayerSubtitle?) {
        shownSubtitle = subtitle
        if (subtitle == null) {
            ProfilePreferences.change(PreferenceChange(subtitlesEnabled = false))
            return
        }
        ProfilePreferences.change(PreferenceChange(subtitlesEnabled = true, subtitleLanguage = subtitle.language))
    }

    private fun televisionSubtitleFile(): TelevisionSubtitleFile? {
        return subtitleFile(shownSubtitle)
    }

    private fun subtitleFile(subtitle: PlayerSubtitle?): TelevisionSubtitleFile? {
        if (subtitle == null) {
            return null
        }
        return TelevisionSubtitleFile(subtitle.id, subtitle.srtPath)
    }

    // Subtitles -------------------------------------------------------------------

    private fun startSubtitleLoad(mediaId: String) {
        subtitlesLoad?.cancel()
        subtitles = emptyList()
        shownSubtitle = null
        subtitlesLoad = viewModelScope.async { loadSubtitlesOfProfile(mediaId) }
    }

    private suspend fun loadSubtitlesOfProfile(mediaId: String): List<PlayerSubtitle> {
        val profileId = ProfileSession.activeProfile.value?.id ?: return emptyList()
        return loadSubtitles(mediaId, profileId)
    }

    private suspend fun awaitSubtitles(): List<PlayerSubtitle> {
        val load = subtitlesLoad ?: return emptyList()
        return withTimeoutOrNull(SUBTITLE_WAIT_MILLISECONDS) { load.await() }.orEmpty()
    }

    // The captions the profile asks for, before the player has read any track.
    private fun defaultSubtitle(): PlayerSubtitle? {
        val profile = ProfileSession.activeProfile.value ?: return null
        if (!profile.subtitlesEnabled) {
            return null
        }
        return subtitles.firstOrNull { subtitle -> subtitle.language.equals(profile.captionLanguage(), ignoreCase = true) }
    }

    // Profile ------------------------------------------------------------------------

    private fun onProfileChanged(profile: ProfileFields?) {
        voiceBoost.enabled = profile?.voiceBoost == true
        mutableState.update { current ->
            current.copy(voiceBoost = voiceBoost.enabled, subtitleFraction = subtitleFractionOf(profile))
        }
    }

    private fun subtitleFractionOf(profile: ProfileFields?): Float {
        if (profile == null) {
            return subtitleTextFraction(SubtitleSize.MEDIUM)
        }
        return subtitleTextFraction(profile.subtitleSize)
    }

    /** Dialogue boost on or off for every title of the profile. */
    fun toggleVoiceBoost() {
        ProfilePreferences.change(PreferenceChange(voiceBoost = !voiceBoost.enabled))
    }

    // Position, progress and frame preview -------------------------------------

    private suspend fun pollPosition() {
        while (true) {
            val position = activePlayer.currentPosition / 1000.0
            val buffered = activePlayer.bufferedPosition / 1000.0
            mutablePosition.value = PlaybackPosition(position, buffered)
            followSegments(position)
            if (activePlayer.playWhenReady) {
                saveProgress(force = false)
            }
            delay(POSITION_POLL_MILLISECONDS)
        }
    }

    private fun followSegments(positionSeconds: Double) {
        mutableSkipButton.value = skipTracker.jumpAt(positionSeconds)
        upNextOffer.follow(skipTracker.isInCredits(positionSeconds), activePlayer.isPlaying, nextEpisode())
        skipIntroAt(positionSeconds)
    }

    // Only while playing: a paused or loading player keeps whatever the viewer picked.
    private fun skipIntroAt(positionSeconds: Double) {
        val current = mutableState.value
        if (!activePlayer.isPlaying || current.isHolding || !PlaybackSettings.skipsIntroAutomatically(current.isCasting)) {
            return
        }
        val target = skipTracker.autoSkipTarget(positionSeconds) ?: return
        seekTo(target)
    }

    private fun nextEpisode(): PlayerEpisode? {
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

    /** The card's Cancel: the credits keep playing. */
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

    /** Shows the frame at [seconds] in the preview player. */
    fun previewAt(seconds: Double) {
        val source = playing ?: return
        if (mutableState.value.previewable) {
            framePreview.show(source, seconds)
        }
    }

    /** Saves progress before the screen leaves. */
    fun close() {
        saveProgress(force = true)
    }

    // Watch Together ---------------------------------------------------------

    private fun announceToTogether() {
        val current = mutableState.value
        val loaded = media
        if (!current.hasSource || loaded == null) {
            return
        }
        val payload = MediaPayload(current.mediaId, loaded.route, loaded.title, streamPlayback.sharedSourceOf(playing))
        TogetherSession.attachPlayer(PlayerReport(payload, { mutableState.value.wantPlaying }, { positionSeconds }))
    }

    // Pauses while someone is still loading, resumes once everyone is ready.
    private suspend fun followHolding() {
        combine(TogetherSession.room, TogetherSession.peerStates, TogetherSession.holdOverrideMediaId, mutableState) { room, states, override, uiState ->
            TogetherSession.holdingFor(uiState.mediaId, room, states, override)
        }.distinctUntilChanged().collect { waitingFor ->
            mutableState.update { current -> current.copy(waitingFor = waitingFor) }
            applyIntent()
            togetherSync.onHoldingChanged(waitingFor.isNotEmpty())
        }
    }

    // Someone in the room moved on to another episode or title.
    private suspend fun followRemoteMedia() {
        TogetherSession.remoteMedia.collect { remote ->
            if (remote != null && remote.mediaId != currentMediaId) {
                openMedia(remote.mediaId, initial = false)
            }
        }
    }

    fun startWithoutWaiting() {
        TogetherSession.startWithoutWaiting(currentMediaId)
    }

    override fun onCleared() {
        saveProgress(force = true)
        // The TV streams from this phone; leaving the player ends the cast with it.
        if (mutableState.value.isCasting) {
            CastSetup.endSession(getApplication())
            CastKeepAliveService.stop(getApplication())
        }
        televisionPlayer?.let { television ->
            television.removeListener(playerListener)
            television.release()
            CastKeepAliveService.stop(getApplication())
        }
        TogetherSession.detachPlayer(currentMediaId)
        togetherSync.stop()
        streamPlayback.stop()
        player.removeListener(playerListener)
        player.release()
        framePreview.release()
    }
}

private fun buildPlayer(application: Application, voiceBoost: VoiceBoostAudioProcessor): Player {
    val localPlayer = ExoPlayer.Builder(application, VoiceBoostRenderersFactory(application, voiceBoost)).build()
    return try {
        val remotePlayer = RemoteCastPlayer.Builder(application)
            .setMediaItemConverter(LanMediaItemConverter(application))
            .setTrackSelector(CastCaptionSelector())
            .build()
        CastPlayer.Builder(application).setLocalPlayer(localPlayer).setRemotePlayer(remotePlayer).build()
    } catch (error: Exception) {
        // No Google Play services: no casting, plain local playback.
        localPlayer
    }
}
