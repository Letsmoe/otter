package com.letsmoe.otter.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.settings.DEFAULT_NIGHT_VOLUME_LIMIT
import com.letsmoe.otter.settings.MINIMUM_NIGHT_VOLUME_LIMIT
import com.letsmoe.otter.settings.PlaybackSettings
import com.letsmoe.otter.settings.TELEVISION_VOLUME_RANGE
import kotlin.math.roundToInt

/** The switch for the TV's night limit and, while it is on, the loudest volume. Shown with the bedtime. */
@Composable
fun NightVolumeRows() {
    var limit by remember { mutableStateOf(PlaybackSettings.nightVolumeLimit) }
    val description = "After bedtime the TV can't get louder than this, not even with its own remote"
    SettingsSwitchRow("Limit the TV's volume", description, limit != null) { enabled ->
        limit = limitFor(enabled)
        PlaybackSettings.nightVolumeLimit = limit
    }
    val chosen = limit
    if (chosen != null) {
        SettingsDivider()
        NightVolumeSlider(
            volume = chosen,
            onChange = { changed -> limit = changed },
            onChangeFinished = { PlaybackSettings.nightVolumeLimit = limit },
        )
    }
}

private fun limitFor(enabled: Boolean): Int? {
    if (enabled) {
        return DEFAULT_NIGHT_VOLUME_LIMIT
    }
    return null
}

@Composable
private fun NightVolumeSlider(volume: Int, onChange: (Int) -> Unit, onChangeFinished: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Loudest", fontSize = 14.sp)
        Slider(
            value = volume.toFloat(),
            onValueChange = { changed -> onChange(changed.roundToInt()) },
            onValueChangeFinished = onChangeFinished,
            valueRange = MINIMUM_NIGHT_VOLUME_LIMIT.toFloat()..TELEVISION_VOLUME_RANGE.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(volume.toString(), fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
    }
}
