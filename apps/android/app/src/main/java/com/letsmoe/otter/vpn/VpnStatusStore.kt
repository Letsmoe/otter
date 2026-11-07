package com.letsmoe.otter.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.letsmoe.otter.AppScope
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.VpnStatusQuery
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

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

    /** A VPN on the phone came up or went down: ask the backend right away. */
    private val networkChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val status: StateFlow<VpnStatus> = flow {
        while (true) {
            val current = fetchStatus()
            emit(current)
            withTimeoutOrNull(nextDelay(current)) { networkChanges.first() }
        }
    }.stateIn(AppScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLISECONDS), unknown)

    fun initialize(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val vpnNetworks = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivity.registerNetworkCallback(vpnNetworks, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkChanges.tryEmit(Unit)
            }

            override fun onLost(network: Network) {
                networkChanges.tryEmit(Unit)
            }
        })
    }

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
