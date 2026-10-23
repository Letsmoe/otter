package dev.neoworks.otter.ui.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import com.apollographql.apollo.api.Optional
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.ServerSettings
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.graphql.PlayerSubtitlesQuery
import dev.neoworks.otter.graphql.fragment.ProfileFields
import dev.neoworks.otter.graphql.fragment.SubtitleFields
import dev.neoworks.otter.graphql.type.SubtitleSize
import dev.neoworks.otter.profiles.captionLanguage
import kotlinx.coroutines.CancellationException

/** Subtitle files handed to the player per title; the backend ranks them best first. */
private const val MAX_SUBTITLES = 6

/** [vttPath] and [srtPath] are backend paths such as /api/subtitles/<id>.vtt. */
data class PlayerSubtitle(
    val id: String,
    val language: String,
    val label: String,
    val vttPath: String,
    val srtPath: String,
)

fun SubtitleFields.toPlayerSubtitle(): PlayerSubtitle {
    return PlayerSubtitle(id, language, label, vttUrl, srtUrl)
}

/** The captions the backend has for a movie or episode; empty when it has none or can't be asked. */
suspend fun loadSubtitles(mediaId: String, profileId: String): List<PlayerSubtitle> {
    return try {
        val query = PlayerSubtitlesQuery(mediaId, Optional.present(profileId))
        val subtitles = OtterApi.client.query(query).execute().dataOrThrow().subtitles
        subtitles.take(MAX_SUBTITLES).map { subtitle -> subtitle.subtitleFields.toPlayerSubtitle() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        emptyList()
    }
}

fun PlayerSubtitle.toSubtitleConfiguration(): MediaItem.SubtitleConfiguration {
    return MediaItem.SubtitleConfiguration.Builder(Uri.parse(ServerSettings.baseUrl + vttPath))
        .setId(id)
        .setMimeType(MimeTypes.TEXT_VTT)
        .setLanguage(language)
        .setLabel(label)
        .setSelectionFlags(0)
        .build()
}

/** Whether a track of the player is this subtitle: local tracks carry its id, Chromecast tracks its file URL. */
fun PlayerSubtitle.matchesTrackId(trackId: String?): Boolean {
    if (trackId.isNullOrEmpty()) {
        return false
    }
    return trackId == id || trackId.endsWith(vttPath)
}

/** What the active profile wants shown when captions start: its language, or nothing when captions are off. */
fun captionSelection(profile: ProfileFields?, base: TrackSelectionParameters): TrackSelectionParameters {
    val builder = base.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
    if (profile == null || !profile.subtitlesEnabled) {
        return builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }
    return builder
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .setPreferredTextLanguage(profile.captionLanguage())
        .build()
}

fun applyCaptionSelection(player: Player, profile: ProfileFields?) {
    player.trackSelectionParameters = captionSelection(profile, player.trackSelectionParameters)
}

/** Text size as a fraction of the video height; MEDIUM is the SubtitleView default. */
fun subtitleTextFraction(size: SubtitleSize): Float {
    return when (size) {
        SubtitleSize.SMALL -> 0.045f
        SubtitleSize.LARGE -> 0.07f
        else -> 0.0533f
    }
}
