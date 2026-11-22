package com.letsmoe.otter.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.letsmoe.otter.ui.title.TrailerStreamResolver
import com.letsmoe.otter.ui.title.TrailerStreams
import com.letsmoe.otter.ui.title.toMediaSource

/**
 * A title's YouTube trailer, played once with sound, cropped to fill [modifier] unless
 * [contentScale] says otherwise. [onReady] says the first frame is there, [onEnded] that it
 * finished; it pauses while the app is in the background.
 */
@Composable
fun PreviewTrailer(
    trailerKey: String,
    onReady: () -> Unit,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnEnded by rememberUpdatedState(onEnded)
    val streams by produceState<TrailerStreams?>(null, trailerKey) {
        value = TrailerStreamResolver.resolve(trailerKey)
    }
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = currentOnReady()

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    currentOnEnded()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(streams) {
        val resolved = streams ?: return@LaunchedEffect
        player.setMediaSource(resolved.toMediaSource())
        player.playWhenReady = true
        player.prepare()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { player.play() }
    ContentFrame(player, modifier, surfaceType = SURFACE_TYPE_TEXTURE_VIEW, contentScale = contentScale)
}
