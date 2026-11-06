package com.letsmoe.otter.ui.title

import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.graphql.StreamsSubscription
import com.letsmoe.otter.graphql.fragment.StreamLink
import com.letsmoe.otter.profiles.languageRank
import com.letsmoe.otter.profiles.preferredLanguages
import com.letsmoe.otter.toasts.ToastKind
import com.letsmoe.otter.toasts.Toasts
import com.letsmoe.otter.ui.media.TitleActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.letsmoe.otter.vpn.VpnStatusStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [vpnBlocked]: the backend requires a VPN and none is up, so it neither searches nor plays. */
data class StreamList(val streams: List<StreamLink>, val searching: Boolean, val vpnBlocked: Boolean = false) {
    val isBusy: Boolean get() = streams.isEmpty() && searching && !vpnBlocked
    val nothingFound: Boolean get() = streams.isEmpty() && !searching && !vpnBlocked
}

/** Stream links of one movie: stored links first, then plugin answers as they arrive. */
class StreamFollower(private val scope: CoroutineScope) {

    private val mutableStreams = MutableStateFlow(StreamList(emptyList(), searching = true))
    val streams: StateFlow<StreamList> = mutableStreams

    private var mediaId: String? = null
    private var subscriptionJob: Job? = null
    private var vpnJob: Job? = null

    fun follow(mediaId: String) {
        stop()
        this.mediaId = mediaId
        mutableStreams.value = StreamList(emptyList(), searching = true)
        subscriptionJob = scope.launch { collectUpdates(mediaId) }
        vpnJob = scope.launch { followVpn() }
    }

    fun stop() {
        subscriptionJob?.cancel()
        vpnJob?.cancel()
        subscriptionJob = null
        vpnJob = null
    }

    // The search the backend refused without a VPN starts once one is up.
    private suspend fun followVpn() {
        VpnStatusStore.status.map { status -> status.blocksStreaming }.distinctUntilChanged().collect { blocked ->
            val wasBlocked = mutableStreams.value.vpnBlocked
            mutableStreams.update { current -> current.copy(vpnBlocked = blocked) }
            if (wasBlocked && !blocked) {
                lookUpAgain()
            }
        }
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
        lookUpAgain()
    }

    private fun lookUpAgain() {
        val currentMediaId = mediaId ?: return
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
