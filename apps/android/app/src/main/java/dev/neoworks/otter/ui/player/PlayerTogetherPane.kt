package dev.neoworks.otter.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neoworks.otter.together.TogetherSession
import dev.neoworks.otter.together.formatRoomCode
import dev.neoworks.otter.together.isOpen
import dev.neoworks.otter.ui.together.PeerAvatar
import dev.neoworks.otter.ui.together.TogetherChat
import dev.neoworks.otter.ui.together.TogetherPanel

private enum class TogetherView { CHAT, ROOM }

/** The Watch Together side pane: the chat or the room (code, people, Leave) once in one, otherwise start or join. */
@Composable
fun PlayerTogetherPane() {
    val room by TogetherSession.room.collectAsStateWithLifecycle()
    val peerStates by TogetherSession.peerStates.collectAsStateWithLifecycle()
    var view by remember { mutableStateOf(TogetherView.CHAT) }
    val currentRoom = room
    if (currentRoom == null || !room.isOpen()) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 16.dp)) {
            TogetherPanel()
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(end = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                for (peer in currentRoom.peers) {
                    PeerAvatar(peer, 28.dp, buffering = peerStates[peer.id]?.buffering == true)
                }
            }
            Box(Modifier.weight(1f))
            ViewSwitch(view, currentRoom.code) { view = other(view) }
        }
        if (view == TogetherView.CHAT) {
            TogetherChat(Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { TogetherPanel() }
        }
    }
}

private fun other(view: TogetherView): TogetherView {
    if (view == TogetherView.CHAT) {
        return TogetherView.ROOM
    }
    return TogetherView.CHAT
}

@Composable
private fun ViewSwitch(view: TogetherView, code: String, onClick: () -> Unit) {
    var label = "Chat"
    if (view == TogetherView.CHAT) {
        label = formatRoomCode(code)
    }
    Box(
        Modifier.height(32.dp).background(Color.White.copy(alpha = 0.08f), CircleShape).clickableWithoutRipple(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}
