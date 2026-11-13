package com.letsmoe.otter.cast

/** One entry of a renderer's sink protocol info, e.g. http-get:*:video/mp2t:DLNA.ORG_PN=AVC_TS_HD_EU_ISO. */
data class TelevisionFormat(val mimeType: String, val profile: String?)

/** The type and DLNA profile the TV is told about a stream; [profile] is null when the TV takes any. */
data class AnnouncedFormat(val mimeType: String, val profile: String?)

// Names TVs use for the same container, in the order they are tried after the one Otter sends by default.
private val containerAliases = listOf(
    listOf("video/mpeg", "video/mp2t", "video/vnd.dlna.mpeg-tts", "video/mpeg2"),
    listOf("video/mp4", "video/mpeg4", "video/x-mp4"),
    listOf("video/x-matroska", "video/x-mkv", "video/mkv"),
)

// Profiles for H.264 in plain 188-byte TS or MP4, which is what the stream proxy serves.
private val preferredProfilePrefixes = listOf("AVC_TS_HD_EU_ISO", "AVC_TS_HD_ISO", "AVC_TS_", "AVC_MP4_", "MPEG_TS_HD_EU_ISO", "MPEG_TS_")

/** Parses the Sink value of ConnectionManager's GetProtocolInfo; only http-get entries count. */
fun parseSinkProtocolInfo(sink: String): List<TelevisionFormat> {
    return sink.split(",").mapNotNull { entry -> parseProtocolInfo(entry.trim()) }
}

private fun parseProtocolInfo(entry: String): TelevisionFormat? {
    val fields = entry.split(":", limit = 4)
    if (fields.size < 4 || !fields[0].equals("http-get", ignoreCase = true)) {
        return null
    }
    val profile = fields[3].split(";")
        .firstOrNull { parameter -> parameter.startsWith("DLNA.ORG_PN=") }
        ?.substringAfter("=")
    return TelevisionFormat(fields[2].trim(), profile)
}

/**
 * What to announce for a stream of [mimeType] to a TV that accepts [accepted].
 * The default type stays when the TV lists it (or lists nothing), so TVs that
 * already play keep getting exactly what they got before.
 */
fun announcedFormat(mimeType: String, accepted: List<TelevisionFormat>): AnnouncedFormat {
    val unchanged = AnnouncedFormat(mimeType, null)
    if (accepted.isEmpty()) {
        return unchanged
    }
    val candidates = listOf(mimeType) + aliasesOf(mimeType)
    for (candidate in candidates) {
        val matches = accepted.filter { format -> format.mimeType.equals(candidate, ignoreCase = true) }
        if (matches.isNotEmpty()) {
            return AnnouncedFormat(matches.first().mimeType, profileFor(matches))
        }
    }
    return unchanged
}

private fun aliasesOf(mimeType: String): List<String> {
    val group = containerAliases.firstOrNull { aliases -> aliases.any { alias -> alias.equals(mimeType, ignoreCase = true) } }
    if (group == null) {
        return emptyList()
    }
    return group.filterNot { alias -> alias.equals(mimeType, ignoreCase = true) }
}

// A profile is only named when the TV accepts the type with nothing but specific profiles.
private fun profileFor(matches: List<TelevisionFormat>): String? {
    if (matches.any { format -> format.profile == null }) {
        return null
    }
    val profiles = matches.mapNotNull { format -> format.profile }
    for (prefix in preferredProfilePrefixes) {
        val preferred = profiles.firstOrNull { profile -> profile.startsWith(prefix) && !profile.endsWith("_T") }
        if (preferred != null) {
            return preferred
        }
    }
    return profiles.firstOrNull()
}
