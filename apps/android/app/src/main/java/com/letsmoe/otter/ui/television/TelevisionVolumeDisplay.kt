package com.letsmoe.otter.ui.television

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.SpeakerHigh
import com.letsmoe.otter.cast.TelevisionVolumeChange
import com.letsmoe.otter.cast.TelevisionVolumeKeys
import com.letsmoe.otter.ui.components.frostedGlass
import kotlinx.coroutines.delay

private const val SHOWN_MILLISECONDS = 1500L

/** A small glass pill under the status bar that shows the TV's volume while the phone's buttons change it. */
@Composable
fun TelevisionVolumeDisplay(modifier: Modifier = Modifier) {
    val change by TelevisionVolumeKeys.change.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf<TelevisionVolumeChange?>(null) }
    LaunchedEffect(change) {
        val latest = change ?: return@LaunchedEffect
        shown = latest
        delay(SHOWN_MILLISECONDS)
        shown = null
    }
    val volume = shown?.volume ?: return
    Row(
        modifier.statusBarsPadding().padding(top = 10.dp).frostedGlass(CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Regular.SpeakerHigh, contentDescription = "TV volume", modifier = Modifier.size(20.dp))
        VolumeBar(volume)
        Text(volume.toString(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(28.dp))
    }
}

@Composable
private fun VolumeBar(volume: Int) {
    Box(Modifier.width(120.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(volume / 100f).background(Color.White))
    }
}
