package dev.neoworks.otter.ui.together

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ChatCircleDots
import dev.neoworks.otter.together.ChatLine
import dev.neoworks.otter.together.TogetherSession
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color

private val reactions = listOf("😂", "😮", "😍", "😱", "👏", "🔥")
private val maxMessagesHeight = 260.dp

/**
 * Watch Together chat: quick reactions, the conversation and a message field. Mirrors TogetherChat.svelte.
 * With [fillHeight] the messages take the space the parent gives; otherwise they grow up to a maximum.
 */
@Composable
fun TogetherChat(modifier: Modifier = Modifier, fillHeight: Boolean = true) {
    val chat by TogetherSession.chat.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(innerGap)) {
        Text("Chat", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        ReactionRow()
        if (fillHeight) {
            MessageArea(chat, Modifier.weight(1f))
        } else {
            MessageArea(chat, Modifier.heightIn(max = maxMessagesHeight))
        }
        MessageField()
    }
}

@Composable
private fun ReactionRow() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (emoji in reactions) {
            ReactionButton(emoji)
        }
    }
}

@Composable
private fun ReactionButton(emoji: String) {
    Box(
        Modifier
            .size(48.dp)
            .pressable { TogetherSession.sendReaction(emoji) }
            .background(Color.White.copy(alpha = 0.06f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 22.sp)
    }
}

@Composable
private fun MessageArea(chat: List<ChatLine>, modifier: Modifier) {
    if (chat.isEmpty()) {
        EmptyChat(modifier)
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(chat.size) {
        listState.animateScrollToItem(chat.lastIndex)
    }
    LazyColumn(modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chat, key = { line -> line.id }) { line -> ChatBubble(line) }
    }
}

@Composable
private fun EmptyChat(modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(PhosphorIcons.Regular.ChatCircleDots, null, tint = faintText, modifier = Modifier.size(32.dp))
        Text("No messages yet", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = mutedText)
        Text("Say hi to everyone watching.", fontSize = 13.sp, color = faintText)
    }
}
