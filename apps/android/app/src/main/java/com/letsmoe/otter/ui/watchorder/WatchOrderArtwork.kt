package com.letsmoe.otter.ui.watchorder

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.letsmoe.otter.ui.theme.OtterColors
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Where the artwork gets its images from; swapped in previews and tests. */
fun interface ArtworkImageSource {
    /** The decoded image at [url], or null when it can't be loaded. */
    suspend fun load(context: Context, url: String, size: Size): Bitmap?
}

private object CoilImageSource : ArtworkImageSource {
    override suspend fun load(context: Context, url: String, size: Size): Bitmap? {
        val request = ImageRequest.Builder(context).data(url).size(size).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request)
        if (result !is SuccessResult) {
            return null
        }
        return result.image.toBitmap()
    }
}

val LocalArtworkImageSource = compositionLocalOf<ArtworkImageSource> { CoilImageSource }

private val backdropSize = Size(1280, 720)
private val posterSize = Size(300, 450)
private val artworkShape = RoundedCornerShape(12.dp)

/**
 * A franchise's watch-order artwork, drawn by [WatchOrderArtworkPainter] from the franchise's
 * backdrop and the films' posters. Shows the typography at once and the images as they arrive.
 */
@Composable
fun WatchOrderArtwork(guide: WatchOrderGuide, modifier: Modifier = Modifier, rounded: Boolean = true) {
    val context = LocalContext.current
    val source = LocalArtworkImageSource.current
    val painter = remember { WatchOrderArtworkPainter() }
    val images by produceState(ArtworkImages(null, emptyMap()), guide.imageUrls()) {
        value = loadArtworkImages(context, source, guide)
    }
    var shaped = modifier
    if (rounded) {
        shaped = modifier.clip(artworkShape)
    }
    Canvas(shaped.aspectRatio(ARTWORK_WIDTH / ARTWORK_HEIGHT).semantics { contentDescription = "Watch order of ${guide.headline}" }) {
        drawIntoCanvas { canvas -> painter.draw(canvas.nativeCanvas, guide, images, size.width) }
    }
}

/** A film's poster as a thumbnail, from the same image source as the artwork. */
@Composable
fun ArtworkPoster(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val source = LocalArtworkImageSource.current
    val bitmap by produceState<Bitmap?>(null, url) { value = loadOrNull(context, source, url, posterSize) }
    Box(modifier.background(OtterColors.secondary)) {
        bitmap?.let { poster ->
            Image(poster.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** What the artwork loads: the backdrop and the posters that fit the strip. */
private fun WatchOrderGuide.imageUrls(): List<String?> = listOf(backdropUrl) + parts.take(MAX_POSTERS).map { part -> part.posterUrl }

private suspend fun loadArtworkImages(context: Context, source: ArtworkImageSource, guide: WatchOrderGuide): ArtworkImages =
    coroutineScope {
        val backdrop = async { loadOrNull(context, source, guide.backdropUrl, backdropSize) }
        val posters = guide.parts.take(MAX_POSTERS).mapIndexed { index, part ->
            async { index to loadOrNull(context, source, part.posterUrl, posterSize) }
        }
        val loaded = posters.awaitAll().mapNotNull { (index, bitmap) -> bitmap?.let { index to it } }
        ArtworkImages(backdrop.await(), loaded.toMap())
    }

private suspend fun loadOrNull(context: Context, source: ArtworkImageSource, url: String?, size: Size): Bitmap? {
    if (url == null) {
        return null
    }
    return source.load(context, url, size)
}

/** Provides [source] to the artwork inside [content]; for previews and tests without a network. */
@Composable
fun WithArtworkImages(source: ArtworkImageSource, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalArtworkImageSource provides source, content = content)
}
