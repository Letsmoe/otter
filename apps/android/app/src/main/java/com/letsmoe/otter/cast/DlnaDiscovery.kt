package com.letsmoe.otter.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import com.letsmoe.otter.reporting.ErrorReporting
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
    /** ConnectionManager (which formats the TV accepts); null when the TV doesn't offer it. */
    val connectionManager: DlnaService? = null,
    /** Manufacturer and model, e.g. "Hisense 55A6K", for error reports. */
    val model: String = "",
    /** The device description XML as the TV sent it, for error reports. */
    val description: String = "",
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
        LocalNetworkRouting.bind(socket)
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
    var description = ""
    return try {
        val connection = openHttp(URL(location))
        description = connection.inputStream.use { input -> input.readBytes().decodeToString() }
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(description.byteInputStream()).documentElement
        rendererOf(root, location, description)
    } catch (unreachable: IOException) {
        // A remembered TV that is off or got another address.
        Log.w(TAG, "describing $location failed", unreachable)
        null
    } catch (error: Exception) {
        Log.w(TAG, "describing $location failed", error)
        ErrorReporting.capture(error, "dlna.discovery", mapOf("location" to location, "description" to description))
        null
    }
}

private fun rendererOf(root: Element, location: String, description: String): DlnaRenderer? {
    val model = listOfNotNull(childText(root, "manufacturer"), childText(root, "modelName")).joinToString(" ")
    val transport = serviceOf(root, ":AVTransport:", location)
    if (transport == null) {
        // It answered as a media renderer, so it should have AVTransport; the description tells why not.
        val error = DlnaException("Renderer $model has no AVTransport service")
        ErrorReporting.capture(error, "dlna.discovery", mapOf("location" to location, "description" to description))
        return null
    }
    return DlnaRenderer(
        name = childText(root, "friendlyName") ?: "TV",
        id = childText(root, "UDN") ?: transport.controlUrl,
        controlUrl = transport.controlUrl,
        serviceType = transport.serviceType,
        volumeControl = serviceOf(root, ":RenderingControl:", location),
        connectionManager = serviceOf(root, ":ConnectionManager:", location),
        model = model,
        description = description,
    )
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

/** HTTP to the TV, over Wi-Fi even while a VPN is up (see [LocalNetworkRouting]). */
fun openHttp(url: URL): HttpURLConnection {
    val connection = LocalNetworkRouting.openConnection(url)
    connection.connectTimeout = HTTP_TIMEOUT_MILLISECONDS
    connection.readTimeout = HTTP_TIMEOUT_MILLISECONDS
    return connection as HttpURLConnection
}
