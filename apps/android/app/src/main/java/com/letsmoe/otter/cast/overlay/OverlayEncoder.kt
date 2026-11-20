package com.letsmoe.otter.cast.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.muxer.FragmentedMp4Muxer
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.MuxerUtil
import androidx.media3.common.Metadata
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Short fragments reach the TV sooner after a (re)start.
private const val FRAGMENT_MILLISECONDS = 1000L
// The TV sits on the phone's Wi-Fi: picture quality matters more than size.
private const val VIDEO_BITRATE = 8_000_000

/**
 * Re-encodes one overlay job with Media3 Transformer: the hardware decoder
 * reads the title from the backend on loopback, [OverlayPainter] draws into
 * every frame, the hardware encoder writes H.264, and the audio is copied
 * (AAC) or converted to AAC. The result is fragmented MP4, written as it is
 * made; a TV that stops reading holds the encoder back.
 */
@UnstableApi
class OverlayEncoder(
    private val context: Context,
    private val job: OverlayJob,
    private val videoBitrate: Int = VIDEO_BITRATE,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Encodes until the title ends; throws when encoding or [output] fails. */
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
            transformer.start(editedMediaItem(), placeholderPath())
            // Transformer only takes calls on its own looper, the main one.
            continuation.invokeOnCancellation { mainHandler.post { transformer.cancel() } }
        }
    }

    private fun buildTransformer(output: OutputStream, listener: Transformer.Listener): Transformer {
        val decoderFactory = DefaultDecoderFactory.Builder(context).build()
        val assetLoaderFactory = ExoPlayerAssetLoader.Factory(context, decoderFactory, Clock.DEFAULT, null, trackSelectorFactory(), null, null)
        val encoderSettings = VideoEncoderSettings.Builder().setBitrate(videoBitrate).build()
        val encoderFactory = DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(encoderSettings).build()
        return Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAssetLoaderFactory(assetLoaderFactory)
            .setEncoderFactory(encoderFactory)
            .setMuxerFactory(StreamMuxerFactory(output))
            // A paused TV stops reading for as long as it likes.
            .setMaxDelayBetweenMuxerSamplesMs(C.TIME_UNSET)
            .addListener(listener)
            .build()
    }

    // The best variant the TV takes, as the TV's continuous streams pick it.
    private fun trackSelectorFactory(): TrackSelector.Factory {
        return TrackSelector.Factory { selectorContext ->
            val selector = DefaultTrackSelector(selectorContext)
            selector.parameters = selector.buildUponParameters()
                .setForceHighestSupportedBitrate(true)
                .setMaxVideoSize(Int.MAX_VALUE, job.maxHeight)
                .setConstrainAudioChannelCountToDeviceCapabilities(false)
                .build()
            selector
        }
    }

    private fun editedMediaItem(): EditedMediaItem {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs((job.startSeconds * 1000).toLong())
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(job.sourceUrl)
            .setMimeType(job.sourceMimeType)
            .setClippingConfiguration(clipping)
            .build()
        val overlays: List<TextureOverlay> = listOf(OverlayPainter(job))
        val overlay = OverlayEffect(overlays)
        return EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), listOf(overlay)))
            .build()
    }

    // Transformer wants a path; the muxer below writes to the TV instead, so nothing is created there.
    private fun placeholderPath(): String {
        return File(context.cacheDir, "overlay-${job.id}.mp4").path
    }
}

/** Drops metadata the muxer refuses (e.g. the source's own tags), as Media3's file muxers do. */
@UnstableApi
private class SupportedMetadataMuxer(private val muxer: Muxer) : Muxer by muxer {

    override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
        if (MuxerUtil.isMetadataSupported(metadataEntry)) {
            muxer.addMetadataEntry(metadataEntry)
        }
    }
}

/** Hands Transformer a fragmented-MP4 muxer that writes to the TV's connection. */
@UnstableApi
private class StreamMuxerFactory(private val output: OutputStream) : Muxer.Factory {

    override fun create(path: String): Muxer {
        val muxer = FragmentedMp4Muxer.Builder(output).setFragmentDurationMs(FRAGMENT_MILLISECONDS).build()
        return SupportedMetadataMuxer(muxer)
    }

    // What MPEG-TS carries to every TV; other audio is converted to AAC.
    override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> {
        if (trackType == C.TRACK_TYPE_VIDEO) {
            return ImmutableList.of(MimeTypes.VIDEO_H264)
        }
        if (trackType == C.TRACK_TYPE_AUDIO) {
            return ImmutableList.of(MimeTypes.AUDIO_AAC)
        }
        return ImmutableList.of()
    }
}
