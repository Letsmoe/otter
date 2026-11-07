package com.letsmoe.otter.cast

import android.content.Context
import android.net.Network
import android.util.Log
import java.io.IOException
import java.net.DatagramSocket
import java.net.URL
import java.net.URLConnection

private const val TAG = "OtterLanRouting"

/**
 * Sends TV traffic over the Wi-Fi network itself when the VPN lets apps bypass
 * it: ProtonVPN without "Allow LAN connections" routes private addresses into
 * its tunnel. VPNs that forbid binding (NordVPN: EPERM) keep plain routing.
 */
object LocalNetworkRouting {
    private lateinit var applicationContext: Context

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun openConnection(url: URL): URLConnection {
        val network = bindableNetwork() ?: return url.openConnection()
        return network.openConnection(url)
    }

    /** Binds [socket] to the Wi-Fi network when allowed; otherwise it keeps plain routing. */
    fun bind(socket: DatagramSocket) {
        val network = bindableNetwork() ?: return
        try {
            network.bindSocket(socket)
        } catch (error: IOException) {
            Log.w(TAG, "binding the search socket to Wi-Fi failed", error)
        }
    }

    /** The Wi-Fi or Ethernet network when sockets may be bound to it, else null. */
    private fun bindableNetwork(): Network? {
        val network = localNetwork(applicationContext) ?: return null
        if (!canBindTo(network)) {
            return null
        }
        return network
    }

    private fun canBindTo(network: Network): Boolean {
        return try {
            DatagramSocket().use { socket -> network.bindSocket(socket) }
            true
        } catch (error: IOException) {
            false
        }
    }
}
