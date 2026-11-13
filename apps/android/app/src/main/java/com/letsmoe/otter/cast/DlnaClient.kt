package com.letsmoe.otter.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL

/** UPnP AVTransport transport states, as the TV reports them. */
enum class TransportState { PLAYING, PAUSED, STOPPED, TRANSITIONING, NO_MEDIA, UNKNOWN }

data class TransportPosition(val positionSeconds: Double, val durationSeconds: Double)

/** A caption file the TV fetches: [url] is the SRT on the phone's LAN stream port. */
data class TelevisionSubtitle(val id: String, val url: String)

/** What the TV is told it plays: DLNA renderers check the type before fetching. */
data class TelevisionMedia(
    val url: String,
    val title: String,
    val mimeType: String,
    val seekableBytes: Boolean,
    val subtitle: TelevisionSubtitle? = null,
    /** DLNA.ORG_PN, for TVs that only take named profiles. */
    val profile: String? = null,
)

private const val COMMAND_TIMEOUT_MILLISECONDS = 4000
private const val INSTANCE = "<InstanceID>0</InstanceID>"
// Philips TVs answer SetAVTransportURI and Play only after reading the start of the stream,
// which takes a while for a continuous stream fetched over the VPN.
private const val LOADING_TIMEOUT_MILLISECONDS = 30_000

class DlnaException(message: String) : IOException(message)

/** The TV took the link but never started it, e.g. "resolution not supported": another link may play. */
class TelevisionRefusedException(message: String) : IOException(message)

/** The backend couldn't open the stream, e.g. because the link's token expired. */
class StreamUnavailableException(message: String) : IOException(message)

/** Sends AVTransport SOAP actions to one renderer. */
class DlnaClient(private val renderer: DlnaRenderer) {

    suspend fun load(media: TelevisionMedia) {
        val arguments = "$INSTANCE<CurrentURI>${escapeXml(media.url)}</CurrentURI><CurrentURIMetaData>${escapeXml(didl(media))}</CurrentURIMetaData>"
        call(transportService(), "SetAVTransportURI", arguments, LOADING_TIMEOUT_MILLISECONDS)
    }

    suspend fun play() {
        call(transportService(), "Play", "$INSTANCE<Speed>1</Speed>", LOADING_TIMEOUT_MILLISECONDS)
    }

    /** The formats the TV says it plays; empty when it doesn't say. */
    suspend fun acceptedFormats(): List<TelevisionFormat> {
        val service = renderer.connectionManager ?: return emptyList()
        val response = call(service, "GetProtocolInfo", "")
        return parseSinkProtocolInfo(xmlUnescape(tagValue(response, "Sink")))
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

    /** The TV's volume, 0 to 100; null when it has no volume control. */
    suspend fun volume(): Int? {
        val service = renderer.volumeControl ?: return null
        val response = call(service, "GetVolume", "$INSTANCE<Channel>Master</Channel>")
        return tagValue(response, "CurrentVolume").toIntOrNull()
    }

    suspend fun setVolume(volume: Int) {
        val service = renderer.volumeControl ?: return
        call(service, "SetVolume", "$INSTANCE<Channel>Master</Channel><DesiredVolume>${volume.coerceIn(0, 100)}</DesiredVolume>")
    }

    private fun transportService(): DlnaService {
        return DlnaService(renderer.controlUrl, renderer.serviceType)
    }

    private suspend fun call(action: String, arguments: String): String {
        return call(transportService(), action, INSTANCE + arguments)
    }

    private suspend fun call(
        service: DlnaService,
        action: String,
        arguments: String,
        readTimeoutMilliseconds: Int = COMMAND_TIMEOUT_MILLISECONDS,
    ): String = withContext(Dispatchers.IO) {
        val envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"${service.serviceType}\">$arguments</u:$action></s:Body></s:Envelope>"
        val connection = openHttp(URL(service.controlUrl))
        connection.readTimeout = readTimeoutMilliseconds
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        connection.setRequestProperty("SOAPACTION", "\"${service.serviceType}#$action\"")
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
    if (media.profile != null) {
        operations = "DLNA.ORG_PN=${media.profile};$operations"
    }
    val protocolInfo = "http-get:*:${media.mimeType}:$operations;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
    val subtitle = media.subtitle
    return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
        "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" xmlns:sec=\"http://www.sec.co.kr/\" xmlns:pv=\"http://www.pv.com/pvns/\">" +
        "<item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>${escapeXml(media.title)}</dc:title>" +
        "<upnp:class>object.item.videoItem</upnp:class>" +
        "<res protocolInfo=\"$protocolInfo\"${videoSubtitleAttributes(subtitle)}>${escapeXml(media.url)}</res>" +
        subtitleElements(subtitle) +
        "</item></DIDL-Lite>"
}

// Panasonic and others read the caption file from attributes of the video resource.
private fun videoSubtitleAttributes(subtitle: TelevisionSubtitle?): String {
    if (subtitle == null) {
        return ""
    }
    return " pv:subtitleFileUri=\"${escapeXml(subtitle.url)}\" pv:subtitleFileType=\"srt\""
}

// Samsung reads the CaptionInfo elements; other renderers pick up the extra SRT resource.
private fun subtitleElements(subtitle: TelevisionSubtitle?): String {
    if (subtitle == null) {
        return ""
    }
    val url = escapeXml(subtitle.url)
    return "<res protocolInfo=\"http-get:*:text/srt:*\">$url</res>" +
        "<sec:CaptionInfoEx sec:type=\"srt\">$url</sec:CaptionInfoEx>" +
        "<sec:CaptionInfo sec:type=\"srt\">$url</sec:CaptionInfo>"
}

fun escapeXml(text: String): String {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

private fun xmlUnescape(text: String): String {
    return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
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
