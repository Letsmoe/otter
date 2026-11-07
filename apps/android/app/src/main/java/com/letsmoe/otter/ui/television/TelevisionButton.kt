package com.letsmoe.otter.ui.television

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.adamglin.phosphoricons.regular.Screencast
import com.adamglin.phosphoricons.regular.Television
import com.letsmoe.otter.cast.TelevisionConnection
import com.letsmoe.otter.cast.TelevisionTarget
import com.letsmoe.otter.ui.components.GlassBottomSheet
import com.letsmoe.otter.ui.components.PRESSED_SCALE_LARGE
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.components.frostedGlass
import com.letsmoe.otter.ui.components.popIn
import com.letsmoe.otter.ui.components.squishOnChange
import com.letsmoe.otter.ui.components.wiggleOnChange
import androidx.compose.runtime.mutableIntStateOf
import com.letsmoe.otter.ui.player.TelevisionPlayback
import com.letsmoe.otter.ui.player.TelevisionSheetContent
import com.letsmoe.otter.ui.theme.OtterColors

/** Not connected: picks a TV. Connected: opens the remote. */
@Composable
fun TelevisionButton(modifier: Modifier = Modifier, glass: Boolean = false, iconSize: Dp = 22.dp) {
    val target by TelevisionConnection.target.collectAsStateWithLifecycle()
    var pickerOpen by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    val pressableModifier = modifier.pressable {
        tapCount += 1
        if (target == null) {
            pickerOpen = true
        } else {
            TelevisionPlayback.openRemote()
        }
    }
    var shape = pressableModifier.size(40.dp).clip(CircleShape)
    if (glass) {
        shape = pressableModifier.size(40.dp).frostedGlass(CircleShape)
    }
    Box(shape, contentAlignment = Alignment.Center) {
        // Wiggles on tap; connecting squashes it and pops the green dot in.
        Icon(
            PhosphorIcons.Regular.Screencast,
            contentDescription = "Play on TV",
            modifier = Modifier.wiggleOnChange(tapCount).squishOnChange(target != null).size(iconSize),
        )
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
            .popIn()
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
            .padding(horizontal = 12.dp)
            .pressable(pressedScale = PRESSED_SCALE_LARGE, onClick = TelevisionPlayback::openRemote)
            .fillMaxWidth()
            .frostedGlass(RoundedCornerShape(20.dp))
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
        Box(Modifier.pressable(onClick = viewModel::togglePlay).size(32.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
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
