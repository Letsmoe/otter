package dev.neoworks.otter.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Check
import dev.neoworks.otter.ui.components.OtterSwitch

@Composable
fun SpeedSheetContent(speed: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
        Text("Playback Speed", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        SpeedSlider(playbackSpeeds, speed, onChange)
    }
}

@Composable
fun SourcesSheetContent(sources: List<PlayerSource>, currentSourceId: String?, onChoose: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sources", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        LazyColumn {
            items(sources, key = { source -> source.id }) { source ->
                SourceRow(source, source.id == currentSourceId) { onChoose(source.id) }
            }
        }
    }
}

@Composable
private fun SourceRow(source: PlayerSource, current: Boolean, onClick: () -> Unit) {
    var color = Color.White.copy(alpha = 0.55f)
    var weight = FontWeight.Normal
    if (current) {
        color = Color.White
        weight = FontWeight.Bold
    }
    Row(
        Modifier.fillMaxWidth().clickableWithoutRipple(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckSlot(current)
        Column(Modifier.weight(1f)) {
            Text(source.label, color = color, fontWeight = weight, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (source.details.isNotEmpty()) {
                Text(source.details, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            source.plugin,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CheckSlot(checked: Boolean) {
    Box(Modifier.width(20.dp)) {
        if (checked) {
            Icon(PhosphorIcons.Regular.Check, contentDescription = "Playing", modifier = Modifier.size(18.dp))
        }
    }
}

/** Audio and subtitle tracks of the current stream, in two columns. Mirrors TracksSheet.svelte. */
@Composable
fun TracksSheetContent(state: PlayerUiState, onAudio: (Int) -> Unit, onSubtitle: (Int) -> Unit, onVoiceBoost: () -> Unit) {
    Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        TrackColumn("Audio", state.audioTracks, state.selectedAudioIndex, "Original", onAudio, Modifier.weight(1f)) {
            if (state.canBoostVoice) {
                VoiceBoostRow(state.voiceBoost, onVoiceBoost)
            }
        }
        val subtitleOptions = listOf(TrackOption(-1, "Off")) + state.subtitleTracks
        TrackColumn("Subtitles", subtitleOptions, state.selectedSubtitleIndex, null, onSubtitle, Modifier.weight(1f))
    }
}

@Composable
private fun VoiceBoostRow(enabled: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Voice boost", fontWeight = FontWeight.SemiBold)
            Text("Dialogue over music and effects", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        OtterSwitch(enabled) { onToggle() }
    }
}

@Composable
private fun TrackColumn(
    heading: String,
    options: List<TrackOption>,
    selectedIndex: Int,
    emptyText: String?,
    onChoose: (Int) -> Unit,
    modifier: Modifier,
    extra: @Composable () -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(heading, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        extra()
        if (options.isEmpty() && emptyText != null) {
            Text(emptyText, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(vertical = 10.dp))
        }
        LazyColumn {
            items(options, key = { option -> option.index }) { option ->
                TrackRow(option.label, option.index == selectedIndex) { onChoose(option.index) }
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var color = Color.White.copy(alpha = 0.55f)
    var weight = FontWeight.Normal
    if (selected) {
        color = Color.White
        weight = FontWeight.Bold
    }
    Row(
        Modifier.fillMaxWidth().clickableWithoutRipple(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckSlot(selected)
        Text(label, color = color, fontWeight = weight)
    }
}
