package com.letsmoe.otter.cast.overlay

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateFormat
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
import java.util.Date

private const val TAG = "OtterOverlay"
private const val CAPTION_TIMEOUT_MILLISECONDS = 5000
private const val STILL_WIDTH = 640
private const val STILL_HEIGHT = 360
private const val TITLE_CARD_SECONDS = 6.0

/** The next episode as its card in the picture shows it; it counts down from the credits. */
data class UpNextPreview(val episodeLabel: String, val stillUrl: String?, val creditsStartSeconds: Double, val countdownSeconds: Double)

/** What is playing, for the title card: "Severance" and "S1:E2 · Half Loop" (null for movies). */
data class TitleText(val title: String, val detail: String?)

/** What the player knows to draw besides captions: the title's skippable parts, what plays next and what plays now. */
data class TelevisionHints(
    val skips: List<SkipHint> = emptyList(),
    val upNext: UpNextPreview? = null,
    val titleText: TitleText? = null,
)

/** The caption files the picture shows, as backend paths: the main language and, smaller, a second one. */
data class CaptionPaths(val main: String?, val second: String?)

/** Where the title card starts on the title's timeline, and "Ends at 22:41" when the length is known. */
data class TitleCardRequest(val fromSeconds: Double, val endsAtLabel: String?)

/** Loads what the picture shows: the caption files, the next episode's still and, when asked for, the title card. */
suspend fun loadOverlayPlan(context: Context, hints: TelevisionHints, captionPaths: CaptionPaths, titleCard: TitleCardRequest?): OverlayPlan {
    val captions = loadCaptionCues(captionPaths.main)
    val secondCaptions = loadCaptionCues(captionPaths.second)
    val upNext = hints.upNext?.let { preview -> upNextHint(context, preview) }
    return OverlayPlan(captions, hints.skips, upNext, secondCaptions, titleCardOf(hints.titleText, titleCard))
}

private fun titleCardOf(text: TitleText?, request: TitleCardRequest?): TitleCard? {
    if (text == null || request == null) {
        return null
    }
    return TitleCard(text.title, text.detail, request.fromSeconds, request.fromSeconds + TITLE_CARD_SECONDS, request.endsAtLabel)
}

/** "Ends at 22:41" in the phone's clock format: now plus what is left of the title. */
fun endsAtLabel(context: Context, secondsLeft: Double): String? {
    if (secondsLeft <= 0) {
        return null
    }
    val endsAt = Date(System.currentTimeMillis() + (secondsLeft * 1000).toLong())
    return "Ends at " + DateFormat.getTimeFormat(context).format(endsAt)
}

private suspend fun upNextHint(context: Context, preview: UpNextPreview): UpNextHint {
    val still = preview.stillUrl?.let { url -> loadStill(context, url) }
    return UpNextHint(preview.episodeLabel, preview.creditsStartSeconds, preview.countdownSeconds, still)
}

/** The cues of the caption file at [captionPath] (a backend .srt path); empty without one or when it can't be read. */
suspend fun loadCaptionCues(captionPath: String?): List<CaptionCue> {
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
