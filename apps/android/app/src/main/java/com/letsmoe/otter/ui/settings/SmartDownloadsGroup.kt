package com.letsmoe.otter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import com.letsmoe.otter.downloads.SmartDownloadScheduler
import com.letsmoe.otter.downloads.SmartDownloadStore
import com.letsmoe.otter.downloads.SmartQuality
import com.letsmoe.otter.downloads.playtimeHours
import com.letsmoe.otter.ui.components.FilterSelect
import kotlin.math.roundToInt

private const val MINIMUM_BUDGET_GIGABYTES = 2
private const val MAXIMUM_BUDGET_GIGABYTES = 100

/** "About 10 hours of video", rounded to what a person says. */
fun playtimeLabel(gigabytes: Int, quality: SmartQuality): String {
    val hours = playtimeHours(gigabytes, quality)
    if (hours < 1) {
        return "about ${(hours * 60).roundToInt()} minutes of video"
    }
    val rounded = (hours * 2).roundToInt() / 2.0
    if (rounded == 1.0) {
        return "about 1 hour of video"
    }
    if (rounded == rounded.toInt().toDouble()) {
        return "about ${rounded.toInt()} hours of video"
    }
    return "about $rounded hours of video"
}

/** Smart Downloads: a storage budget, its playtime, and the quality to save in. */
@Composable
fun SmartDownloadsGroup() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SmartDownloadStore.enabled) }
    var budget by remember { mutableStateOf(SmartDownloadStore.budgetGigabytes) }
    var quality by remember { mutableStateOf(SmartDownloadStore.quality) }
    val footer = "On Wi-Fi, while charging or idle, Otter keeps the next episodes of series you're watching and a few " +
        "recommended films downloaded, and deletes them once watched. Downloads you made yourself are never touched."
    SettingsGroup("Smart Downloads", footer = footer) {
        SettingsSwitchRow("Smart Downloads", "Keeps what you'll watch next ready offline", enabled) { checked ->
            enabled = checked
            SmartDownloadStore.enabled = checked
            SmartDownloadScheduler.apply(context)
        }
        if (enabled) {
            SettingsDivider()
            QualityRow(quality) { chosen ->
                quality = chosen
                SmartDownloadStore.quality = chosen
            }
            SettingsDivider()
            BudgetRow(budget, quality, onChange = { changed -> budget = changed }, onChangeFinished = { SmartDownloadStore.budgetGigabytes = budget })
        }
    }
}

@Composable
private fun QualityRow(quality: SmartQuality, onChoose: (SmartQuality) -> Unit) {
    val options = SmartQuality.entries.map { option -> option to option.label }
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Quality", fontSize = 14.sp)
        FilterSelect("Quality", options, quality, onChoose, showLabel = false)
    }
}

@Composable
private fun BudgetRow(budget: Int, quality: SmartQuality, onChange: (Int) -> Unit, onChangeFinished: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Storage budget", fontSize = 14.sp)
            Text("$budget GB", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Slider(
            value = budget.toFloat(),
            onValueChange = { changed -> onChange(changed.roundToInt()) },
            onValueChangeFinished = onChangeFinished,
            valueRange = MINIMUM_BUDGET_GIGABYTES.toFloat()..MAXIMUM_BUDGET_GIGABYTES.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
        )
        Text("That is ${playtimeLabel(budget, quality)} at ${quality.label}.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
    }
}
