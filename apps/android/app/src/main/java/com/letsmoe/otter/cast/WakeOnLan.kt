package com.letsmoe.otter.cast

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

private const val TAG = "OtterWakeOnLan"
// Wake-on-LAN listens on the discard port (9); some TVs on the echo port (7).
private val WAKE_PORTS = listOf(9, 7)
private const val LIMITED_BROADCAST = "255.255.255.255"
// A TV needs a few seconds to boot its network; this long is waited for it before giving up.
private const val WAKE_TIMEOUT_MILLISECONDS = 60_000L
private const val RETRY_MILLISECONDS = 3000L

/** Sends the magic packet for [macAddress] to the Wi-Fi's broadcast addresses. */
suspend fun sendMagicPacket(context: Context, macAddress: ByteArray) = withContext(Dispatchers.IO) {
    val packet = magicPacket(macAddress)
    val targets = broadcastTargets(context)
    try {
        DatagramSocket().use { socket ->
            LocalNetworkRouting.bind(socket)
            socket.broadcast = true
            for (target in targets) {
                for (port in WAKE_PORTS) {
                    socket.send(DatagramPacket(packet, packet.size, target, port))
                }
            }
        }
    } catch (error: IOException) {
        Log.w(TAG, "sending the magic packet failed", error)
    }
}

private fun broadcastTargets(context: Context): List<InetAddress> {
    val targets = mutableListOf(InetAddress.getByName(LIMITED_BROADCAST))
    directedBroadcast(context)?.let { address -> targets.add(address) }
    return targets
}

// The router may drop 255.255.255.255 on the way into the Wi-Fi, so the network's own broadcast address goes too.
private fun directedBroadcast(context: Context): InetAddress? {
    val network = localNetwork(context) ?: return null
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val linkAddresses = connectivity.getLinkProperties(network)?.linkAddresses.orEmpty()
    val linkAddress = linkAddresses.firstOrNull { candidate -> candidate.address is Inet4Address } ?: return null
    val broadcast = directedBroadcastAddress(linkAddress.address.address, linkAddress.prefixLength)
    return InetAddress.getByAddress(broadcast)
}

/**
 * Switches the TV [television] on and waits until it answers an SSDP search again. Returns the TV as it
 * answers (it may have another address than before), or null when it didn't wake up in a minute.
 */
suspend fun wakeTelevision(context: Context, television: DlnaRenderer): DlnaRenderer? {
    val macAddress = WakeableTelevisions.macAddressOf(television.id) ?: return null
    val address = parseMacAddress(macAddress) ?: return null
    val deadline = SystemClock.elapsedRealtime() + WAKE_TIMEOUT_MILLISECONDS
    while (SystemClock.elapsedRealtime() < deadline) {
        sendMagicPacket(context, address)
        val answering = discoverRenderers(context).firstOrNull { renderer -> renderer.id == television.id }
        if (answering != null) {
            return answering
        }
        delay(RETRY_MILLISECONDS)
    }
    return null
}
