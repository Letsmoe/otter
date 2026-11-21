package com.letsmoe.otter.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether the phone's default network reaches the internet (Android validated it). */
object ConnectivityStore {

    private val mutableOnline = MutableStateFlow(true)
    val online: StateFlow<Boolean> = mutableOnline

    fun initialize(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        mutableOnline.value = reachesInternet(connectivity.getNetworkCapabilities(connectivity.activeNetwork))
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                mutableOnline.value = reachesInternet(capabilities)
            }

            override fun onLost(network: Network) {
                mutableOnline.value = false
            }
        })
    }

    private fun reachesInternet(capabilities: NetworkCapabilities?): Boolean {
        if (capabilities == null) {
            return false
        }
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return hasInternet && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
