package com.letsmoe.otter.ui.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.ui.components.pressable

/** What the page's rewatch row shows and does. */
class RewatchBarState(val offer: RewatchOffer, val onStart: () -> Unit, val onStop: () -> Unit) {
    companion object {
        val None = RewatchBarState(RewatchOffer.NONE, {}, {})
    }
}

private val mutedColor = Color.White.copy(alpha = 0.55f)

/**
 * Under Play: "Rewatch" on a finished title, and while a rewatch goes a note that Continue
 * watching is left alone, with a way to stop it.
 */
@Composable
fun RewatchBar(state: RewatchBarState) {
    when (state.offer) {
        RewatchOffer.NONE -> Unit
        RewatchOffer.START -> StartRewatch(state.onStart)
        RewatchOffer.RUNNING -> RunningRewatch(state.onStop)
    }
}

@Composable
private fun StartRewatch(onStart: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PillButton("Rewatch", Modifier.fillMaxWidth(), enabled = true, filled = false, onClick = onStart)
        Text(
            "Starts over with progress of its own. Continue Watching and recommendations stay as they are.",
            fontSize = 12.sp,
            color = mutedColor,
        )
    }
}

@Composable
private fun RunningRewatch(onStop: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Rewatching · Continue Watching stays as it is",
            fontSize = 12.sp,
            color = mutedColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Stop rewatch",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.pressable(onClick = onStop),
        )
    }
}
