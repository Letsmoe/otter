package com.letsmoe.otter.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.settings.DEFAULT_BEDTIME_MINUTES
import com.letsmoe.otter.ui.components.OtterSwitch
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.settings.BedtimePickerDialog
import com.letsmoe.otter.ui.settings.timeLabel

/** What only a child's profile has: the oldest age its titles may be rated for, and a fixed bedtime. */
@Composable
fun ChildOptions(editor: ProfileEditorViewModel) {
    var pickingBedtime by remember { mutableStateOf(false) }
    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    AgeLimitRow(editor.ageLimit, onPick = { age -> editor.ageLimit = age })
    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    BedtimeRow(
        bedtimeMinutes = editor.bedtimeMinutes,
        onToggle = { enabled -> editor.bedtimeMinutes = bedtimeFor(enabled) },
        onPickTime = { pickingBedtime = true },
    )
    val bedtime = editor.bedtimeMinutes
    if (pickingBedtime && bedtime != null) {
        BedtimePickerDialog(
            initialMinutes = bedtime,
            onPick = { picked ->
                editor.bedtimeMinutes = picked
                pickingBedtime = false
            },
            onDismiss = { pickingBedtime = false },
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
private fun AgeLimitRow(ageLimit: Int, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Age limit", fontWeight = FontWeight.SemiBold)
        Text(
            "Titles rated for older viewers, or without a rating, are hidden and can't be played. Uses the ratings of the profile's country (FSK in Germany).",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.55f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (age in AGE_LIMIT_CHOICES) {
                AgeChip(ageLabel(age), picked = age == ageLimit, onClick = { onPick(age) })
            }
        }
    }
}

private fun ageLabel(age: Int): String {
    if (age == 0) {
        return "Off"
    }
    return "Up to $age"
}

@Composable
private fun AgeChip(label: String, picked: Boolean, onClick: () -> Unit) {
    var background = Color.White.copy(alpha = 0.08f)
    var textAlpha = 0.7f
    if (picked) {
        background = Color.White.copy(alpha = 0.22f)
        textAlpha = 1f
    }
    Text(
        label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .alpha(textAlpha),
    )
}

@Composable
private fun BedtimeRow(bedtimeMinutes: Int?, onToggle: (Boolean) -> Unit, onPickTime: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Bedtime", fontWeight = FontWeight.SemiBold)
            Text(
                "After it, every title stops after its episode, and that can't be switched off.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.55f),
            )
            if (bedtimeMinutes != null) {
                Text(timeLabel(bedtimeMinutes), fontSize = 14.sp, modifier = Modifier.pressable(onClick = onPickTime).padding(vertical = 4.dp))
            }
        }
        OtterSwitch(bedtimeMinutes != null, onToggle)
    }
}
