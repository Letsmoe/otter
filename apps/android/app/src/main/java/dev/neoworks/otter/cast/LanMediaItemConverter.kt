package dev.neoworks.otter.cast

import android.content.Context
import android.net.Uri
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack
import dev.neoworks.otter.backend.EmbeddedBackend
import org.json.JSONObject

private const val SUBTITLE_ID_KEY = "otterSubtitleId"

/**
 * Points the TV at the phone's LAN stream port instead of loopback. The TV then
 * fetches every segment from the phone, which fetches it through its VPN.
 * Caption files travel along as the receiver's text tracks, which the default
 * converter doesn't carry.
 */
class LanMediaItemConverter(private val context: Context) : MediaItemConverter {

    private val defaultConverter = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val television = forTelevision(mediaItem)
        val queueItem = defaultConverter.toMediaQueueItem(television)
        return withTextTracks(queueItem, television.localConfiguration?.subtitleConfigurations.orEmpty())
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val mediaItem = defaultConverter.toMediaItem(mediaQueueItem)
        val origin = lanStreamOrigin(context) ?: return mediaItem
        val withSubtitles = withSubtitleConfigurations(mediaItem, mediaQueueItem.media?.mediaTracks.orEmpty())
        return withOrigin(withSubtitles, origin, EmbeddedBackend.BASE_URL)
    }

    private fun forTelevision(mediaItem: MediaItem): MediaItem {
        // Without Wi-Fi the TV can't reach the phone anyway; it reports the failure.
        val origin = lanStreamOrigin(context) ?: return mediaItem
        val rewritten = withOrigin(mediaItem, EmbeddedBackend.BASE_URL, origin)
        // The receiver can't sniff the container; untyped links are progressive files.
        if (rewritten.localConfiguration?.mimeType != null) {
            return rewritten
        }
        return rewritten.buildUpon().setMimeType(MimeTypes.VIDEO_MP4).build()
    }

    private fun withOrigin(mediaItem: MediaItem, from: String, to: String): MediaItem {
        val configuration = mediaItem.localConfiguration ?: return mediaItem
        val builder = mediaItem.buildUpon()
        val uri = configuration.uri.toString()
        if (uri.startsWith(from)) {
            builder.setUri(to + uri.removePrefix(from))
        }
        builder.setSubtitleConfigurations(configuration.subtitleConfigurations.map { subtitle -> subtitleWithOrigin(subtitle, from, to) })
        return builder.build()
    }

    private fun subtitleWithOrigin(subtitle: MediaItem.SubtitleConfiguration, from: String, to: String): MediaItem.SubtitleConfiguration {
        val uri = subtitle.uri.toString()
        if (!uri.startsWith(from)) {
            return subtitle
        }
        return subtitle.buildUpon().setUri(Uri.parse(to + uri.removePrefix(from))).build()
    }

    private fun withTextTracks(queueItem: MediaQueueItem, subtitles: List<MediaItem.SubtitleConfiguration>): MediaQueueItem {
        val info = queueItem.media
        if (subtitles.isEmpty() || info == null) {
            return queueItem
        }
        val tracks = subtitles.mapIndexed { index, subtitle -> textTrack(index + 1L, subtitle) }
        val infoBuilder = MediaInfo.Builder(info.contentId)
            .setContentType(info.contentType)
            .setStreamType(info.streamType)
            .setMetadata(info.metadata)
            .setCustomData(info.customData)
            .setMediaTracks(tracks)
        info.contentUrl?.let { contentUrl -> infoBuilder.setContentUrl(contentUrl) }
        return MediaQueueItem.Builder(infoBuilder.build()).build()
    }

    private fun textTrack(trackId: Long, subtitle: MediaItem.SubtitleConfiguration): MediaTrack {
        val customData = JSONObject().put(SUBTITLE_ID_KEY, subtitle.id)
        return MediaTrack.Builder(trackId, MediaTrack.TYPE_TEXT)
            .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
            .setContentId(subtitle.uri.toString())
            .setContentType(MimeTypes.TEXT_VTT)
            .setLanguage(subtitle.language)
            .setName(subtitle.label)
            .setCustomData(customData)
            .build()
    }

    // Brings the caption files back when a cast session hands the title back to the phone.
    private fun withSubtitleConfigurations(mediaItem: MediaItem, tracks: List<MediaTrack>): MediaItem {
        val subtitles = tracks.filter { track -> track.type == MediaTrack.TYPE_TEXT && track.contentId != null }
            .map { track -> subtitleConfiguration(track) }
        if (subtitles.isEmpty()) {
            return mediaItem
        }
        return mediaItem.buildUpon().setSubtitleConfigurations(subtitles).build()
    }

    private fun subtitleConfiguration(track: MediaTrack): MediaItem.SubtitleConfiguration {
        val subtitleId = track.customData?.optString(SUBTITLE_ID_KEY)
        return MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.contentId))
            .setId(subtitleId)
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(track.language)
            .setLabel(track.name)
            .setSelectionFlags(0)
            .build()
    }
}
