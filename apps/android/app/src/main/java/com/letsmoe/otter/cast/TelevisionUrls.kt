package com.letsmoe.otter.cast

import android.net.Uri
import android.util.Base64
import com.letsmoe.otter.backend.EmbeddedBackend

/**
 * Backend paths a DLNA TV plays, for a URL the phone's player plays. Every
 * path stays on the backend's stream proxy, so the TV never contacts a CDN.
 */
object TelevisionUrls {

    private const val MANIFEST_PATH = "/api/stream/manifest"
    private const val MP4_PATH = "/api/stream/mp4"
    private const val BLOB_PATH = "/api/blob/"

    /** An HLS stream as one continuous file starting near [startSeconds], from a variant below [refusedHeight] when one is. */
    fun continuousPath(playerUrl: String, startSeconds: Double, refusedHeight: Int?): String? {
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
        val path = "/api/stream/continuous?s=$session&u=$encodedUrl&start=${startSeconds.toLong()}"
        if (refusedHeight == null) {
            return path
        }
        return "$path&maxHeight=${refusedHeight - 1}"
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
        return withParameter(path, "subtitle", subtitleId)
    }

    /** Asks the stream server to send the type and profile the TV was told about, when they differ from its own. */
    fun withFormat(path: String, defaultMimeType: String, format: AnnouncedFormat): String {
        var result = path
        if (format.mimeType != defaultMimeType) {
            result = withParameter(result, "dlnaType", format.mimeType)
        }
        if (format.profile != null) {
            result = withParameter(result, "dlnaProfile", format.profile)
        }
        return result
    }

    /** Tags the TV's requests with the cast, so backend traces and the phone's TV log join up. */
    fun withCast(path: String, castId: String): String {
        return withParameter(path, "cast", castId)
    }

    private fun withParameter(path: String, name: String, value: String): String {
        var separator = "?"
        if (path.contains("?")) {
            separator = "&"
        }
        return "$path$separator$name=${Uri.encode(value)}"
    }
}
