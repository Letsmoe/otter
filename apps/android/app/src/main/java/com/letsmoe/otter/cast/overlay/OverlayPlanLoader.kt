package com.letsmoe.otter.cast.overlay

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.letsmoe.otter.backend.EmbeddedBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "OtterOverlay"
private const val CAPTION_TIMEOUT_MILLISECONDS = 5000
private const val STILL_WIDTH = 640
private const val STILL_HEIGHT = 360

/** The next episode as its card in the picture shows it; it counts down from the credits. */
data class UpNextPreview(val episodeLabel: String, val stillUrl: String?, val creditsStartSeconds: Double, val countdownSeconds: Double)

/** What the player knows to draw besides captions: the title's skippable parts and what plays next. */
data class TelevisionHints(val skips: List<SkipHint> = emptyList(), val upNext: UpNextPreview? = null)

/** Loads what the picture shows: the caption file at [captionPath] (a backend path) and the next episode's still. */
suspend fun loadOverlayPlan(context: Context, hints: TelevisionHints, captionPath: String?): OverlayPlan {
    val captions = loadCaptions(captionPath)
    val upNext = hints.upNext?.let { preview -> upNextHint(context, preview) }
    return OverlayPlan(captions, hints.skips, upNext)
}

private suspend fun upNextHint(context: Context, preview: UpNextPreview): UpNextHint {
    val still = preview.stillUrl?.let { url -> loadStill(context, url) }
    return UpNextHint(preview.episodeLabel, preview.creditsStartSeconds, preview.countdownSeconds, still)
}

// Without the file the title still plays, just without captions.
private suspend fun loadCaptions(captionPath: String?): List<CaptionCue> {
    val path = captionPath ?: return emptyList()
    return try {
        parseSrt(fetchText(EmbeddedBackend.BASE_URL + path))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: IOException) {
        Log.w(TAG, "loading captions for the TV failed", error)
        emptyList()
    }
}

private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = CAPTION_TIMEOUT_MILLISECONDS
    connection.readTimeout = CAPTION_TIMEOUT_MILLISECONDS
    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("HTTP ${connection.responseCode} for $url")
        }
        connection.inputStream.bufferedReader().readText()
    } finally {
        connection.disconnect()
    }
}

private suspend fun loadStill(context: Context, url: String): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(Size(STILL_WIDTH, STILL_HEIGHT))
        .allowHardware(false)
        .build()
    val result = SingletonImageLoader.get(context).execute(request)
    if (result !is SuccessResult) {
        return null
    }
    return result.image.toBitmap()
}
