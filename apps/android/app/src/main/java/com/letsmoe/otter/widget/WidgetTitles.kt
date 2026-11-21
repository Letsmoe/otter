package com.letsmoe.otter.widget

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.ui.media.MediaRepository
import com.letsmoe.otter.ui.media.RailItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

// The widget shows this many titles side by side.
private const val WIDGET_TITLE_COUNT = 4
private const val BACKEND_WAIT_MILLISECONDS = 20_000L
// Posters are small: a widget's pictures all travel in one binder transaction.
private const val POSTER_WIDTH = 240
private const val POSTER_HEIGHT = 360

/** One continue-watching title as the widget shows it. */
class WidgetTitle(
    /** The movie, or the episode to continue; what the player opens. */
    val mediaId: String,
    val title: String,
    val subtitle: String?,
    val progress: Float?,
    val poster: Bitmap?,
)

sealed interface WidgetContentState {
    /** Nobody is logged in on the phone yet. */
    data object NoProfile : WidgetContentState
    data class Titles(val titles: List<WidgetTitle>) : WidgetContentState
}

/** What a tap on the title plays: the episode a series left off at, else the title itself. */
fun widgetPlayMediaId(item: RailItem): String {
    val episodeId = item.episodeId
    if (episodeId != null) {
        return episodeId
    }
    return item.id
}

/** The active profile's continue-watching titles with their posters; empty when the backend doesn't answer. */
suspend fun loadWidgetContent(context: Context): WidgetContentState {
    val profileId = ProfileSession.activeProfile.value?.id ?: return WidgetContentState.NoProfile
    // Started by the first widget update after the phone booted, the backend needs a moment.
    withTimeoutOrNull(BACKEND_WAIT_MILLISECONDS) { OtterApi.awaitBackend() } ?: return WidgetContentState.Titles(emptyList())
    val items = MediaRepository.continueWatching(profileId, WIDGET_TITLE_COUNT)
    return WidgetContentState.Titles(withPosters(context, items))
}

private suspend fun withPosters(context: Context, items: List<RailItem>): List<WidgetTitle> = coroutineScope {
    items.take(WIDGET_TITLE_COUNT)
        .map { item -> async { widgetTitleOf(item, loadPoster(context, item.posterUrl)) } }
        .map { poster -> poster.await() }
}

private fun widgetTitleOf(item: RailItem, poster: Bitmap?): WidgetTitle {
    return WidgetTitle(widgetPlayMediaId(item), item.title, item.subtitle, item.progress, poster)
}

private suspend fun loadPoster(context: Context, posterUrl: String?): Bitmap? {
    if (posterUrl == null) {
        return null
    }
    val request = ImageRequest.Builder(context)
        .data(posterUrl)
        .size(Size(POSTER_WIDTH, POSTER_HEIGHT))
        .allowHardware(false)
        .build()
    val result = SingletonImageLoader.get(context).execute(request)
    if (result !is SuccessResult) {
        return null
    }
    return result.image.toBitmap()
}
