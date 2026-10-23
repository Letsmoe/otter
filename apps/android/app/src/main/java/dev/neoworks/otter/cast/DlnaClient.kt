package dev.neoworks.otter.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL

/** UPnP AVTransport transport states, as the TV reports them. */
enum class TransportState { PLAYING, PAUSED, STOPPED, TRANSITIONING, NO_MEDIA, UNKNOWN }

data class TransportPosition(val positionSeconds: Double, val durationSeconds: Double)

/** What the TV is told it plays: DLNA renderers check the type before fetching. */
data class TelevisionMedia(val url: String, val title: String, val mimeType: String, val seekableBytes: Boolean)

class DlnaException(message: String) : IOException(message)

/** The backend couldn't open the stream, e.g. because the link's token expired. */
class StreamUnavailableException(message: String) : IOException(message)

/** Sends AVTransport SOAP actions to one renderer. */
class DlnaClient(private val renderer: DlnaRenderer) {

    suspend fun load(media: TelevisionMedia) {
        call("SetAVTransportURI", "<CurrentURI>${escapeXml(media.url)}</CurrentURI><CurrentURIMetaData>${escapeXml(didl(media))}</CurrentURIMetaData>")
    }

    suspend fun play() {
        call("Play", "<Speed>1</Speed>")
    }

    suspend fun pause() {
        call("Pause", "")
    }

    suspend fun stop() {
        call("Stop", "")
    }

    suspend fun seek(seconds: Double) {
        call("Seek", "<Unit>REL_TIME</Unit><Target>${formatTime(seconds)}</Target>")
    }

    suspend fun transportState(): TransportState {
        val response = call("GetTransportInfo", "")
        return when (tagValue(response, "CurrentTransportState")) {
            "PLAYING" -> TransportState.PLAYING
            "PAUSED_PLAYBACK" -> TransportState.PAUSED
            "STOPPED" -> TransportState.STOPPED
            "TRANSITIONING" -> TransportState.TRANSITIONING
            "NO_MEDIA_PRESENT" -> TransportState.NO_MEDIA
            else -> TransportState.UNKNOWN
        }
    }

    suspend fun position(): TransportPosition {
        val response = call("GetPositionInfo", "")
        return TransportPosition(parseTime(tagValue(response, "RelTime")), parseTime(tagValue(response, "TrackDuration")))
    }

    private suspend fun call(action: String, arguments: String): String = withContext(Dispatchers.IO) {
        val envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"${renderer.serviceType}\"><InstanceID>0</InstanceID>$arguments</u:$action></s:Body></s:Envelope>"
        val connection = openHttp(URL(renderer.controlUrl))
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        connection.setRequestProperty("SOAPACTION", "\"${renderer.serviceType}#$action\"")
        try {
            connection.outputStream.use { output -> output.write(envelope.toByteArray()) }
            if (connection.responseCode != 200) {
                val fault = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                throw DlnaException("$action failed: HTTP ${connection.responseCode} ${tagValue(fault, "errorDescription")}")
            }
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}

private fun didl(media: TelevisionMedia): String {
    var operations = "DLNA.ORG_OP=00"
    if (media.seekableBytes) {
        operations = "DLNA.ORG_OP=01"
    }
    val protocolInfo = "http-get:*:${media.mimeType}:$operations;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
    return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
        "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
        "<item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>${escapeXml(media.title)}</dc:title>" +
        "<upnp:class>object.item.videoItem</upnp:class>" +
        "<res protocolInfo=\"$protocolInfo\">${escapeXml(media.url)}</res></item></DIDL-Lite>"
}

fun escapeXml(text: String): String {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

private fun tagValue(xml: String, tagName: String): String {
    val match = Regex("<(?:[\\w-]+:)?$tagName>([^<]*)</").find(xml) ?: return ""
    return match.groupValues[1].trim()
}

/** "H:MM:SS" (optionally with fractions) to seconds; 0 for NOT_IMPLEMENTED and the like. */
fun parseTime(value: String): Double {
    val parts = value.split(":")
    if (parts.size != 3) {
        return 0.0
    }
    val hours = parts[0].toDoubleOrNull() ?: return 0.0
    val minutes = parts[1].toDoubleOrNull() ?: return 0.0
    val seconds = parts[2].toDoubleOrNull() ?: return 0.0
    return hours * 3600 + minutes * 60 + seconds
}

fun formatTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    return "%d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}
