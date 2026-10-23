package dev.neoworks.otter.ui.title

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

private const val MAX_TRAILER_HEIGHT = 720

/** A playable trailer: [audioUrl] is null when [videoUrl] already carries the sound (or is an HLS manifest). */
data class TrailerStreams(val videoUrl: String, val audioUrl: String?, val isHls: Boolean = false)

/** Resolves YouTube keys to stream URLs with NewPipeExtractor, caching results (including misses) in memory. */
object TrailerStreamResolver {
    private val cache = ConcurrentHashMap<String, java.util.Optional<TrailerStreams>>()
    private var initialized = false

    suspend fun resolve(trailerKey: String): TrailerStreams? {
        val cached = cache[trailerKey]
        if (cached != null) {
            return cached.orElse(null)
        }
        val streams = withContext(Dispatchers.IO) { extractQuietly(trailerKey) }
        cache[trailerKey] = java.util.Optional.ofNullable(streams)
        return streams
    }

    private fun extractQuietly(trailerKey: String): TrailerStreams? {
        try {
            ensureInitialized()
            return pickStreams(StreamInfo.getInfo("https://www.youtube.com/watch?v=$trailerKey"))
        } catch (exception: Exception) {
            return null
        }
    }

    @Synchronized
    private fun ensureInitialized() {
        if (initialized) {
            return
        }
        NewPipe.init(OkHttpDownloader(OkHttpClient()), Localization.DEFAULT)
        initialized = true
    }

    private fun pickStreams(info: StreamInfo): TrailerStreams? {
        val muxed = bestVideo(info.videoStreams)
        if (muxed != null) {
            return TrailerStreams(muxed.content, null)
        }
        val videoOnly = bestVideo(info.videoOnlyStreams)
        val audio = bestAudio(info.audioStreams)
        if (videoOnly != null && audio != null) {
            return TrailerStreams(videoOnly.content, audio.content)
        }
        if (info.hlsUrl.isNotEmpty()) {
            return TrailerStreams(info.hlsUrl, null, isHls = true)
        }
        return null
    }

    private fun bestVideo(streams: List<VideoStream>): VideoStream? {
        return streams
            .filter { it.isUrl && it.height in 1..MAX_TRAILER_HEIGHT }
            .maxByOrNull { it.height }
    }

    private fun bestAudio(streams: List<AudioStream>): AudioStream? {
        return streams.filter { it.isUrl }.maxByOrNull { it.averageBitrate }
    }
}
