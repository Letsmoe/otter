package com.letsmoe.otter.ui.together

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.together.TogetherSession
import com.letsmoe.otter.together.isOpen
import com.letsmoe.otter.ui.components.GlassBottomSheet
import kotlinx.coroutines.launch

/** Watch Together over the app: start or join, and once in a room its code, people, a way into the chat and Leave. Mirrors TogetherSheet.svelte. */
@Composable
fun TogetherSheet() {
    val sheetOpen by TogetherSession.sheetOpen.collectAsStateWithLifecycle()
    if (!sheetOpen) {
        return
    }
    val room by TogetherSession.room.collectAsStateWithLifecycle()
    val inviteCode by TogetherSession.inviteCode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    GlassBottomSheet(onDismiss = TogetherSession::closeSheet) {
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(sectionGap)) {
            TogetherPanel(initialCode = inviteCode, showLeave = false)
            if (room.isOpen()) {
                ChatEntryCard()
                LeaveRoomButton { scope.launch { TogetherSession.leave() } }
            }
        }
    }
}
