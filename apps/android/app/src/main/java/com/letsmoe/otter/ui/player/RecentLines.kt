package com.letsmoe.otter.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.cast.overlay.CaptionCue
import com.letsmoe.otter.ui.components.pressable

private const val RECENT_LINE_COUNT = 4

/** The last few lines said up to [seconds] of the title, oldest first; [delaySeconds] as the captions are shifted. */
fun recentLines(cues: List<CaptionCue>, seconds: Double, delaySeconds: Double): List<CaptionCue> {
    val captionSeconds = seconds - delaySeconds
    return cues.filter { cue -> cue.startSeconds <= captionSeconds }.takeLast(RECENT_LINE_COUNT)
}

/** "What did they say?" while casting: the latest lines, the newest brightest; tapping one plays it again. */
@Composable
fun RecentLines(lines: List<CaptionCue>, onReplay: (CaptionCue) -> Unit, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) {
        return
    }
    Column(modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Just said · tap a line to hear it again", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f))
        lines.forEachIndexed { index, line ->
            val newest = index == lines.lastIndex
            Text(
                line.text,
                Modifier.pressable { onReplay(line) },
                textAlign = TextAlign.Center,
                fontSize = lineSize(newest),
                fontWeight = lineWeight(newest),
                color = Color.White.copy(alpha = lineAlpha(index, lines.size)),
            )
        }
    }
}

private fun lineSize(newest: Boolean): TextUnit {
    if (newest) {
        return 17.sp
    }
    return 15.sp
}

private fun lineWeight(newest: Boolean): FontWeight {
    if (newest) {
        return FontWeight.SemiBold
    }
    return FontWeight.Normal
}

// Older lines fade: 0.35 for the oldest up to 0.95 for the newest.
private fun lineAlpha(index: Int, count: Int): Float {
    if (count <= 1) {
        return 0.95f
    }
    return 0.35f + 0.6f * index / (count - 1)
}
