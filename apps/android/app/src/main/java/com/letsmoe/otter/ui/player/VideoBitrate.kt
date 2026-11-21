package com.letsmoe.otter.ui.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks

/** Bits per second of the video track that plays; 0 when the stream does not say. */
fun selectedVideoBitrate(tracks: Tracks): Int {
    val group = tracks.groups.firstOrNull { candidate -> candidate.type == C.TRACK_TYPE_VIDEO && candidate.isSelected }
    if (group == null) {
        return 0
    }
    val index = (0 until group.length).firstOrNull { candidate -> group.isTrackSelected(candidate) }
    if (index == null) {
        return 0
    }
    return declaredBitrate(group.getTrackFormat(index))
}

private fun declaredBitrate(format: Format): Int {
    if (format.averageBitrate != Format.NO_VALUE) {
        return format.averageBitrate
    }
    if (format.peakBitrate != Format.NO_VALUE) {
        return format.peakBitrate
    }
    return 0
}
