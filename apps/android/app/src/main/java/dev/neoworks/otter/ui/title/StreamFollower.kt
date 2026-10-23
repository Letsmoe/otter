package dev.neoworks.otter.ui.title

import dev.neoworks.otter.api.OtterApi
import dev.neoworks.otter.graphql.StreamsSubscription
import dev.neoworks.otter.graphql.fragment.StreamLink
import dev.neoworks.otter.profiles.languageRank
import dev.neoworks.otter.profiles.preferredLanguages
import dev.neoworks.otter.toasts.ToastKind
import dev.neoworks.otter.toasts.Toasts
import dev.neoworks.otter.ui.media.TitleActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StreamList(val streams: List<StreamLink>, val searching: Boolean) {
    val isBusy: Boolean get() = streams.isEmpty() && searching
    val nothingFound: Boolean get() = streams.isEmpty() && !searching
}

/** Stream links of one movie: stored links first, then plugin answers as they arrive. */
class StreamFollower(private val scope: CoroutineScope) {

    private val mutableStreams = MutableStateFlow(StreamList(emptyList(), searching = true))
    val streams: StateFlow<StreamList> = mutableStreams

    private var mediaId: String? = null
    private var subscriptionJob: Job? = null

    fun follow(mediaId: String) {
        stop()
        this.mediaId = mediaId
        mutableStreams.value = StreamList(emptyList(), searching = true)
        subscriptionJob = scope.launch { collectUpdates(mediaId) }
    }

    fun stop() {
        subscriptionJob?.cancel()
        subscriptionJob = null
    }

    private suspend fun collectUpdates(mediaId: String) {
        OtterApi.awaitBackend()
        OtterApi.client.subscription(StreamsSubscription(mediaId)).toFlow()
            .catch { mutableStreams.update { current -> current.copy(searching = false) } }
            .collect { response ->
                val update = response.data?.streams ?: return@collect
                val ranked = rankStreams(update.streams.map { stream -> stream.streamLink })
                mutableStreams.value = StreamList(ranked, searching = !update.done)
            }
    }

    /** Asks every streams plugin again; results arrive through the subscription. */
    fun searchAgain() {
        val currentMediaId = mediaId
        if (currentMediaId == null || mutableStreams.value.searching) {
            return
        }
        mutableStreams.update { current -> current.copy(searching = true) }
        scope.launch { requestLookup(currentMediaId) }
    }

    private suspend fun requestLookup(mediaId: String) {
        try {
            TitleActions.findStreams(mediaId)
        } catch (error: Exception) {
            mutableStreams.update { current -> current.copy(searching = false) }
            Toasts.push("Could not start a source lookup", ToastKind.ERROR)
        }
    }
}

/** Known-broken links last; otherwise the profile's languages first, then known-playable before untested. */
fun rankStreams(streams: List<StreamLink>): List<StreamLink> {
    val preferred = preferredLanguages()
    return streams.sortedWith(
        compareBy<StreamLink>(
            { stream -> stream.playable == false },
            { stream -> languageRank(stream.language, preferred) },
            { stream -> playableRank(stream) },
        ),
    )
}

private fun playableRank(stream: StreamLink): Int = when (stream.playable) {
    true -> 0
    null -> 1
    false -> 2
}
