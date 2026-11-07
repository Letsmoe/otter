package com.letsmoe.otter.ui.television

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Check
import com.letsmoe.otter.ui.components.PRESSED_SCALE_LARGE
import com.letsmoe.otter.ui.components.SubtleFill
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.player.PlayerUiState
import com.letsmoe.otter.ui.player.TrackOption

private val groupShape = RoundedCornerShape(20.dp)
private const val CAPTIONS_OFF_INDEX = -1

/**
 * The remote's captions page: one column of choices grouped like the remote's settings, "Off" first.
 * Audio shows only when the stream has more than one track to choose from.
 */
@Composable
fun TelevisionCaptionsContent(state: PlayerUiState, onAudio: (Int) -> Unit, onSubtitle: (Int) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Captions", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        val captionOptions = listOf(TrackOption(CAPTIONS_OFF_INDEX, "Off")) + state.subtitleTracks
        ChoiceGroup(captionOptions, state.selectedSubtitleIndex, onSubtitle)
        if (state.audioTracks.size > 1) {
            Text("Audio", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            ChoiceGroup(state.audioTracks, state.selectedAudioIndex, onAudio)
        }
    }
}

@Composable
private fun ChoiceGroup(options: List<TrackOption>, selectedIndex: Int, onChoose: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(SubtleFill, groupShape).padding(horizontal = 16.dp)) {
        options.forEachIndexed { position, option ->
            if (position > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
            }
            ChoiceRow(option.label, option.index == selectedIndex) { onChoose(option.index) }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var color = Color.White.copy(alpha = 0.75f)
    var weight = FontWeight.Normal
    if (selected) {
        color = Color.White
        weight = FontWeight.SemiBold
    }
    Row(
        Modifier.pressable(pressedScale = PRESSED_SCALE_LARGE, onClick = onClick).fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = color, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(PhosphorIcons.Bold.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp))
        }
    }
}
