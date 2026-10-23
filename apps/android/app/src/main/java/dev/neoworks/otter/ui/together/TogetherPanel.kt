package dev.neoworks.otter.ui.together

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neoworks.otter.together.TogetherSession
import kotlinx.coroutines.launch

/**
 * Start or join a Watch Together room, or, inside one: its code to share, who is there, what plays and Leave.
 * Used by the Watch Together sheet and the player. Mirrors TogetherPanel.svelte.
 */
@Composable
fun TogetherPanel(initialCode: String = "", modifier: Modifier = Modifier, showLeave: Boolean = true) {
    val room by TogetherSession.room.collectAsStateWithLifecycle()
    val currentRoom = room
    if (currentRoom == null) {
        TogetherEntry(initialCode, modifier)
        return
    }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(sectionGap)) {
        TogetherRoomView(currentRoom)
        if (showLeave) {
            LeaveRoomButton { scope.launch { TogetherSession.leave() } }
        }
    }
}
