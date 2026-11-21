package com.letsmoe.otter.ui.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import com.letsmoe.otter.tonight.TonightEntry
import com.letsmoe.otter.tonight.TonightQueue
import com.letsmoe.otter.ui.components.PillButton

/**
 * Lines up [entry] (the film, or the series' next episode) for tonight, and plays the queue from
 * its first title. Titles play in order, on the phone or a TV.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TonightRow(entry: TonightEntry?, onPlay: (PlayRequest) -> Unit) {
    val queue = TonightQueue.shared
    val queued by queue.entries.collectAsState()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (entry != null) {
            ToggleQueuedPill(entry, queued.any { candidate -> candidate.mediaId == entry.mediaId })
        }
        if (queued.isNotEmpty()) {
            PillButton("Play tonight (${queued.size})", Modifier, enabled = true, filled = false) {
                onPlay(PlayRequest(queued.first().mediaId))
            }
            PillButton("Clear", Modifier, enabled = true, filled = false) { queue.clear() }
        }
    }
}

@Composable
private fun ToggleQueuedPill(entry: TonightEntry, isQueued: Boolean) {
    val queue = TonightQueue.shared
    if (isQueued) {
        PillButton("Remove from tonight", Modifier, enabled = true, filled = false) { queue.remove(entry.mediaId) }
        return
    }
    PillButton("Add to tonight", Modifier, enabled = true, filled = false) { queue.add(entry) }
}
