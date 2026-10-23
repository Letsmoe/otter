package dev.neoworks.otter.ui.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes

fun PlayingSource.isHls(): Boolean {
    return mimeType.contains("mpegurl", ignoreCase = true) || url.contains(".m3u8")
}

fun PlayingSource.toMediaItem(): MediaItem {
    val builder = MediaItem.Builder().setUri(url)
    if (isHls()) {
        builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    } else if (mimeType.startsWith("video/")) {
        builder.setMimeType(mimeType)
    }
    return builder.build()
}
