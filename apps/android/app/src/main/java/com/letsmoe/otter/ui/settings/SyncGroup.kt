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
import com.letsmoe.otter.neoworks.NeoworksSyncResult
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.ui.theme.OtterColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val syncTimeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/** The household's Neoworks account, which syncs profiles between devices. */
@Composable
fun SyncGroup() {
    LaunchedEffect(Unit) { NeoworksAccountStore.refresh() }
    val account by NeoworksAccountStore.state.collectAsStateWithLifecycle()
    val footer = "One Neoworks account per household, end-to-end encrypted. Profiles sync between its devices " +
        "now; watch history, My List, ratings and settings follow in a later update."
    SettingsGroup("Sync", footer = footer) {
        AccountRow(account)
        if (account.signedIn) {
            SettingsDivider()
            LastSyncRow(account)
        }
    }
}

@Composable
private fun AccountRow(account: NeoworksAccountState) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Neoworks", fontSize = 14.sp)
            Text(accountLine(account), fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
        }
        if (account.signedIn) {
            PillButton("Sign out", Modifier, enabled = !account.busy, filled = false, onClick = NeoworksAccountStore::signOut)
            return@Row
        }
        PillButton("Sign in", Modifier, enabled = account.known && !account.busy, filled = true) {
            NeoworksAccountStore.startSignIn(context)
        }
    }
}

@Composable
private fun LastSyncRow(account: NeoworksAccountState) {
    val lastSync = account.lastSync
    var detailColor = Color.White.copy(alpha = 0.45f)
    if (lastSync?.failure != null) {
        detailColor = OtterColors.error
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(syncHeadline(account), fontSize = 14.sp)
            Text(syncDetail(lastSync), fontSize = 12.sp, color = detailColor)
        }
        PillButton("Sync now", Modifier, enabled = !account.busy, filled = false, onClick = NeoworksAccountStore::syncNow)
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

private fun syncHeadline(account: NeoworksAccountState): String {
    if (account.busy) {
        return "Syncing…"
    }
    val lastSync = account.lastSync ?: return "Not synced yet"
    val time = syncTimeFormat.format(Instant.parse(lastSync.at))
    if (lastSync.failure != null) {
        return "Sync failed at $time"
    }
    return "Synced at $time"
}

private fun syncDetail(lastSync: NeoworksSyncResult?): String {
    if (lastSync == null) {
        return "Syncs every 10 minutes"
    }
    if (lastSync.failure != null) {
        return lastSync.failure
    }
    return "${lastSync.received} received, ${lastSync.sent} sent"
}
