package com.letsmoe.otter.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.settings.DEFAULT_BEDTIME_MINUTES
import com.letsmoe.otter.settings.PlaybackSettings
import com.letsmoe.otter.ui.components.pressable
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** The bedtime switch and, while it is on, the time it starts. */
@Composable
fun BedtimeRows() {
    var bedtimeMinutes by remember { mutableStateOf(PlaybackSettings.bedtimeMinutes) }
    var picking by remember { mutableStateOf(false) }
    val description = "After it, every episode stops at its end and the volume is evened out"
    SettingsSwitchRow("Bedtime", description, bedtimeMinutes != null) { enabled ->
        bedtimeMinutes = bedtimeFor(enabled)
        PlaybackSettings.bedtimeMinutes = bedtimeMinutes
    }
    val chosen = bedtimeMinutes
    if (chosen != null) {
        SettingsDivider()
        BedtimeTimeRow(chosen) { picking = true }
    }
    if (picking && chosen != null) {
        BedtimePickerDialog(
            initialMinutes = chosen,
            onPick = { picked ->
                bedtimeMinutes = picked
                PlaybackSettings.bedtimeMinutes = picked
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

private fun bedtimeFor(enabled: Boolean): Int? {
    if (enabled) {
        return DEFAULT_BEDTIME_MINUTES
    }
    return null
}

@Composable
private fun BedtimeTimeRow(minutes: Int, onClick: () -> Unit) {
    Row(
        Modifier.pressable(onClick = onClick).fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Bedtime starts at", fontSize = 14.sp)
        Text(timeLabel(minutes), fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

/** The time of day for [minutes] after midnight, in the phone's format. */
fun timeLabel(minutes: Int): String {
    return LocalTime.of(minutes / 60, minutes % 60).format(timeFormat)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedtimePickerDialog(initialMinutes: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val uses24Hours = DateFormat.is24HourFormat(LocalContext.current)
    val pickerState = rememberTimePickerState(initialMinutes / 60, initialMinutes % 60, is24Hour = uses24Hours)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(pickerState.hour * 60 + pickerState.minute) }) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(pickerState)
            }
        },
    )
}
