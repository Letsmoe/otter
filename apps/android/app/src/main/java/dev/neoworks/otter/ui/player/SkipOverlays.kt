package dev.neoworks.otter.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private const val COUNTDOWN_TICK_MILLISECONDS = 100L

// Above the bottom bar while the controls show, near the edge otherwise.
private fun bottomOffset(controlsVisible: Boolean): Dp {
    if (controlsVisible) {
        return 112.dp
    }
    return 28.dp
}

/** "Skip Intro" / "Skip Recap" while one plays, bottom right. */
@Composable
fun BoxScope.SkipSegmentButton(jump: SkipJump?, controlsVisible: Boolean, onSkip: () -> Unit) {
    val modifier = Modifier.align(Alignment.BottomEnd)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(end = 24.dp, bottom = bottomOffset(controlsVisible))
    AnimatedVisibility(jump != null, modifier, enter = fadeIn(), exit = fadeOut()) {
        val label = jump?.label.orEmpty()
        Box(Modifier.height(44.dp).playerGlass(CircleShape).clickableWithoutRipple(onClick = onSkip).padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The next episode during the credits: countdown, still, Cancel and Play now. */
@Composable
fun BoxScope.UpNextOverlay(card: UpNextCard?, controlsVisible: Boolean, onCancel: () -> Unit, onPlayNow: () -> Unit) {
    val modifier = Modifier.align(Alignment.BottomEnd)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(end = 24.dp, bottom = bottomOffset(controlsVisible))
    AnimatedVisibility(card != null, modifier, enter = fadeIn() + slideInHorizontally { width -> width / 3 }, exit = fadeOut() + slideOutHorizontally { width -> width / 3 }) {
        if (card != null) {
            UpNextCardContent(card, onCancel, onPlayNow)
        }
    }
}

@Composable
private fun UpNextCardContent(card: UpNextCard, onCancel: () -> Unit, onPlayNow: () -> Unit) {
    val remainingMilliseconds = rememberRemainingMilliseconds(card)
    val remainingSeconds = (remainingMilliseconds + 999) / 1000
    Column(Modifier.width(300.dp).playerGlass(RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Next episode in $remainingSeconds s", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
        AsyncImage(
            card.stillUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)),
        )
        Text(card.episodeLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CancelButton(onCancel, Modifier.weight(1f))
            PlayNowButton(1f - remainingMilliseconds / UP_NEXT_MILLISECONDS.toFloat(), onPlayNow, Modifier.weight(1f))
        }
    }
}

// Counts down from the card's start, so it is right after rotation or coming back to the app.
@Composable
private fun rememberRemainingMilliseconds(card: UpNextCard): Long {
    var remaining by remember(card) { mutableLongStateOf(remainingOf(card)) }
    LaunchedEffect(card) {
        while (remaining > 0) {
            delay(COUNTDOWN_TICK_MILLISECONDS)
            remaining = remainingOf(card)
        }
    }
    return remaining
}

private fun remainingOf(card: UpNextCard): Long {
    val elapsed = System.currentTimeMillis() - card.startedAtMillis
    return (UP_NEXT_MILLISECONDS - elapsed).coerceIn(0, UP_NEXT_MILLISECONDS)
}

@Composable
private fun CancelButton(onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.height(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickableWithoutRipple(onClick = onClick), contentAlignment = Alignment.Center) {
        Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Fills left to right with the countdown.
@Composable
private fun PlayNowButton(progress: Float, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.height(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)).clickableWithoutRipple(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Color.White.copy(alpha = 0.45f)))
        Text("Play now", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
