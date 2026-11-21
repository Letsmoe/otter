package com.letsmoe.otter.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Check
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.FocusableSurface
import com.letsmoe.otter.ui.player.PlayerEpisode
import com.letsmoe.otter.ui.player.PlayerSource
import com.letsmoe.otter.ui.player.PlayerUiState
import com.letsmoe.otter.ui.player.episodeCode
import com.letsmoe.otter.ui.player.episodeName

private val PANEL_WIDTH = 340.dp

/** A list on the right over the darkened video: episodes, audio and captions, or sources. */
@Composable
fun PlayerSidePanel(panel: PlayerPanel, state: PlayerUiState, viewModel: TvPlayerViewModel, chrome: PlayerChrome, modifier: Modifier) {
    val close = { chrome.panel = null }
    LazyColumn(
        modifier = modifier
            .width(PANEL_WIDTH)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.88f)),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (panel) {
            PlayerPanel.EPISODES -> episodeRows(state, onPick = { episode ->
                viewModel.openEpisode(episode.id)
                close()
            })
            PlayerPanel.AUDIO -> audioRows(state, viewModel)
            PlayerPanel.SOURCES -> sourceRows(state, onPick = { source ->
                viewModel.chooseSource(source.id)
                close()
            })
        }
    }
}

private fun LazyListScope.panelHeading(title: String) {
    item(key = "heading-$title") {
        Text(title, fontSize = TvType.sectionTitle, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    }
}

private fun LazyListScope.episodeRows(state: PlayerUiState, onPick: (PlayerEpisode) -> Unit) {
    panelHeading("Episodes")
    items(state.episodes, key = { episode -> episode.id }) { episode ->
        val label = "${episodeCode(episode.seasonNumber, episode.episodeNumber)}  ${episodeName(episode.name, episode.episodeNumber)}"
        PanelRow(label, detail = null, selected = episode.id == state.mediaId, onClick = { onPick(episode) })
    }
}

private fun LazyListScope.audioRows(state: PlayerUiState, viewModel: TvPlayerViewModel) {
    if (state.audioTracks.size > 1) {
        panelHeading("Audio")
        items(state.audioTracks, key = { track -> "audio-${track.index}" }) { track ->
            PanelRow(track.label, detail = null, selected = track.index == state.selectedAudioIndex, onClick = { viewModel.chooseAudio(track.index) })
        }
    }
    panelHeading("Subtitles")
    item(key = "subtitles-off") {
        PanelRow("Off", detail = null, selected = state.selectedSubtitleIndex < 0, onClick = { viewModel.chooseSubtitle(-1) })
    }
    items(state.subtitleTracks, key = { track -> "subtitle-${track.index}" }) { track ->
        PanelRow(track.label, detail = null, selected = track.index == state.selectedSubtitleIndex, onClick = { viewModel.chooseSubtitle(track.index) })
    }
    panelHeading("Sound")
    item(key = "voice-boost") {
        PanelRow("Voice boost", detail = "Dialogue clearer over music and effects", selected = state.voiceBoost, onClick = viewModel::toggleVoiceBoost)
    }
    item(key = "even-volume") {
        PanelRow("Even volume", detail = "Quiet scenes louder, loud ones softer", selected = state.evenVolume, onClick = viewModel::toggleEvenVolume)
    }
}

private fun LazyListScope.sourceRows(state: PlayerUiState, onPick: (PlayerSource) -> Unit) {
    panelHeading("Sources")
    if (state.sources.isEmpty()) {
        item(key = "no-sources") {
            Text(noSourcesText(state), fontSize = TvType.detail, color = TvColors.secondaryContent)
        }
    }
    items(state.sources, key = { source -> source.id }) { source ->
        val selected = source.id == selectedSourceId(state)
        PanelRow(source.label, detail = sourceDetailLine(source), selected = selected, onClick = { onPick(source) })
    }
}

private fun noSourcesText(state: PlayerUiState): String {
    if (state.searchingSources) {
        return "Searching…"
    }
    return "No sources found."
}

// The link just picked is marked while it resolves, else the playing one.
private fun selectedSourceId(state: PlayerUiState): String? {
    val chosen = state.chosenSourceId
    if (chosen != null) {
        return chosen
    }
    return state.currentSourceId
}

// "1080p · de · ~2.4 MB/s · plugin", and a note on links the backend found dead.
private fun sourceDetailLine(source: PlayerSource): String {
    var line = "${source.details} · ${source.plugin}"
    if (source.playable == false) {
        line = "$line · didn't play last time"
    }
    return line
}

/** One choice: a check when it's the selected one, an optional line under it. The selected row has focus first. */
@Composable
private fun PanelRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (selected) {
            focus.requestFocus()
        }
    }
    FocusableSurface(onClick = onClick, focusedScale = 1.02f, modifier = Modifier.fillMaxWidth().focusRequester(focus)) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CheckMark(selected)
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = TvType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) {
                    Text(detail, fontSize = TvType.caption, color = TvColors.secondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CheckMark(selected: Boolean) {
    if (!selected) {
        Spacer(Modifier.size(16.dp))
        return
    }
    Icon(PhosphorIcons.Bold.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp))
}
