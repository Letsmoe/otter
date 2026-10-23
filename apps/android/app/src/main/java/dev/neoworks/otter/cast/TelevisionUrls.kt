package dev.neoworks.otter.cast

import android.net.Uri
import android.util.Base64
import dev.neoworks.otter.backend.EmbeddedBackend

/**
 * Backend paths a DLNA TV plays, for a URL the phone's player plays. Every
 * path stays on the backend's stream proxy, so the TV never contacts a CDN.
 */
object TelevisionUrls {

    private const val MANIFEST_PATH = "/api/stream/manifest"
    private const val MP4_PATH = "/api/stream/mp4"
    private const val BLOB_PATH = "/api/blob/"

    /** An HLS stream as one continuous file starting near [startSeconds]. */
    fun continuousPath(playerUrl: String, startSeconds: Double): String? {
        if (!playerUrl.startsWith(EmbeddedBackend.BASE_URL)) {
            return null
        }
        val uri = Uri.parse(playerUrl)
        var session = ""
        var encodedUrl = encodeUrl(playerUrl)
        if (uri.path == MANIFEST_PATH) {
            session = uri.getQueryParameter("s").orEmpty()
            encodedUrl = uri.getQueryParameter("u") ?: return null
        }
        return "/api/stream/continuous?s=$session&u=$encodedUrl&start=${startSeconds.toLong()}"
    }

    /** A progressive file the TV reads with byte ranges, without redirects. */
    fun filePath(playerUrl: String): String? {
        if (!playerUrl.startsWith(EmbeddedBackend.BASE_URL)) {
            return null
        }
        val uri = Uri.parse(playerUrl)
        val path = uri.encodedPath.orEmpty()
        if (path == MP4_PATH) {
            return "$path?${uri.encodedQuery}&direct=1"
        }
        if (path.startsWith(BLOB_PATH)) {
            return path
        }
        return null
    }

    // Matches Go's base64.RawURLEncoding (encURL in the stream proxy).
    private fun encodeUrl(url: String): String {
        return Base64.encodeToString(url.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /** Asks the stream server to announce the caption file with the stream (Samsung's CaptionInfo.sec header). */
    fun withSubtitle(path: String, subtitleId: String?): String {
        if (subtitleId == null) {
            return path
        }
        var separator = "?"
        if (path.contains("?")) {
            separator = "&"
        }
        return "$path${separator}subtitle=${Uri.encode(subtitleId)}"
    }
}
