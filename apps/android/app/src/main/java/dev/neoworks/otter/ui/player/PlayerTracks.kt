package dev.neoworks.otter.ui.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import java.util.Locale

class TrackEntry(val label: String, val group: Tracks.Group, val trackIndex: Int, val selected: Boolean)

/** Audio and subtitle tracks of the current stream. */
class TrackCatalog(val audio: List<TrackEntry>, val subtitles: List<TrackEntry>) {

    val audioOptions: List<TrackOption>
        get() = audio.mapIndexed { index, entry -> TrackOption(index, entry.label) }

    val subtitleOptions: List<TrackOption>
        get() = subtitles.mapIndexed { index, entry -> TrackOption(index, entry.label) }

    /** Index of the playing audio track. */
    val selectedAudioIndex: Int
        get() = audio.indexOfFirst { entry -> entry.selected }.coerceAtLeast(0)

    /** Index of the shown subtitles, -1 when off. */
    val selectedSubtitleIndex: Int
        get() = subtitles.indexOfFirst { entry -> entry.selected }

    companion object {
        val Empty = TrackCatalog(emptyList(), emptyList())
    }
}

fun readTrackCatalog(tracks: Tracks): TrackCatalog {
    return TrackCatalog(
        audio = entriesOfType(tracks, C.TRACK_TYPE_AUDIO),
        subtitles = entriesOfType(tracks, C.TRACK_TYPE_TEXT),
    )
}

private fun entriesOfType(tracks: Tracks, trackType: Int): List<TrackEntry> {
    val entries = mutableListOf<TrackEntry>()
    for (group in tracks.groups) {
        if (group.type != trackType) {
            continue
        }
        for (trackIndex in 0 until group.length) {
            if (group.isTrackSupported(trackIndex)) {
                entries.add(TrackEntry(trackLabel(group, trackIndex, entries.size), group, trackIndex, group.isTrackSelected(trackIndex)))
            }
        }
    }
    return entries
}

private fun trackLabel(group: Tracks.Group, trackIndex: Int, position: Int): String {
    val format = group.getTrackFormat(trackIndex)
    if (!format.label.isNullOrEmpty()) {
        return format.label.orEmpty()
    }
    val language = format.language
    if (!language.isNullOrEmpty() && language != C.LANGUAGE_UNDETERMINED) {
        return Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH).ifEmpty { language }
    }
    return "Track ${position + 1}"
}

fun selectAudioTrack(player: Player, entry: TrackEntry) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(entry.group.mediaTrackGroup, entry.trackIndex))
        .build()
}

/** Shows the subtitles of [entry], or none for null. */
fun selectSubtitleTrack(player: Player, entry: TrackEntry?) {
    val builder = player.trackSelectionParameters.buildUpon()
    if (entry == null) {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        builder.setOverrideForType(TrackSelectionOverride(entry.group.mediaTrackGroup, entry.trackIndex))
    }
    player.trackSelectionParameters = builder.build()
}
