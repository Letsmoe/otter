package com.letsmoe.otter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.neoworks.NeoworksAccountState
import com.letsmoe.otter.neoworks.NeoworksAccountStore
import com.letsmoe.otter.ui.components.PillButton

/** The household's Neoworks account, which will sync profiles and what they watched. */
@Composable
fun SyncGroup() {
    LaunchedEffect(Unit) { NeoworksAccountStore.refresh() }
    val account by NeoworksAccountStore.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val footer = "One Neoworks account per household. Profiles, watch history, My List, ratings and " +
        "settings will sync between its devices, end-to-end encrypted. Syncing itself comes in a later update."
    SettingsGroup("Sync", footer = footer) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Neoworks", fontSize = 14.sp)
                Text(accountLine(account), fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
            }
            if (account.signedIn) {
                PillButton("Sign out", Modifier, enabled = !account.busy, filled = false, onClick = NeoworksAccountStore::signOut)
            } else {
                PillButton("Sign in", Modifier, enabled = account.known && !account.busy, filled = true) {
                    NeoworksAccountStore.startSignIn(context)
                }
            }
        }
    }
}

private fun accountLine(account: NeoworksAccountState): String {
    if (!account.known) {
        return "Checking…"
    }
    if (!account.signedIn) {
        return "Not signed in"
    }
    val who = account.email ?: account.name
    if (who == null) {
        return "Signed in"
    }
    return "Signed in as $who"
}
