package dev.neoworks.otter.ui.player

import dev.neoworks.otter.together.TogetherPeer

/** What opens the player: a movie or episode, optionally on one link, or a finished download. */
data class PlayerRequest(val mediaId: String, val streamId: String? = null, val downloadId: String? = null)

/** [details] is resolution, language and measured speed, e.g. "1080p · de · ~2.4 MB/s". */
data class PlayerSource(val id: String, val label: String, val details: String, val plugin: String)

data class PlayerEpisode(
    val id: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String?,
    val overview: String?,
    val stillUrl: String?,
    /** Watch fraction (0..1); null when unwatched. */
    val progress: Float?,
)

data class TrackOption(val index: Int, val label: String)

data class PlayerUiState(
    val mediaId: String,
    val title: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val hasEnded: Boolean = false,
    /** True once a playable link was handed to the player. */
    val hasSource: Boolean = false,
    /** A failure from finding or resolving a source, before playback. */
    val loadError: String? = null,
    /** A failure of the player itself. */
    val playbackError: String? = null,
    val statusText: String = "Finding sources…",
    val durationSeconds: Double = 0.0,
    val speed: Float = 1f,
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val selectedAudioIndex: Int = 0,
    /** -1 = subtitles off. */
    val selectedSubtitleIndex: Int = -1,
    val sources: List<PlayerSource> = emptyList(),
    val currentSourceId: String? = null,
    /** Direct files can show a frame preview while scrubbing; HLS can't. */
    val previewable: Boolean = false,
    val episodes: List<PlayerEpisode> = emptyList(),
    val hasNextEpisode: Boolean = false,
    /** Watch Together: peers still loading this title. */
    val waitingFor: List<TogetherPeer> = emptyList(),
    /** Whether the group (or this user) wants playback running. */
    val wantPlaying: Boolean = true,
    /** The TV playing this, while casting. */
    val castDeviceName: String? = null,
    /** Casting goes to a DLNA TV (picked in the TV sheet), not a Chromecast. */
    val castsToTelevision: Boolean = false,
    /** Dialogue boost of the profile; it only works on the phone's own speakers. */
    val voiceBoost: Boolean = false,
    /** Caption text size as a fraction of the video height. */
    val subtitleFraction: Float = 0.0533f,
) {
    val shownError: String?
        get() = loadError ?: playbackError

    val hasTrackChoice: Boolean
        get() = audioTracks.size > 1 || subtitleTracks.isNotEmpty()

    /** The TV plays the stream directly, so the phone can't process its audio. */
    val canBoostVoice: Boolean
        get() = !isCasting

    val hasAudioSheet: Boolean
        get() = hasTrackChoice || canBoostVoice

    val isHolding: Boolean
        get() = waitingFor.isNotEmpty()

    val isCasting: Boolean
        get() = castDeviceName != null
}

data class PlaybackPosition(val positionSeconds: Double = 0.0, val bufferedSeconds: Double = 0.0)

fun formatPlayerTime(seconds: Double): String {
    if (seconds.isNaN() || seconds.isInfinite() || seconds < 0) {
        return "0:00"
    }
    val total = seconds.toLong()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    if (hours > 0) {
        return "%d:%02d:%02d".format(hours, minutes, secs)
    }
    return "%d:%02d".format(minutes, secs)
}
