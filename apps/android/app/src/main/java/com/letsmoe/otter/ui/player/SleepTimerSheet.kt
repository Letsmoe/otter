package com.letsmoe.otter.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.ui.components.pressable

/** What the sleep timer is set to, for the sheet's marks. */
enum class SleepChoice { OFF, MINUTES, END_OF_EPISODE }

fun sleepChoiceOf(run: SleepTimerRun?, stopsAfterEpisode: Boolean): SleepChoice {
    if (run != null) {
        return SleepChoice.MINUTES
    }
    if (stopsAfterEpisode) {
        return SleepChoice.END_OF_EPISODE
    }
    return SleepChoice.OFF
}

fun sleepMinutesLabel(minutes: Int): String {
    if (minutes == 60) {
        return "1 hour"
    }
    return "$minutes minutes"
}

/** Off, 15, 30 or 60 minutes, or the end of the episode; the sound fades out over the last 20 seconds. */
@Composable
fun SleepTimerSheetContent(
    run: SleepTimerRun?,
    stopsAfterEpisode: Boolean,
    canStopAfterEpisode: Boolean,
    onStart: (Int) -> Unit,
    onEndOfEpisode: () -> Unit,
    onOff: () -> Unit,
) {
    val choice = sleepChoiceOf(run, stopsAfterEpisode)
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        Text("Sleep timer", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        SleepRow("Off", choice == SleepChoice.OFF, onOff)
        for (minutes in sleepTimerMinutes) {
            SleepRow(sleepMinutesLabel(minutes), choice == SleepChoice.MINUTES && run?.minutes == minutes) { onStart(minutes) }
        }
        if (canStopAfterEpisode) {
            SleepRow("End of episode", choice == SleepChoice.END_OF_EPISODE, onEndOfEpisode)
        }
        Text(
            "The sound fades out over the last 20 seconds, then playback pauses.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun SleepRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var color = Color.White.copy(alpha = 0.6f)
    var weight = FontWeight.Normal
    if (selected) {
        color = Color.White
        weight = FontWeight.Bold
    }
    Row(Modifier.pressable(onClick = onClick).fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, color = color, fontWeight = weight, modifier = Modifier.weight(1f))
        if (selected) {
            Text("On", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
        }
    }
}
