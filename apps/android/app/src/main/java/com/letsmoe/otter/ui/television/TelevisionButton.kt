package com.letsmoe.otter.ui.television

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Pause
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.regular.Television
import com.letsmoe.otter.cast.TelevisionConnection
import com.letsmoe.otter.cast.TelevisionTarget
import com.letsmoe.otter.ui.components.GlassBottomSheet
import com.letsmoe.otter.ui.components.frostedGlass
import com.letsmoe.otter.ui.player.TelevisionPlayback
import com.letsmoe.otter.ui.player.TelevisionSheetContent
import com.letsmoe.otter.ui.theme.OtterColors

/** Not connected: picks a TV. Connected: opens the remote. */
@Composable
fun TelevisionButton(modifier: Modifier = Modifier, iconSize: Dp = 22.dp) {
    val target by TelevisionConnection.target.collectAsStateWithLifecycle()
    var pickerOpen by remember { mutableStateOf(false) }
    Box(
        modifier.size(40.dp).clip(CircleShape).clickable {
            if (target == null) {
                pickerOpen = true
            } else {
                TelevisionPlayback.openRemote()
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(PhosphorIcons.Regular.Television, contentDescription = "Play on TV", modifier = Modifier.size(iconSize))
        if (target != null) {
            ConnectedDot(Modifier.align(Alignment.TopEnd))
        }
    }
    if (pickerOpen) {
        GlassBottomSheet(onDismiss = { pickerOpen = false }) {
            TelevisionSheetContent(onChoose = { renderer ->
                pickerOpen = false
                TelevisionConnection.connectDlna(renderer)
                TelevisionPlayback.openRemote()
            })
        }
    }
}

@Composable
private fun ConnectedDot(modifier: Modifier) {
    Box(
        modifier
            .offset(x = (-6).dp, y = 6.dp)
            .size(8.dp)
            .border(2.dp, Color.Black, CircleShape)
            .background(OtterColors.success, CircleShape),
    )
}

/** A slim glass bar above the tab bar while a TV is connected; tapping it opens the remote. */
@Composable
fun TelevisionNowPlayingBar(modifier: Modifier = Modifier) {
    val target by TelevisionConnection.target.collectAsStateWithLifecycle()
    val connected = target ?: return
    val playback by TelevisionPlayback.player.collectAsStateWithLifecycle()
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .frostedGlass(RoundedCornerShape(20.dp))
            .clickable(onClick = TelevisionPlayback::openRemote)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Regular.Television, contentDescription = null, modifier = Modifier.size(20.dp))
        val viewModel = playback
        if (viewModel == null) {
            BarText("Connected to ${connected.name}", Modifier.weight(1f))
            return@Row
        }
        val state by viewModel.state.collectAsStateWithLifecycle()
        BarText(barTitle(state.title, connected), Modifier.weight(1f))
        var icon = PhosphorIcons.Fill.Play
        if (state.isPlaying) {
            icon = PhosphorIcons.Fill.Pause
        }
        Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = viewModel::togglePlay), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = "Play or pause", modifier = Modifier.size(20.dp))
        }
    }
}

private fun barTitle(title: String, target: TelevisionTarget): String {
    if (title.isBlank()) {
        return "Starting on ${target.name}…"
    }
    return title
}

@Composable
private fun BarText(text: String, modifier: Modifier) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}
