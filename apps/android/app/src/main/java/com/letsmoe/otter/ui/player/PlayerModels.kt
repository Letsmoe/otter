package com.letsmoe.otter.ui.player

import com.letsmoe.otter.settings.PlaybackSettings
import com.letsmoe.otter.together.TogetherPeer

/** What opens the player: a movie or episode, optionally on one link, or a finished download. */
/** [startSeconds] overrides the saved resume position, e.g. when playback moves from the phone to a TV. */
data class PlayerRequest(val mediaId: String, val streamId: String? = null, val downloadId: String? = null, val startSeconds: Double? = null)

/**
 * [details] is resolution, language and measured speed, e.g. "1080p · de · ~2.4 MB/s".
 * [playable] is the backend's last check: null while untested, false when it didn't deliver video.
 */
data class PlayerSource(val id: String, val label: String, val details: String, val plugin: String, val playable: Boolean?)

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
    /** Plugins are still being asked for links. */
    val searchingSources: Boolean = true,
    val currentSourceId: String? = null,
    /** The link the user just picked, still resolving; the sources sheet marks it instead of the playing one. */
    val chosenSourceId: String? = null,
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
    /** Even volume on this phone (and in the sound it makes for a TV). */
    val evenVolume: Boolean = false,
    /** Nothing plays after the episode playing now. */
    val stopsAfterEpisode: Boolean = false,
    /** The profile's caption size and style. */
    val captionLook: CaptionLook = CaptionLook.DEFAULT,
    /** How far the TV's sound is moved against the picture (positive: later); kept per TV. */
    val audioDelayMillis: Int = 0,
) {
    /** The sound is the phone's own, so it can be moved against the picture: a DLNA TV with the drawn picture. */
    val canDelayAudio: Boolean
        get() = drawsOnTelevision

    val shownError: String?
        get() = loadError ?: playbackError

    val hasTrackChoice: Boolean
        get() = audioTracks.size > 1 || subtitleTracks.isNotEmpty()

    /** The phone processes the audio itself, or re-encodes it for a TV; a Chromecast plays the stream directly. */
    val canBoostVoice: Boolean
        get() = !isCasting || drawsOnTelevision

    /** Caption timing and a second language: the phone's own captions, or the ones it draws for a TV. */
    val canTweakCaptions: Boolean
        get() = !isCasting || drawsOnTelevision

    // A DLNA TV gets the phone's re-encoded picture and sound unless that is switched off.
    private val drawsOnTelevision: Boolean
        get() = castsToTelevision && PlaybackSettings.televisionOverlays

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
