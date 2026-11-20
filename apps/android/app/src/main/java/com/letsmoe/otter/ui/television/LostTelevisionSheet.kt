package com.letsmoe.otter.ui.television

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.WarningCircle
import com.letsmoe.otter.ui.components.GlassBottomSheet
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.ui.player.LostTelevision
import com.letsmoe.otter.ui.player.PlayerRequest
import com.letsmoe.otter.ui.player.TelevisionPlayback
import com.letsmoe.otter.ui.theme.OtterColors

/** Asks how to go on after the TV stopped answering and was disconnected; [onContinueOnPhone] opens the player screen. */
@Composable
fun LostTelevisionHost(onContinueOnPhone: (PlayerRequest) -> Unit) {
    val lostTelevision by TelevisionPlayback.lostTelevision.collectAsStateWithLifecycle()
    val lost = lostTelevision ?: return
    GlassBottomSheet(onDismiss = TelevisionPlayback::dismissLostTelevision) {
        LostTelevisionContent(lost, onContinueOnPhone)
    }
}

@Composable
private fun LostTelevisionContent(lost: LostTelevision, onContinueOnPhone: (PlayerRequest) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PhosphorIcons.Regular.WarningCircle, contentDescription = null, tint = OtterColors.error, modifier = Modifier.size(22.dp))
            Text("Lost the connection to ${lost.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "The TV stopped answering. It may be switched off or asleep, so it was disconnected.",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.7f),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            PillButton("Not now", Modifier, enabled = true, filled = false, onClick = TelevisionPlayback::dismissLostTelevision)
            PillButton("Continue on phone", Modifier, enabled = true, filled = true) {
                val request = TelevisionPlayback.continueLostOnPhone() ?: return@PillButton
                onContinueOnPhone(request)
            }
        }
    }
}
