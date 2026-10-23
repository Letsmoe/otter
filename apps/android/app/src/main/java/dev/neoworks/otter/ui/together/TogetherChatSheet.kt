package dev.neoworks.otter.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.ChatCircleDots
import dev.neoworks.otter.together.ChatLine
import dev.neoworks.otter.together.TogetherSession
import dev.neoworks.otter.together.isOpen
import dev.neoworks.otter.ui.components.GlassBottomSheet
import dev.neoworks.otter.ui.theme.OtterColors

/** The room's chat in its own tall sheet, stacked on the Watch Together sheet; its field sits above the keyboard. */
@Composable
fun TogetherChatSheet() {
    val chatOpen by TogetherSession.chatOpen.collectAsStateWithLifecycle()
    val room by TogetherSession.room.collectAsStateWithLifecycle()
    if (!chatOpen || !room.isOpen()) {
        return
    }
    val chat by TogetherSession.chat.collectAsStateWithLifecycle()
    LaunchedEffect(chat.size) { TogetherSession.markChatSeen() }
    GlassBottomSheet(onDismiss = TogetherSession::closeChat, fullHeight = true) {
        TogetherChat(Modifier.weight(1f), fillHeight = true)
    }
}

/** Opens the chat sheet: the latest message and how many are unread. */
@Composable
fun ChatEntryCard(modifier: Modifier = Modifier) {
    val chat by TogetherSession.chat.collectAsStateWithLifecycle()
    val seenCount by TogetherSession.seenChatCount.collectAsStateWithLifecycle()
    val unread = (chat.size - seenCount).coerceAtLeast(0)
    Row(
        modifier
            .fillMaxWidth()
            .pressable { TogetherSession.openChat() }
            .subtleCard()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(innerGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Regular.ChatCircleDots, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Chat", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(chatPreview(chat.lastOrNull()), fontSize = 13.sp, color = mutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (unread > 0) {
            UnreadBadge(unread)
        }
        Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, tint = faintText, modifier = Modifier.size(18.dp))
    }
}

private fun chatPreview(latest: ChatLine?): String {
    if (latest == null) {
        return "Say hi to everyone watching"
    }
    if (latest.mine) {
        return "You: ${latest.text}"
    }
    return "${latest.from.name}: ${latest.text}"
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp).background(OtterColors.error, CircleShape).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
