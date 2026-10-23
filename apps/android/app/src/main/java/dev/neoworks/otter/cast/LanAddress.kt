package dev.neoworks.otter.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dev.neoworks.otter.BuildConfig
import java.net.Inet4Address

/**
 * The origin a TV on the same Wi-Fi reaches the backend's stream port at, e.g.
 * "http://192.168.1.23:47834"; null without Wi-Fi or Ethernet.
 */
fun lanStreamOrigin(context: Context): String? {
    val address = localNetworkAddress(context) ?: return null
    return "http://$address:${BuildConfig.BACKEND_LAN_PORT}"
}

/**
 * The Wi-Fi or Ethernet network. The VPN is the active network while connected,
 * so it is looked up directly.
 */
@Suppress("DEPRECATION") // allNetworks: the callback alternative can't answer synchronously.
fun localNetwork(context: Context): Network? {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    return connectivity.allNetworks.firstOrNull { network ->
        val capabilities = connectivity.getNetworkCapabilities(network)
        capabilities != null && isLocalNetwork(capabilities)
    }
}

private fun localNetworkAddress(context: Context): String? {
    val network = localNetwork(context) ?: return null
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val addresses = connectivity.getLinkProperties(network)?.linkAddresses.orEmpty()
    val ipv4 = addresses.map { linkAddress -> linkAddress.address }.firstOrNull { address -> address is Inet4Address }
    return ipv4?.hostAddress
}

private fun isLocalNetwork(capabilities: NetworkCapabilities): Boolean {
    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
        return false
    }
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}
