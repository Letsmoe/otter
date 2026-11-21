package com.letsmoe.otter.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.letsmoe.otter.AppScope
import com.letsmoe.otter.ui.player.PlayerViewModel
import com.letsmoe.otter.ui.player.TelevisionPlayback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// A Wi-Fi that is back within this time was a hiccup (or a roam), not the phone leaving.
private const val GRACE_MILLISECONDS = 4000L

/** The network the TV was on when it got connected; tells the phone leaving it from any other network going away. */
class HomeNetwork<N> {

    private var network: N? = null

    fun rememberAs(current: N?) {
        network = current
    }

    fun isLost(lost: N): Boolean {
        val home = network ?: return false
        return home == lost
    }
}

/**
 * Notices the phone leaving the Wi-Fi while a DLNA TV plays: the TV is paused (as far as it still can
 * be reached) and the title carries on on the phone from the same spot, see [TelevisionPlayback.carryOnOnPhone].
 */
object LeavingHome {

    private val homeNetwork = HomeNetwork<Network>()
    private var watching = false

    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                if (homeNetwork.isLost(network)) {
                    AppScope.launch { onLeftWifi(applicationContext) }
                }
            }
        }
        AppScope.launch {
            TelevisionConnection.target.collect { target ->
                if (target is TelevisionTarget.Dlna) {
                    watch(applicationContext, connectivity, callback)
                    return@collect
                }
                stopWatching(connectivity, callback)
            }
        }
    }

    private fun watch(context: Context, connectivity: ConnectivityManager, callback: ConnectivityManager.NetworkCallback) {
        homeNetwork.rememberAs(localNetwork(context))
        if (watching) {
            return
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        connectivity.registerNetworkCallback(request, callback)
        watching = true
    }

    private fun stopWatching(connectivity: ConnectivityManager, callback: ConnectivityManager.NetworkCallback) {
        homeNetwork.rememberAs(null)
        if (!watching) {
            return
        }
        connectivity.unregisterNetworkCallback(callback)
        watching = false
    }

    private suspend fun onLeftWifi(context: Context) {
        val playback = TelevisionPlayback.player.value ?: return
        if (!playback.state.value.isPlaying) {
            return
        }
        // Reaches the TV only when it still can be; the phone is gone from its network otherwise.
        playback.setPlaying(false)
        delay(GRACE_MILLISECONDS)
        if (localNetwork(context) != null) {
            resumeAfterHiccup(playback)
            return
        }
        TelevisionPlayback.carryOnOnPhone()
    }

    private fun resumeAfterHiccup(playback: PlayerViewModel) {
        if (TelevisionPlayback.player.value === playback) {
            playback.setPlaying(true)
        }
    }
}
