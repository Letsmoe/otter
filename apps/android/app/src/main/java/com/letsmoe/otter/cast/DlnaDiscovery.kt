package com.letsmoe.otter.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

/** A TV (or any UPnP media renderer) that can play a URL. */
data class DlnaRenderer(
    val name: String,
    /** The device's UDN: stays the same when the TV gets another IP address. */
    val id: String,
    /** Where AVTransport SOAP actions go. */
    val controlUrl: String,
    /** e.g. urn:schemas-upnp-org:service:AVTransport:1 */
    val serviceType: String,
    /** RenderingControl (volume); null when the TV doesn't offer it. */
    val volumeControl: DlnaService? = null,
)

/** One UPnP service of a renderer: where its SOAP actions go and its type. */
data class DlnaService(val controlUrl: String, val serviceType: String)

private const val TAG = "OtterDlna"
private const val SSDP_ADDRESS = "239.255.255.250"
private const val SSDP_PORT = 1900
private const val SEARCH_MILLISECONDS = 3000
private const val SEARCH_RESEND_MILLISECONDS = 1000
private const val PREFERENCES = "dlna_renderers"
private const val KNOWN_LOCATIONS = "locations"
private const val HTTP_TIMEOUT_MILLISECONDS = 4000
private const val MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

/** Finds the media renderers on the Wi-Fi with an SSDP search. */
suspend fun discoverRenderers(context: Context): List<DlnaRenderer> = withContext(Dispatchers.IO) {
    val wifiInterface = localNetworkInterface(context)
    val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    // Android drops multicast answers without this lock.
    val multicastLock = wifi.createMulticastLock("otter:dlna")
    multicastLock.acquire()
    try {
        val found = searchLocations(wifiInterface)
        rememberLocations(context, found)
        // TVs found before are asked directly: an SSDP answer gets lost now and then.
        val locations = found + knownLocations(context)
        locations.mapNotNull { location -> describeRenderer(location) }.distinctBy { renderer -> renderer.id }
    } catch (error: IOException) {
        Log.w(TAG, "discovery failed", error)
        emptyList()
    } finally {
        multicastLock.release()
    }
}

/**
 * The Wi-Fi interface for the search. A VPN routes multicast into its tunnel,
 * and NordVPN forbids binding sockets to the Wi-Fi network (EPERM), but the
 * multicast interface may be chosen freely.
 */
private fun localNetworkInterface(context: Context): NetworkInterface? {
    val network = localNetwork(context) ?: return null
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val interfaceName = connectivity.getLinkProperties(network)?.interfaceName ?: return null
    return NetworkInterface.getByName(interfaceName)
}

private fun searchLocations(wifiInterface: NetworkInterface?): Set<String> {
    val request = listOf(
        "M-SEARCH * HTTP/1.1",
        "HOST: $SSDP_ADDRESS:$SSDP_PORT",
        "MAN: \"ssdp:discover\"",
        "MX: 2",
        "ST: $MEDIA_RENDERER",
        "",
        "",
    ).joinToString("\r\n").toByteArray()
    val locations = mutableSetOf<String>()
    MulticastSocket().use { socket ->
        if (wifiInterface != null) {
            socket.networkInterface = wifiInterface
        }
        socket.soTimeout = 500
        val target = InetAddress.getByName(SSDP_ADDRESS)
        val start = System.currentTimeMillis()
        var nextSendAt = start
        while (System.currentTimeMillis() < start + SEARCH_MILLISECONDS) {
            // Repeated through the search: the first packets after taking the multicast lock often get lost.
            if (System.currentTimeMillis() >= nextSendAt) {
                socket.send(DatagramPacket(request, request.size, target, SSDP_PORT))
                nextSendAt += SEARCH_RESEND_MILLISECONDS
            }
            receiveLocation(socket)?.let { location -> locations.add(location) }
        }
    }
    return locations
}

/** The description URLs of the renderers found by earlier searches. */
private fun knownLocations(context: Context): Set<String> {
    val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    return preferences.getStringSet(KNOWN_LOCATIONS, emptySet()).orEmpty()
}

// Only the last search's answers: a TV that got another address answers again under it.
private fun rememberLocations(context: Context, locations: Set<String>) {
    if (locations.isEmpty()) {
        return
    }
    val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    preferences.edit().putStringSet(KNOWN_LOCATIONS, locations).apply()
}

private fun receiveLocation(socket: DatagramSocket): String? {
    val buffer = ByteArray(4096)
    val packet = DatagramPacket(buffer, buffer.size)
    try {
        socket.receive(packet)
    } catch (timeout: SocketTimeoutException) {
        return null
    }
    val response = String(packet.data, 0, packet.length)
    val locationLine = response.lineSequence().firstOrNull { line -> line.startsWith("location:", ignoreCase = true) }
    return locationLine?.substringAfter(":")?.trim()
}

/** Reads the device description and finds its AVTransport service. */
private fun describeRenderer(location: String): DlnaRenderer? {
    return try {
        val connection = openHttp(URL(location))
        val document = connection.inputStream.use { input ->
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
        }
        val root = document.documentElement
        val transport = serviceOf(root, ":AVTransport:", location) ?: return null
        val name = childText(root, "friendlyName") ?: "TV"
        val id = childText(root, "UDN") ?: transport.controlUrl
        DlnaRenderer(name, id, transport.controlUrl, transport.serviceType, serviceOf(root, ":RenderingControl:", location))
    } catch (error: Exception) {
        Log.w(TAG, "describing $location failed", error)
        null
    }
}

/** The first service whose type contains [typeMarker], e.g. ":AVTransport:". */
private fun serviceOf(root: Element, typeMarker: String, location: String): DlnaService? {
    val services = root.getElementsByTagName("service")
    for (index in 0 until services.length) {
        val service = services.item(index) as Element
        val serviceType = childText(service, "serviceType").orEmpty()
        if (!serviceType.contains(typeMarker)) {
            continue
        }
        val controlPath = childText(service, "controlURL") ?: return null
        return DlnaService(URL(URL(location), controlPath).toString(), serviceType)
    }
    return null
}

private fun childText(element: Element, tagName: String): String? {
    val matches = element.getElementsByTagName(tagName)
    if (matches.length == 0) {
        return null
    }
    return matches.item(0).textContent.trim()
}

/**
 * HTTP to the TV. Plain routing: VPNs with LAN access (NordVPN: Local network
 * discovery) send private addresses over Wi-Fi; without it the TV couldn't
 * reach the phone's stream port either.
 */
fun openHttp(url: URL): HttpURLConnection {
    val connection = url.openConnection()
    connection.connectTimeout = HTTP_TIMEOUT_MILLISECONDS
    connection.readTimeout = HTTP_TIMEOUT_MILLISECONDS
    return connection as HttpURLConnection
}
