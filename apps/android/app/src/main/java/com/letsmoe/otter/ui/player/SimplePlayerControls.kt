package com.letsmoe.otter.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Pause
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.regular.ArrowCounterClockwise
import com.adamglin.phosphoricons.regular.X
import com.letsmoe.otter.ui.components.PRESSED_SCALE_LARGE
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.simple.SimpleBigButton
import com.letsmoe.otter.ui.simple.SimpleSize

private val bottomFade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))

/**
 * The player in simple mode: three big, labelled buttons that never hide. No scrubbing, no
 * menus, no taps on the picture; the title starts by itself on the best link.
 */
@Composable
fun SimplePlayerControls(isPlaying: Boolean, onTogglePlay: () -> Unit, onBackTenSeconds: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = 160.dp).background(bottomFade))
        CloseButton(onClose, Modifier.align(Alignment.TopEnd).padding(20.dp))
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(56.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            LabelledControl(PhosphorIcons.Regular.ArrowCounterClockwise, "Back 10 seconds", onBackTenSeconds)
            PlayPauseControl(isPlaying, onTogglePlay)
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .pressable(pressedScale = PRESSED_SCALE_LARGE, onClick = onClose)
            .heightIn(min = 72.dp)
            .playerGlass(CircleShape)
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Regular.X, contentDescription = null, modifier = Modifier.size(36.dp))
        Text("Close", fontSize = SimpleSize.button, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlayPauseControl(isPlaying: Boolean, onTogglePlay: () -> Unit) {
    if (isPlaying) {
        LabelledControl(PhosphorIcons.Fill.Pause, "Pause", onTogglePlay)
        return
    }
    LabelledControl(PhosphorIcons.Fill.Play, "Play", onTogglePlay)
}

@Composable
private fun LabelledControl(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.pressable(pressedScale = PRESSED_SCALE_LARGE, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(96.dp).playerGlass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(56.dp))
        }
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** Shown when playback ends: what she watched and the only two things to do next. */
@Composable
fun SimpleEndScreen(title: String, hasNextEpisode: Boolean, onNextEpisode: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(title, fontSize = SimpleSize.heading, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        if (hasNextEpisode) {
            SimpleBigButton("Next episode", PhosphorIcons.Fill.Play, filled = true, onClick = onNextEpisode, modifier = Modifier.widthIn(max = 520.dp))
        }
        SimpleBigButton("Close", PhosphorIcons.Regular.X, filled = !hasNextEpisode, onClick = onClose, modifier = Modifier.widthIn(max = 520.dp))
    }
}

/** A failure in plain words with the one thing worth trying. */
@Composable
fun SimpleErrorNotice(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Sorry, this does not play right now.", fontSize = SimpleSize.itemTitle, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        SimpleBigButton("Try again", PhosphorIcons.Regular.ArrowCounterClockwise, filled = true, onClick = onRetry, modifier = Modifier.widthIn(max = 520.dp))
    }
}
