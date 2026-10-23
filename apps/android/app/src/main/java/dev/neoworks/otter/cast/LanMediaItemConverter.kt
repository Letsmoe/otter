package dev.neoworks.otter.cast

import android.content.Context
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.google.android.gms.cast.MediaQueueItem
import dev.neoworks.otter.backend.EmbeddedBackend

/**
 * Points the TV at the phone's LAN stream port instead of loopback. The TV then
 * fetches every segment from the phone, which fetches it through its VPN.
 */
class LanMediaItemConverter(private val context: Context) : MediaItemConverter {

    private val defaultConverter = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        return defaultConverter.toMediaQueueItem(forTelevision(mediaItem))
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val mediaItem = defaultConverter.toMediaItem(mediaQueueItem)
        val origin = lanStreamOrigin(context) ?: return mediaItem
        return withOrigin(mediaItem, origin, EmbeddedBackend.BASE_URL)
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
        val uri = mediaItem.localConfiguration?.uri?.toString() ?: return mediaItem
        if (!uri.startsWith(from)) {
            return mediaItem
        }
        return mediaItem.buildUpon().setUri(to + uri.removePrefix(from)).build()
    }
}
