package dev.neoworks.otter.ui.together

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neoworks.otter.together.ChatLine
import dev.neoworks.otter.together.FloatingReaction
import dev.neoworks.otter.together.TogetherSession
import dev.neoworks.otter.ui.components.frostedGlass
import kotlinx.coroutines.delay

private const val BUBBLE_MILLISECONDS = 6000L
private const val BUBBLE_COUNT = 3
private const val REACTION_MILLISECONDS = 2600

/**
 * Over the video: reactions float up on the right, recent chat messages fade in at the bottom left.
 * Visible even while the controls are hidden. Mirrors TogetherOverlay.svelte.
 */
@Composable
fun TogetherOverlay(modifier: Modifier = Modifier) {
    val reactions by TogetherSession.reactions.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize()) {
        for (reaction in reactions) {
            key(reaction.id) { FloatingEmoji(reaction) }
        }
        RecentMessages(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 112.dp))
    }
}

@Composable
private fun FloatingEmoji(reaction: FloatingReaction) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(REACTION_MILLISECONDS)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rise = maxHeight * 0.55f
        val rightInset = maxWidth * ((8 + reaction.offsetPercent / 4) / 100f)
        Text(
            reaction.emoji,
            fontSize = 36.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 96.dp, end = rightInset)
                .offset(y = -(rise * progress.value))
                .alpha(emojiAlpha(progress.value)),
        )
    }
}

// Fades in over the first 12%, then out for the rest.
private fun emojiAlpha(progress: Float): Float {
    if (progress < 0.12f) {
        return progress / 0.12f
    }
    return 1f - (progress - 0.12f) / 0.88f
}

@Composable
private fun RecentMessages(modifier: Modifier) {
    val chat by TogetherSession.chat.collectAsStateWithLifecycle()
    val initialLastId = remember { chat.lastOrNull()?.id ?: 0L }
    var recent by remember { mutableStateOf(emptyList<ChatLine>()) }
    var lastSeenId by remember { mutableStateOf(initialLastId) }
    LaunchedEffect(chat) {
        val fresh = chat.filter { line -> line.id > lastSeenId && !line.mine }
        lastSeenId = chat.lastOrNull()?.id ?: lastSeenId
        recent = (recent + fresh).takeLast(BUBBLE_COUNT)
    }
    Column(modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (line in recent) {
            key(line.id) { MessageBubble(line) { recent = recent.filter { candidate -> candidate.id != line.id } } }
        }
    }
}

@Composable
private fun MessageBubble(line: ChatLine, onExpired: () -> Unit) {
    LaunchedEffect(line.id) {
        delay(BUBBLE_MILLISECONDS)
        onExpired()
    }
    AnimatedVisibility(visible = true, enter = slideInVertically(tween(220)) { height -> height / 2 } + fadeIn(), exit = fadeOut(tween(300))) {
        Row(
            Modifier.frostedGlass(RoundedCornerShape(16.dp)).padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeerAvatar(line.from, 24.dp)
            Text("${line.from.name} ${line.text}", fontSize = 14.sp, fontWeight = FontWeight.Normal)
        }
    }
}
