package com.letsmoe.otter.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.ui.components.FilterSelect
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.player.audio.AUDIO_DELAY_STEP_MILLISECONDS
import com.letsmoe.otter.ui.player.audio.audioDelayLabel

private const val NO_SECOND_LANGUAGE = -1

/** What the captions column offers besides the languages: timing and a second language. */
class CaptionTweaksUi(
    val state: CaptionExtrasState,
    val secondOptions: List<TrackOption>,
    val selectedSecondIndex: Int,
    val onNudge: (Double) -> Unit,
    val onSecond: (Int) -> Unit,
)

@Composable
fun CaptionTweakRows(tweaks: CaptionTweaksUi) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CaptionTimingRow(tweaks.state.delaySeconds, tweaks.onNudge)
        if (tweaks.secondOptions.isNotEmpty()) {
            val options = listOf(NO_SECOND_LANGUAGE to "Off") + tweaks.secondOptions.map { option -> option.index to option.label }
            FilterSelect("Second captions", options, tweaks.selectedSecondIndex, tweaks.onSecond)
        }
    }
}

// "Timing  [−]  +0.5 s  [+]": earlier and later in half seconds.
@Composable
private fun CaptionTimingRow(delaySeconds: Double, onNudge: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Timing", fontWeight = FontWeight.SemiBold)
            Text("When captions come too early or late", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        TimingButton("−") { onNudge(-CAPTION_DELAY_STEP_SECONDS) }
        Text(captionDelayLabel(delaySeconds), Modifier.width(56.dp), textAlign = TextAlign.Center, fontSize = 14.sp)
        TimingButton("+") { onNudge(CAPTION_DELAY_STEP_SECONDS) }
    }
}

/** "Audio delay  [−]  +100 ms  [+]": the TV's sound earlier and later, in 50 ms steps. */
@Composable
fun AudioDelayRow(delayMillis: Int, onNudge: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Audio delay", fontWeight = FontWeight.SemiBold)
            Text("Sound behind the picture: −; ahead of it: +", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        TimingButton("−") { onNudge(-AUDIO_DELAY_STEP_MILLISECONDS) }
        Text(audioDelayLabel(delayMillis), Modifier.width(64.dp), textAlign = TextAlign.Center, fontSize = 14.sp)
        TimingButton("+") { onNudge(AUDIO_DELAY_STEP_MILLISECONDS) }
    }
}

@Composable
internal fun TimingButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier.pressable(onClick = onClick).size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
