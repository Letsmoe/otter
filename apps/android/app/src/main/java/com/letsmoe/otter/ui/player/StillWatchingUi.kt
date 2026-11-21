package com.letsmoe.otter.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.ui.components.pressable

/** Reports every touch on the content to [onTouch] without taking it from the content. */
fun Modifier.reportsTouches(onTouch: () -> Unit): Modifier {
    return this.pointerInput(onTouch) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                onTouch()
            }
        }
    }
}

/** "Still watching?" in the middle of the player while the next episode waits for an answer. */
@Composable
fun BoxScope.StillWatchingPrompt(asking: Boolean, onKeepWatching: () -> Unit, onStop: () -> Unit) {
    AnimatedVisibility(asking, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
        StillWatchingCard(onKeepWatching, onStop, Modifier.width(320.dp).playerGlass(RoundedCornerShape(18.dp)).padding(18.dp))
    }
}

/** The question with its two answers; shared by the player and the TV remote. */
@Composable
fun StillWatchingCard(onKeepWatching: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Still watching?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Nothing was touched for a while. Playback stops in 30 seconds unless you keep watching.",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.75f),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnswerButton("Stop", Color.White.copy(alpha = 0.12f), onStop, Modifier.weight(1f))
            AnswerButton("Keep watching", Color.White.copy(alpha = 0.3f), onKeepWatching, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AnswerButton(label: String, fill: Color, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.pressable(onClick = onClick).height(42.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
