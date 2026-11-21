package com.letsmoe.otter.cast.overlay

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Enough frames that TVs, which dislike a handful per second, never take it for a broken video.
private const val STILL_FRAME_RATE = 10
// A flat picture needs far fewer bits than a film.
private const val STILL_VIDEO_BITRATE = 6_000_000
private const val MICROSECONDS_PER_SECOND = 1_000_000L
private const val MILLISECONDS_PER_SECOND = 1000L

/**
 * Turns one still picture into the TV's video: Media3 Transformer holds the
 * image for the job's duration, with silent sound, and writes fragmented MP4
 * as it goes (the same shape [OverlayEncoder] makes, so the backend relays it
 * as MPEG-TS). A TV that reads slowly holds the encoder back, so a long
 * duration costs nothing until it is watched.
 */
@UnstableApi
class StillEncoder(private val context: Context, private val job: StillJob) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Encodes until the duration is over; throws when encoding or [output] fails. */
    suspend fun encode(output: OutputStream) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val transformer = buildTransformer(output, object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(exportException)
                    }
                }
            })
            transformer.start(composition(), placeholderPath())
            // Transformer only takes calls on its own looper, the main one.
            continuation.invokeOnCancellation { mainHandler.post { transformer.cancel() } }
        }
    }

    private fun buildTransformer(output: OutputStream, listener: Transformer.Listener): Transformer {
        val encoderSettings = VideoEncoderSettings.Builder().setBitrate(STILL_VIDEO_BITRATE).build()
        val encoderFactory = DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(encoderSettings).build()
        return Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .setMuxerFactory(StreamMuxerFactory(output))
            // A paused TV stops reading for as long as it likes.
            .setMaxDelayBetweenMuxerSamplesMs(C.TIME_UNSET)
            .addListener(listener)
            .build()
    }

    private fun composition(): Composition {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(job.imageFile))
            .setImageDurationMs(job.durationSeconds * MILLISECONDS_PER_SECOND)
            .build()
        val picture = EditedMediaItem.Builder(mediaItem)
            .setDurationUs(job.durationSeconds * MICROSECONDS_PER_SECOND)
            .setFrameRate(STILL_FRAME_RATE)
            .setEffects(Effects(emptyList(), videoEffects()))
            .build()
        // An image has no sound of its own: Transformer makes silence for the forced audio track.
        val sequence = EditedMediaItemSequence.Builder(picture).experimentalSetForceAudioTrack(true).build()
        return Composition.Builder(sequence).build()
    }

    private fun videoEffects(): List<Effect> {
        val liveFrames = job.liveFrames ?: return emptyList()
        val overlays: List<TextureOverlay> = listOf(LiveFrameOverlay(liveFrames))
        return listOf(OverlayEffect(overlays))
    }

    // Transformer wants a path; the muxer writes to the TV instead, so nothing is created there.
    private fun placeholderPath(): String {
        return File(context.cacheDir, "still-${job.id}.mp4").path
    }
}
