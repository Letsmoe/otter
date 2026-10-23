package dev.neoworks.otter.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

val playbackSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)

private val thumbWidth = 40.dp
private val thumbHeight = 28.dp

/** Stepped speed slider: tap or drag to one of the stops. Mirrors SpeedSlider.svelte. */
@Composable
fun SpeedSlider(speeds: List<Float>, value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val stopIndex = speeds.indexOf(value).coerceAtLeast(0)
    val fraction by animateFloatAsState(stopIndex / (speeds.size - 1).toFloat(), tween(220), label = "speedThumb")
    Column(modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .speedGestures(speeds, onChange),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Color.White.copy(alpha = 0.9f)))
            }
            Box(Modifier.offset(x = maxWidth * fraction - thumbWidth / 2).size(thumbWidth, thumbHeight).clip(CircleShape).background(Color.White))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (speed in speeds) {
                SpeedLabel(speed, selected = speed == value) { onChange(speed) }
            }
        }
    }
}

@Composable
private fun SpeedLabel(speed: Float, selected: Boolean, onClick: () -> Unit) {
    var color = Color.White.copy(alpha = 0.5f)
    var weight = FontWeight.Normal
    if (selected) {
        color = Color.White
        weight = FontWeight.Bold
    }
    Column(Modifier.width(48.dp).clickableWithoutRipple(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${formatSpeed(speed)}x", fontSize = 12.sp, color = color, fontWeight = weight)
        if (speed == 1f) {
            Text("Normal", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

private fun Modifier.speedGestures(speeds: List<Float>, onChange: (Float) -> Unit): Modifier {
    return this
        .pointerInput(speeds) { detectTapGestures { offset -> onChange(stopAt(offset.x, size.width, speeds)) } }
        .pointerInput(speeds) {
            detectDragGestures { change, _ ->
                change.consume()
                onChange(stopAt(change.position.x, size.width, speeds))
            }
        }
}

private fun stopAt(x: Float, width: Int, speeds: List<Float>): Float {
    val fraction = (x / width).coerceIn(0f, 1f)
    return speeds[(fraction * (speeds.size - 1)).roundToInt()]
}
