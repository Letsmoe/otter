package dev.neoworks.otter.together

import org.json.JSONException
import org.json.JSONObject

// Wire format shared with the web apps (packages/otter-app/src/together.ts).

fun TogetherPayload.toJson(): String {
    val json = JSONObject()
    when (this) {
        is MediaPayload -> writeMedia(json, this)
        is PlaybackPayload -> writePlayback(json, this)
        is StatusPayload -> json.put("type", "status").put("mediaId", mediaId ?: JSONObject.NULL).put("buffering", buffering)
        is ChatPayload -> json.put("type", "chat").put("text", text)
        is ReactionPayload -> json.put("type", "reaction").put("emoji", emoji)
    }
    return json.toString()
}

private fun writeMedia(json: JSONObject, payload: MediaPayload) {
    json.put("type", "media")
    json.put("mediaId", payload.mediaId)
    json.put("route", payload.route)
    json.put("title", payload.title)
    json.put("source", sourceJson(payload.source))
}

private fun sourceJson(source: SharedSource?): Any {
    if (source == null) {
        return JSONObject.NULL
    }
    return JSONObject().put("url", source.url).put("plugin", source.plugin).put("label", source.label)
}

private fun writePlayback(json: JSONObject, payload: PlaybackPayload) {
    json.put("type", "playback")
    json.put("mediaId", payload.mediaId)
    json.put("playing", payload.playing)
    json.put("position", payload.position)
    json.put("heartbeat", payload.heartbeat)
}

/** Parses a received payload; unknown or malformed ones are null. */
fun parseTogetherPayload(text: String): TogetherPayload? {
    return try {
        readPayload(JSONObject(text))
    } catch (error: JSONException) {
        null
    }
}

private fun readPayload(json: JSONObject): TogetherPayload? {
    return when (json.optString("type")) {
        "media" -> readMedia(json)
        "playback" -> PlaybackPayload(
            mediaId = json.getString("mediaId"),
            playing = json.getBoolean("playing"),
            position = json.getDouble("position"),
            heartbeat = json.optBoolean("heartbeat", false),
        )
        "status" -> StatusPayload(optionalString(json, "mediaId"), json.optBoolean("buffering", false))
        "chat" -> ChatPayload(json.getString("text"))
        "reaction" -> ReactionPayload(json.getString("emoji"))
        else -> null
    }
}

private fun readMedia(json: JSONObject): MediaPayload {
    val sourceJson = json.optJSONObject("source")
    var source: SharedSource? = null
    if (sourceJson != null) {
        source = SharedSource(sourceJson.getString("url"), sourceJson.getString("plugin"), sourceJson.optString("label"))
    }
    return MediaPayload(json.getString("mediaId"), json.optString("route"), json.optString("title"), source)
}

private fun optionalString(json: JSONObject, key: String): String? {
    if (json.isNull(key)) {
        return null
    }
    return json.optString(key)
}
