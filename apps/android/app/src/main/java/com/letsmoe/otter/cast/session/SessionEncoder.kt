package com.letsmoe.otter.cast.session

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.Clock
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.letsmoe.otter.cast.overlay.LiveEvenVolume
import com.letsmoe.otter.cast.overlay.LiveFrameOverlay
import com.letsmoe.otter.cast.overlay.LiveVoiceBoost
import com.letsmoe.otter.cast.overlay.OVERLAY_VIDEO_BITRATE
import com.letsmoe.otter.cast.overlay.OverlayJob
import com.letsmoe.otter.cast.overlay.OverlayPainter
import com.letsmoe.otter.cast.overlay.StreamMuxerFactory
import com.letsmoe.otter.cast.overlay.darkSceneLift
import com.letsmoe.otter.cast.overlay.highestVariantSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

// Every job's sound: what the backend's MPEG-TS audio track announces.
private const val SESSION_SAMPLE_RATE = 48_000
private const val SESSION_CHANNELS = 2
// The most channels a source may have; 5.1 is mixed down to stereo.
private const val MAX_SOURCE_CHANNELS = 6
private const val STILL_FRAME_RATE = 25
// Still screens end and start again after this long, so the stream never runs dry.
private const val IDLE_SCREEN_MILLISECONDS = 30 * 60 * 1000L
private const val LOADING_SCREEN_MILLISECONDS = 2 * 60 * 1000L
// A paced sample looks at the clock again at least this often, so a cancelled job stops quickly.
private const val MAX_PACING_SLEEP_MILLISECONDS = 100L

/**
 * Encodes one job of a TV session with Media3 Transformer. Every job comes
 * out alike, so the TV never sees the format change: 16:9 H.264 at the
 * session's height (the picture letterboxed into it), 48 kHz stereo AAC (a
 * silent track for pictures without sound). The muxer is paced: it writes a
 * sample only once the session's clock is less than the lead behind it.
 */
@UnstableApi
class SessionEncoder(
    private val context: Context,
    private val session: TelevisionSession,
    private val job: SessionJob,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val size = sessionPictureSize(session.pictureHeight)
    private val pacing = JobPacing(session, checkNotNull(job.startMicroseconds) { "a job is encoded once the backend took it" })

    /** Encodes until the job ends and returns where its samples end on the session's timeline. */
    suspend fun encode(output: OutputStream): Long {
        val composition = withContext(Dispatchers.IO) { composition() }
        return withContext(Dispatchers.Main) { export(composition, output) }
    }

    // Transformer only takes calls on its own looper, the main one.
    private suspend fun export(composition: Composition, output: OutputStream): Long = suspendCancellableCoroutine { continuation ->
        val transformer = buildTransformer(output, object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (continuation.isActive) {
                    continuation.resume(pacing.lastSessionMicroseconds)
                }
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(exportException)
                }
            }
        })
        transformer.start(composition, File(context.cacheDir, "tv-session-${job.id}.mp4").path)
        // The paced muxer lets go first: Transformer's cancel waits for its thread.
        continuation.invokeOnCancellation {
            pacing.cancel()
            mainHandler.post { transformer.cancel() }
        }
    }

    private fun buildTransformer(output: OutputStream, listener: Transformer.Listener): Transformer {
        val encoderSettings = VideoEncoderSettings.Builder().setBitrate(OVERLAY_VIDEO_BITRATE).build()
        val encoderFactory = DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(encoderSettings).build()
        val builder = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .setMuxerFactory(StreamMuxerFactory(output) { muxer -> PacedMuxer(muxer, pacing) })
            // Pacing and a paused TV hold samples back for as long as they like.
            .setMaxDelayBetweenMuxerSamplesMs(C.TIME_UNSET)
            .addListener(listener)
        val screen = job.screen
        if (screen is SessionScreen.Title) {
            val decoderFactory = DefaultDecoderFactory.Builder(context).build()
            val selector = highestVariantSelector(screen.overlay.maxHeight)
            builder.setAssetLoaderFactory(ExoPlayerAssetLoader.Factory(context, decoderFactory, Clock.DEFAULT, null, selector, null, null))
        }
        return builder.build()
    }

    // One sequence with sound forced on: pictures without any get silence.
    private fun composition(): Composition {
        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
            .addItem(editedMediaItem())
            .build()
        return Composition.Builder(sequence).build()
    }

    private fun editedMediaItem(): EditedMediaItem {
        val screen = job.screen
        if (screen is SessionScreen.Title) {
            return titleItem(screen.overlay)
        }
        return stillItem(screen)
    }

    private fun titleItem(overlay: OverlayJob): EditedMediaItem {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs((overlay.startSeconds * 1000).toLong())
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(overlay.sourceUrl)
            .setMimeType(overlay.sourceMimeType)
            .setClippingConfiguration(clipping)
            .build()
        val audioProcessors = listOf(LiveVoiceBoost(overlay), LiveEvenVolume(overlay)) + uniformSound()
        return EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(audioProcessors, titleEffects(overlay)))
            .build()
    }

    // The shadows are lifted before the picture is letterboxed; captions and cards go on the whole 16:9 frame.
    private fun titleEffects(overlay: OverlayJob): List<Effect> {
        val overlays: List<TextureOverlay> = listOf(OverlayPainter(overlay))
        val effects = mutableListOf<Effect>()
        if (overlay.brightensDarkScenes) {
            effects.add(darkSceneLift())
        }
        effects.add(letterbox())
        effects.add(OverlayEffect(overlays))
        return effects
    }

    private fun stillItem(screen: SessionScreen): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(stillPicture(screen)))
            .setMimeType(stillMimeType(screen))
            .setImageDurationMs(stillMilliseconds(screen))
            .build()
        val effects = mutableListOf<Effect>(letterbox())
        val overlay = stillOverlay(screen)
        if (overlay != null) {
            val overlays: List<TextureOverlay> = listOf(overlay)
            effects.add(OverlayEffect(overlays))
        }
        return EditedMediaItem.Builder(mediaItem)
            .setFrameRate(STILL_FRAME_RATE)
            .setEffects(Effects(uniformSound(), effects))
            .build()
    }

    // A browse picture was drawn by the phone already; the other screens are drawn here.
    private fun stillPicture(screen: SessionScreen): File {
        if (screen is SessionScreen.Picture) {
            return screen.imageFile
        }
        return renderScreenPicture(context, screen, size, job.id)
    }

    private fun stillMimeType(screen: SessionScreen): String {
        if (screen is SessionScreen.Picture) {
            return MimeTypes.IMAGE_JPEG
        }
        return MimeTypes.IMAGE_PNG
    }

    // What moves in a still screen: the loading spinner, the screensaver's backdrops.
    private fun stillOverlay(screen: SessionScreen): TextureOverlay? {
        if (screen is SessionScreen.Loading) {
            return SpinnerOverlay()
        }
        if (screen is SessionScreen.Screensaver) {
            return LiveFrameOverlay(screen.frames)
        }
        return null
    }

    private fun stillMilliseconds(screen: SessionScreen): Long {
        if (screen is SessionScreen.Loading) {
            return LOADING_SCREEN_MILLISECONDS
        }
        return IDLE_SCREEN_MILLISECONDS
    }

    private fun letterbox(): Presentation {
        return Presentation.createForWidthAndHeight(size.width, size.height, Presentation.LAYOUT_SCALE_TO_FIT)
    }
}

/** Mixes any source down to stereo and resamples it to 48 kHz. */
@UnstableApi
private fun uniformSound(): List<AudioProcessor> {
    val mixer = ChannelMixingAudioProcessor()
    for (channels in 1..MAX_SOURCE_CHANNELS) {
        mixer.putChannelMixingMatrix(ChannelMixingMatrix.createForConstantPower(channels, SESSION_CHANNELS))
    }
    val resampler = SonicAudioProcessor()
    resampler.setOutputSampleRateHz(SESSION_SAMPLE_RATE)
    return listOf(mixer, resampler)
}

/**
 * Holds a job's samples back until the session's clock is less than the lead
 * behind them. The job's first sample, whatever its timestamp, sits at
 * [jobStartMicroseconds] of the session, as the backend puts it there.
 */
class JobPacing(private val session: TelevisionSession, private val jobStartMicroseconds: Long) {

    @Volatile
    private var cancelled = false
    private var firstSampleMicroseconds: Long? = null

    /** Where the samples written so far end on the session's timeline. */
    @Volatile
    var lastSessionMicroseconds: Long = jobStartMicroseconds
        private set

    fun cancel() {
        cancelled = true
    }

    /** Waits for the sample's turn; false when the job was cancelled meanwhile and the sample is dropped. */
    fun awaitTurn(sampleMicroseconds: Long): Boolean {
        val sessionMicroseconds = jobStartMicroseconds + sampleMicroseconds - firstSample(sampleMicroseconds)
        while (!cancelled) {
            val waitMicroseconds = session.pacingWaitMicroseconds(sessionMicroseconds)
            if (waitMicroseconds <= 0) {
                lastSessionMicroseconds = max(lastSessionMicroseconds, sessionMicroseconds)
                return true
            }
            if (!sleep(min(waitMicroseconds / 1000, MAX_PACING_SLEEP_MILLISECONDS))) {
                return false
            }
        }
        return false
    }

    @Synchronized
    private fun firstSample(sampleMicroseconds: Long): Long {
        val first = firstSampleMicroseconds
        if (first != null) {
            return first
        }
        firstSampleMicroseconds = sampleMicroseconds
        return sampleMicroseconds
    }

    private fun sleep(milliseconds: Long): Boolean {
        try {
            Thread.sleep(milliseconds.coerceAtLeast(1))
            return true
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }
    }
}

/** Writes each sample only when [pacing] lets it; samples of a cancelled job are dropped. */
@UnstableApi
private class PacedMuxer(private val muxer: Muxer, private val pacing: JobPacing) : Muxer by muxer {

    override fun writeSampleData(trackId: Int, byteBuffer: ByteBuffer, bufferInfo: BufferInfo) {
        if (!pacing.awaitTurn(bufferInfo.presentationTimeUs)) {
            return
        }
        muxer.writeSampleData(trackId, byteBuffer, bufferInfo)
    }
}
