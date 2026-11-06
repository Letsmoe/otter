package com.letsmoe.otter.vpn

import com.letsmoe.otter.AppScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.VpnStatusQuery
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class VpnStatus(
    val connected: Boolean,
    val required: Boolean,
    /** False until the first answer, and while the backend is unreachable. */
    val known: Boolean,
) {
    /** The backend refuses to search and play: a VPN is required and none is up. */
    val blocksStreaming: Boolean
        get() = known && required && !connected
}

/** VPN state from the backend, polled while something shows it; mirrors stores/vpn.ts. */
object VpnStatusStore {

    private const val POLL_INTERVAL_MILLISECONDS = 30_000L
    private const val RETRY_INTERVAL_MILLISECONDS = 2_000L
    private const val BLOCKED_POLL_INTERVAL_MILLISECONDS = 3_000L
    private const val STOP_TIMEOUT_MILLISECONDS = 5_000L

    private val unknown = VpnStatus(connected = false, required = false, known = false)

    val status: StateFlow<VpnStatus> = flow {
        while (true) {
            val current = fetchStatus()
            emit(current)
            delay(nextDelay(current))
        }
    }.stateIn(AppScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLISECONDS), unknown)

    // Fast while unknown or blocked, so a VPN switched on unblocks the app within seconds.
    private fun nextDelay(current: VpnStatus): Long {
        if (!current.known) {
            return RETRY_INTERVAL_MILLISECONDS
        }
        if (current.blocksStreaming) {
            return BLOCKED_POLL_INTERVAL_MILLISECONDS
        }
        return POLL_INTERVAL_MILLISECONDS
    }

    private suspend fun fetchStatus(): VpnStatus {
        return try {
            val data = OtterApi.client.query(VpnStatusQuery()).execute().dataOrThrow()
            VpnStatus(data.status.vpnConnected, data.status.requireVpn, known = true)
        } catch (error: Exception) {
            unknown
        }
    }
}
