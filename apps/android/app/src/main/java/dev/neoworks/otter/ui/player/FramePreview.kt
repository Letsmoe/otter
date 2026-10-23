package dev.neoworks.otter.ui.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

// The preview seeks at most this often while scrubbing.
private const val FRAME_PREVIEW_MILLISECONDS = 120L

/** A muted second player that shows the frame under the finger while scrubbing; created on first use. */
class FramePreview(private val context: Context) {

    private val lazyPlayer = lazy { ExoPlayer.Builder(context).build().apply { volume = 0f } }
    val player: ExoPlayer
        get() = lazyPlayer.value

    private var loadedUrl: String? = null
    private var lastSeekAt = 0L

    fun show(source: PlayingSource, seconds: Double) {
        val now = System.currentTimeMillis()
        if (now - lastSeekAt < FRAME_PREVIEW_MILLISECONDS) {
            return
        }
        lastSeekAt = now
        if (loadedUrl != source.url) {
            loadedUrl = source.url
            player.setMediaItem(source.toMediaItem())
            player.prepare()
        }
        player.seekTo((seconds * 1000).toLong())
    }

    fun release() {
        if (lazyPlayer.isInitialized()) {
            player.release()
        }
    }
}
