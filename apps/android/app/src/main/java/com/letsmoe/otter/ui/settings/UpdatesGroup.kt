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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.BuildConfig
import com.letsmoe.otter.ui.components.FilterSelect
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.updates.AppRelease
import com.letsmoe.otter.updates.AppUpdater
import com.letsmoe.otter.updates.UpdateChannel
import com.letsmoe.otter.updates.UpdateSettings
import com.letsmoe.otter.updates.UpdateState
import com.letsmoe.otter.updates.isDowngrade

/** Release or debug builds, and a button to install the newest build of the chosen one. */
@Composable
fun UpdatesGroup() {
    var channel by remember { mutableStateOf(UpdateSettings.channel) }
    var newest by remember { mutableStateOf<AppRelease?>(null) }
    var checking by remember { mutableStateOf(true) }
    LaunchedEffect(channel) {
        checking = true
        newest = AppUpdater.fetchNewest(channel)
        checking = false
    }
    val options = UpdateChannel.entries.map { option -> option to option.label }
    val footer = "Debug builds are made from every change and can break. Android can't install an " +
        "older build over a newer one, so switching back waits for the next release."
    SettingsGroup("Updates", footer = footer) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Update channel", fontSize = 14.sp)
                Text(installedLine(), fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
            }
            FilterSelect("Update channel", options, channel, { choice ->
                channel = choice
                UpdateSettings.channel = choice
            }, showLabel = false)
        }
        SettingsDivider()
        InstallRow(newest, checking)
    }
}

@Composable
private fun InstallRow(newest: AppRelease?, checking: Boolean) {
    val context = LocalContext.current
    val updateState by AppUpdater.state.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(newestLine(newest, checking), fontSize = 14.sp)
            Text(installHint(newest), fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
        }
        if (newest == null) {
            return@Row
        }
        val downloading = updateState is UpdateState.Downloading
        PillButton(installLabel(updateState), Modifier, enabled = canInstall(newest) && !downloading, filled = true) {
            AppUpdater.downloadAndInstall(context, newest)
        }
    }
}

private fun installedLine(): String {
    val buildType = UpdateSettings.installedChannel().label.lowercase()
    return "Installed: ${BuildConfig.VERSION_NAME} ($buildType, build ${BuildConfig.VERSION_CODE})"
}

private fun newestLine(newest: AppRelease?, checking: Boolean): String {
    if (checking) {
        return "Checking GitHub…"
    }
    if (newest == null) {
        return "No build found"
    }
    return "Newest: ${newest.displayName()}"
}

private fun installHint(newest: AppRelease?): String {
    if (newest == null) {
        return "Try again when online"
    }
    if (newest.build == BuildConfig.VERSION_CODE) {
        return "This build is installed"
    }
    if (isDowngrade(newest, BuildConfig.VERSION_CODE)) {
        return "Older than the installed build"
    }
    return "Installs over this app, keeping your data"
}

private fun canInstall(newest: AppRelease): Boolean {
    if (newest.build == BuildConfig.VERSION_CODE) {
        return false
    }
    return !isDowngrade(newest, BuildConfig.VERSION_CODE)
}

private fun installLabel(state: UpdateState): String {
    if (state is UpdateState.Downloading) {
        return "${(state.progress * 100).toInt()} %"
    }
    return "Install"
}
