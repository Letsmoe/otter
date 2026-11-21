package com.letsmoe.otter.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.media3.common.Player
import com.letsmoe.otter.cast.overlay.CaptionCue
import com.letsmoe.otter.cast.overlay.textAt
import com.letsmoe.otter.graphql.type.SubtitleStyle
import kotlinx.coroutines.delay

// SubtitleView's own bottom padding, as a fraction of the picture's height.
private const val SUBTITLE_BOTTOM_FRACTION = 0.08f
private const val SECOND_CAPTION_SCALE = 0.8f
// Room for two lines of the second language under the main captions.
private const val SECOND_CAPTION_ROOM_LINES = 2.1f
private const val CAPTION_POLL_MILLISECONDS = 100L
private const val OUTLINE_WIDTH_FRACTION = 0.14f

/** Where SubtitleView puts the main captions: higher while a second language sits under them. */
fun mainCaptionBottomFraction(look: CaptionLook, secondShown: Boolean): Float {
    if (!secondShown) {
        return SUBTITLE_BOTTOM_FRACTION
    }
    return SUBTITLE_BOTTOM_FRACTION + SECOND_CAPTION_ROOM_LINES * look.textFraction
}

/** Captions the phone draws itself: a second language (smaller, light grey) or a replay's (as the main ones). */
enum class DrawnCaptionRole(val scale: Float) {
    SECOND(SECOND_CAPTION_SCALE),
    REPLAY(1f),
}

/** Captions from [cues] at the bottom of the picture, in the profile's look. */
@Composable
fun DrawnCaptions(player: Player, cues: List<CaptionCue>, delaySeconds: Double, look: CaptionLook, role: DrawnCaptionRole) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cues, delaySeconds) {
        while (true) {
            text = cues.textAt(player.currentPosition / 1000.0 - delaySeconds)
            delay(CAPTION_POLL_MILLISECONDS)
        }
    }
    val shown = text ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        val bottom = height * SUBTITLE_BOTTOM_FRACTION
        val captionHeight = height * look.textFraction * role.scale
        val textSize = with(LocalDensity.current) { captionHeight.toSp() }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.8f).padding(bottom = bottom), contentAlignment = Alignment.BottomCenter) {
            CaptionText(shown, textSize, captionColor(look.style, role), CaptionEdge(look.style, captionHeight * OUTLINE_WIDTH_FRACTION))
        }
    }
}

/** How the text stands off the picture: the profile's style, with the outline this wide. */
private class CaptionEdge(val style: SubtitleStyle, val outline: Dp)

@Composable
private fun CaptionText(text: String, textSize: TextUnit, color: Color, edge: CaptionEdge) {
    val base = TextStyle(fontSize = textSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    if (edge.style == SubtitleStyle.BOX) {
        Text(text, Modifier.background(Color(CaptionColors.BOX)), style = base.copy(color = color))
        return
    }
    val strokeWidth = with(LocalDensity.current) { edge.outline.toPx() }
    Box {
        Text(text, style = base.copy(color = Color(CaptionColors.OUTLINE), drawStyle = Stroke(width = strokeWidth)))
        Text(text, style = base.copy(color = color))
    }
}

// A replay looks like the main captions; a second language is set apart from them, as on the TV.
private fun captionColor(style: SubtitleStyle, role: DrawnCaptionRole): Color {
    if (role == DrawnCaptionRole.REPLAY) {
        return mainColor(style)
    }
    if (style == SubtitleStyle.YELLOW) {
        return Color.White
    }
    return Color(CaptionColors.SECOND)
}

private fun mainColor(style: SubtitleStyle): Color {
    if (style == SubtitleStyle.YELLOW) {
        return Color(CaptionColors.YELLOW)
    }
    return Color.White
}
