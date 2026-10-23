package dev.neoworks.otter.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neoworks.otter.together.TogetherPeer
import dev.neoworks.otter.together.TogetherRoom
import dev.neoworks.otter.together.TogetherSession

private const val MAX_VISIBLE_AVATARS = 5
private val avatarSize = 40.dp

/** Overlapping avatars with a head count and names. */
@Composable
internal fun PeersCard(room: TogetherRoom) {
    val peerStates by TogetherSession.peerStates.collectAsStateWithLifecycle()
    val visiblePeers = room.peers.take(MAX_VISIBLE_AVATARS)
    val hiddenCount = room.peers.size - visiblePeers.size
    Row(
        Modifier.fillMaxWidth().subtleCard().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(innerGap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
            for (peer in visiblePeers) {
                val buffering = peerStates[peer.id]?.buffering == true
                PeerAvatar(peer, avatarSize, Modifier.border(2.dp, Color.Black, RoundedCornerShape(percent = 28)), buffering = buffering)
            }
            if (hiddenCount > 0) {
                OverflowBadge(hiddenCount)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(headCountText(room.peers.size), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(peerNames(room), fontSize = 13.sp, color = mutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OverflowBadge(hiddenCount: Int) {
    Box(
        Modifier.size(avatarSize).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(percent = 28)),
        contentAlignment = Alignment.Center,
    ) {
        Text("+$hiddenCount", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun headCountText(count: Int): String {
    if (count == 1) {
        return "Just you"
    }
    return "$count watching"
}

private fun peerNames(room: TogetherRoom): String {
    val names = room.peers.map { peer -> displayName(room, peer) }
    return names.joinToString(", ")
}

private fun displayName(room: TogetherRoom, peer: TogetherPeer): String {
    if (peer.id == room.self.id) {
        return "You"
    }
    return peer.name
}
