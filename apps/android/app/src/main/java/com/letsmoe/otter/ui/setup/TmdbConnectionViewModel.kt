package com.letsmoe.otter.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.CheckTmdbConnectionMutation
import com.letsmoe.otter.graphql.TmdbReachableQuery
import com.letsmoe.otter.network.ConnectivityStore
import com.letsmoe.otter.ui.ShellReloads
import com.letsmoe.otter.ui.navigation.Tab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val RETRY_INTERVAL_MILLISECONDS = 5_000L
private const val WATCH_INTERVAL_MILLISECONDS = 30_000L

/**
 * Says when the backend gets no answer from TMDB (offline, or a VPN or DNS filter blocks it). While
 * shown it asks TMDB again every few seconds; otherwise it follows the backend's last request.
 */
class TmdbConnectionViewModel : ViewModel() {

    var visible by mutableStateOf(false)
        private set
    var checking by mutableStateOf(false)
        private set

    /** "Continue anyway" hides the overlay until TMDB answers again. */
    private var dismissed = false

    /** The backend's last answer may date from being offline, so the next check asks TMDB itself. */
    private var recheckWhenOnline = false

    /** Runs while the app is in the foreground; each start asks TMDB itself. */
    suspend fun watch() {
        OtterApi.awaitBackend()
        showReachability(askTmdb())
        while (true) {
            delay(nextDelay())
            showReachability(currentReachability())
        }
    }

    fun retry() {
        viewModelScope.launch {
            checking = true
            showReachability(askTmdb())
            checking = false
        }
    }

    fun dismiss() {
        dismissed = true
        visible = false
    }

    private fun nextDelay(): Long {
        if (visible) {
            return RETRY_INTERVAL_MILLISECONDS
        }
        return WATCH_INTERVAL_MILLISECONDS
    }

    private suspend fun currentReachability(): Boolean {
        if (visible) {
            return askTmdb()
        }
        if (recheckWhenOnline && ConnectivityStore.online.value) {
            recheckWhenOnline = false
            return askTmdb()
        }
        return lastKnownReachability()
    }

    // Pages loaded while TMDB was unreachable are empty: rebuild them once it answers again.
    private fun showReachability(reachable: Boolean) {
        if (!ConnectivityStore.online.value) {
            // The offline bar says why nothing loads; ask TMDB itself once the phone is back online.
            visible = false
            recheckWhenOnline = true
            return
        }
        if (!reachable) {
            visible = !dismissed
            return
        }
        dismissed = false
        if (visible) {
            visible = false
            ShellReloads.request(Tab.HOME)
        }
    }

    private suspend fun askTmdb(): Boolean = answerOrReachable {
        OtterApi.client.mutation(CheckTmdbConnectionMutation()).execute().dataOrThrow().checkTmdbConnection
    }

    private suspend fun lastKnownReachability(): Boolean = answerOrReachable {
        OtterApi.client.query(TmdbReachableQuery()).execute().dataOrThrow().status.tmdbReachable
    }

    // An unreachable backend isn't a TMDB problem; the app shows its own errors for that.
    private suspend fun answerOrReachable(request: suspend () -> Boolean): Boolean {
        try {
            return request()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return true
        }
    }
}
