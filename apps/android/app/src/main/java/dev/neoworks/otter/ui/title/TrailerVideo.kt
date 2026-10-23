package dev.neoworks.otter.ui.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW

/**
 * A YouTube trailer played natively, cropped to fill [modifier]. Nothing is shown when the stream
 * can't be resolved. [onReady] fires once the first frame is on screen.
 */
@Composable
fun TrailerVideo(
    trailerKey: String,
    looping: Boolean,
    muted: Boolean,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    onReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val streams by produceTrailerStreams(trailerKey)
    val currentOnReady by rememberUpdatedState(onReady)
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = currentOnReady()
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(streams, looping) {
        val resolved = streams
        if (resolved == null) {
            return@LaunchedEffect
        }
        player.setMediaSource(buildMediaSource(resolved))
        player.repeatMode = repeatModeFor(looping)
        player.prepare()
    }
    LaunchedEffect(muted) {
        player.volume = volumeFor(muted)
    }
    PlayWhileResumed(player, paused)
    ContentFrame(player, modifier, surfaceType = SURFACE_TYPE_TEXTURE_VIEW, contentScale = ContentScale.Crop)
}

@Composable
private fun produceTrailerStreams(trailerKey: String): State<TrailerStreams?> {
    return produceState<TrailerStreams?>(null, trailerKey) {
        value = TrailerStreamResolver.resolve(trailerKey)
    }
}

@Composable
private fun PlayWhileResumed(player: Player, paused: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                resumed = false
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                resumed = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumed, paused) {
        player.playWhenReady = resumed && !paused
    }
}

private fun repeatModeFor(looping: Boolean): Int {
    if (looping) {
        return Player.REPEAT_MODE_ALL
    }
    return Player.REPEAT_MODE_OFF
}

private fun volumeFor(muted: Boolean): Float {
    if (muted) {
        return 0f
    }
    return 1f
}

private fun buildMediaSource(streams: TrailerStreams): MediaSource {
    val dataSourceFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
    if (streams.isHls) {
        return HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(streams.videoUrl))
    }
    val factory = ProgressiveMediaSource.Factory(dataSourceFactory)
    val videoSource = factory.createMediaSource(MediaItem.fromUri(streams.videoUrl))
    if (streams.audioUrl == null) {
        return videoSource
    }
    return MergingMediaSource(videoSource, factory.createMediaSource(MediaItem.fromUri(streams.audioUrl)))
}
