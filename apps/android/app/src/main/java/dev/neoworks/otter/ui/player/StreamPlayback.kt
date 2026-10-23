package dev.neoworks.otter.ui.player

import com.apollographql.apollo.api.Optional
import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.api.dataOrThrow
import dev.neoworks.otter.graphql.AdoptStreamMutation
import dev.neoworks.otter.graphql.ResolveBestStreamMutation
import dev.neoworks.otter.graphql.SourceStreamsSubscription
import dev.neoworks.otter.graphql.fragment.SourceStreamFields
import dev.neoworks.otter.together.SharedSource
import dev.neoworks.otter.ui.media.TitleActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.neoworks.otter.profiles.languageRank
import dev.neoworks.otter.profiles.preferredLanguages
import dev.neoworks.otter.ui.title.formatSpeed

/** How many sources Play tries before giving up. */

data class SourceStream(
    val id: String,
    val url: String,
    val plugin: String,
    val label: String?,
    val quality: String?,
    val language: String?,
    /** Null until someone tried to play it. */
    val playable: Boolean?,
    val speedBytesPerSecond: Double?,
)

data class PlayingSource(val url: String, val mimeType: String, val sourceId: String?)

data class StreamPlaybackState(
    val streams: List<SourceStream> = emptyList(),
    val searching: Boolean = true,
    val loadingId: String? = null,
    val playing: PlayingSource? = null,
    val error: String? = null,
)

fun SourceStreamFields.toSourceStream(): SourceStream {
    return SourceStream(id, url, plugin, label, quality, language, playable, speedBps)
}

/** Known-broken links last; otherwise the profile's languages first, then known-playable before untested. */
fun rankStreams(streams: List<SourceStream>): List<SourceStream> {
    val preferred = preferredLanguages()
    return streams.sortedWith(
        compareBy<SourceStream>(
            { stream -> stream.playable == false },
            { stream -> languageRank(stream.language, preferred) },
            { stream -> playableRank(stream) },
        ),
    )
}

private fun playableRank(stream: SourceStream): Int {
    return when (stream.playable) {
        true -> 0
        null -> 1
        false -> 2
    }
}

fun sourceLabel(stream: SourceStream): String {
    if (stream.label.isNullOrEmpty()) {
        return "Link"
    }
    return stream.label
}

/** Resolution, language and measured speed, e.g. "1080p · de · ~2.4 MB/s". */
fun sourceDetails(stream: SourceStream): String {
    val parts = mutableListOf<String>()
    stream.quality?.let { quality -> parts.add(quality) }
    stream.language?.let { language -> parts.add(language) }
    formatSpeed(stream.speedBytesPerSecond)?.let { speed -> parts.add("~$speed") }
    return parts.joinToString(" · ")
}

/**
 * Stream lookup and playback for one movie or episode: plays the best link as soon as one arrives
 * and falls back to the next ones when a link is dead. Mirrors lib/playback.svelte.ts.
 */
class StreamPlayback(private val scope: CoroutineScope) {

    private val mutableState = MutableStateFlow(StreamPlaybackState())
    val state: StateFlow<StreamPlaybackState> = mutableState

    private var mediaId: String? = null
    private var autoplayAttempted = false
    // Links that failed for this title; autoplay and fallbacks skip them.
    private val failedIds = mutableSetOf<String>()
    private var subscriptionJob: Job? = null
    private var playJob: Job? = null

    /** Follows the links of one title; stored links come first, then plugin answers. */
    fun follow(newMediaId: String) {
        stop()
        mediaId = newMediaId
        mutableState.value = StreamPlaybackState()
        autoplayAttempted = false
        failedIds.clear()
        subscriptionJob = scope.launch { collectStreams(newMediaId) }
    }

    fun stop() {
        subscriptionJob?.cancel()
        playJob?.cancel()
        subscriptionJob = null
        playJob = null
    }

    private suspend fun collectStreams(followedMediaId: String) {
        try {
            OtterApi.awaitBackend()
            OtterApi.client.subscription(SourceStreamsSubscription(followedMediaId)).toFlow().collect { response ->
                val update = response.data?.streams ?: return@collect
                val streams = rankStreams(update.streams.map { stream -> stream.sourceStreamFields.toSourceStream() })
                mutableState.update { current -> current.copy(streams = streams, searching = !update.done) }
                autoplay()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            mutableState.update { current -> current.copy(searching = false) }
        }
    }

    /** Plays the best link once one is there. */
    fun play() {
        mutableState.update { current -> current.copy(playing = null, error = null) }
        autoplayAttempted = false
        autoplay()
    }

    /** Plays one chosen link. */
    fun playSource(streamId: String) {
        mutableState.update { current -> current.copy(playing = null, error = null) }
        autoplayAttempted = true
        switchSource(streamId)
    }

    /** A link that is already playable, e.g. a downloaded file. */
    fun playUrl(url: String, mimeType: String) {
        autoplayAttempted = true
        mutableState.update { current -> current.copy(playing = PlayingSource(url, mimeType, null), error = null) }
    }

    /**
     * Watch Together: plays the link someone else plays (stored here first), falling back to this
     * device's own best links when it can't be resolved.
     */
    fun playShared(source: SharedSource?) {
        val followedMediaId = mediaId
        if (source == null || followedMediaId == null) {
            play()
            return
        }
        mutableState.update { current -> current.copy(playing = null, error = null) }
        autoplayAttempted = true
        playJob?.cancel()
        playJob = scope.launch {
            if (!tryShared(followedMediaId, source)) {
                autoplayAttempted = false
                autoplay()
            }
        }
    }

    private suspend fun tryShared(followedMediaId: String, source: SharedSource): Boolean {
        return try {
            val mutation = AdoptStreamMutation(followedMediaId, source.url, source.plugin, Optional.present(source.label))
            val adopted = OtterApi.client.mutation(mutation).execute().dataOrThrow().adoptStream.sourceStreamFields.toSourceStream()
            addIfMissing(adopted)
            tryPlay(adopted)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            false
        }
    }

    private fun addIfMissing(stream: SourceStream) {
        mutableState.update { current ->
            if (current.streams.any { candidate -> candidate.id == stream.id }) {
                current
            } else {
                current.copy(streams = listOf(stream) + current.streams)
            }
        }
    }

    /** The playing link, shared with the others in Watch Together. */
    fun sharedSourceOf(playing: PlayingSource?): SharedSource? {
        val stream = mutableState.value.streams.firstOrNull { candidate -> candidate.id == playing?.sourceId } ?: return null
        return SharedSource(stream.url, stream.plugin, stream.label.orEmpty())
    }

    /**
     * The playing link failed in the player: set it aside and race the remaining links. The
     * caller keeps the position.
     */
    fun fallBack() {
        val failedId = mutableState.value.playing?.sourceId ?: return
        failedIds.add(failedId)
        mutableState.update { current -> current.copy(playing = null, error = null) }
        autoplayAttempted = false
        autoplay()
    }

    /** Switching from inside the player; the player keeps its position. */
    fun switchSource(sourceId: String) {
        val stream = mutableState.value.streams.firstOrNull { candidate -> candidate.id == sourceId } ?: return
        mutableState.update { current -> current.copy(error = null) }
        playJob?.cancel()
        playJob = scope.launch {
            if (!tryPlay(stream)) {
                mutableState.update { current -> current.copy(error = "This source could not be played.") }
            }
        }
    }

    /** Asks every plugin again; returns false when the lookup could not start. */
    suspend fun searchAgain(): Boolean {
        val followedMediaId = mediaId ?: return true
        if (mutableState.value.searching) {
            return true
        }
        mutableState.update { current -> current.copy(searching = true) }
        return try {
            TitleActions.findStreams(followedMediaId)
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            mutableState.update { current -> current.copy(searching = false) }
            false
        }
    }

    private fun autoplay() {
        val current = mutableState.value
        if (current.playing != null || autoplayAttempted) {
            return
        }
        if (current.streams.isNotEmpty()) {
            playBest()
            return
        }
        if (!current.searching) {
            mutableState.update { it.copy(error = "No sources found.") }
        }
    }

    // Races the links group by group (best language first, known-broken last); only a link
    // the backend saw deliver video wins.
    private fun playBest() {
        autoplayAttempted = true
        playJob?.cancel()
        playJob = scope.launch {
            val candidates = mutableState.value.streams.filter { stream -> stream.id !in failedIds }
            for (group in raceGroups(candidates)) {
                if (tryPlayBest(group)) {
                    return@launch
                }
                failedIds.addAll(group.map { stream -> stream.id })
            }
            mutableState.update { current -> current.copy(error = "No source could be played.") }
        }
    }

    private suspend fun tryPlay(stream: SourceStream): Boolean {
        return tryPlayBest(listOf(stream))
    }

    private suspend fun tryPlayBest(group: List<SourceStream>): Boolean {
        val resolvingMediaId = mediaId
        mutableState.update { current -> current.copy(loadingId = group.first().id) }
        try {
            val mutation = ResolveBestStreamMutation(group.map { stream -> stream.id })
            val resolved = OtterApi.client.mutation(mutation).execute().dataOrThrow().resolveBestStream
            // The player moved on to another title while these links resolved.
            if (resolvingMediaId != mediaId) {
                return true
            }
            val sourceId = resolved.streamId ?: group.first().id
            mutableState.update { current -> current.copy(playing = PlayingSource(resolved.url, resolved.mimeType, sourceId), error = null) }
            return true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return false
        } finally {
            mutableState.update { current -> current.copy(loadingId = null) }
        }
    }
}

/**
 * The ranked links split into the groups autoplay races one after another: one group per
 * language rank (dubs and subs of a language are separate ranks), known-broken links last.
 */
fun raceGroups(rankedStreams: List<SourceStream>): List<List<SourceStream>> {
    val preferred = preferredLanguages()
    val (broken, usable) = rankedStreams.partition { stream -> stream.playable == false }
    val groups = usable.groupBy { stream -> languageRank(stream.language, preferred) }
        .toSortedMap()
        .values
        .toMutableList<List<SourceStream>>()
    if (broken.isNotEmpty()) {
        groups.add(broken)
    }
    return groups
}

/** Collects the live stream list of a movie or episode until cancelled; each update gets the ranked links and whether the lookup is done. */
suspend fun followSourceStreams(mediaId: String, onUpdate: (List<SourceStream>, Boolean) -> Unit) {
    try {
        OtterApi.awaitBackend()
        OtterApi.client.subscription(SourceStreamsSubscription(mediaId)).toFlow().collect { response ->
            val update = response.data?.streams ?: return@collect
            val streams = rankStreams(update.streams.map { stream -> stream.sourceStreamFields.toSourceStream() })
            onUpdate(streams, update.done)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        onUpdate(emptyList(), true)
    }
}
