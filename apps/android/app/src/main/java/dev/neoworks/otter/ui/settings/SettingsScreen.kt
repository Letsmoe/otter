package dev.neoworks.otter.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CaretRight
import dev.neoworks.otter.api.ServerSettings
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.settings.AutoSkipIntro
import dev.neoworks.otter.settings.PlaybackSettings
import dev.neoworks.otter.toasts.ToastKind
import dev.neoworks.otter.toasts.Toasts
import dev.neoworks.otter.ui.components.FilterSelect
import dev.neoworks.otter.ui.components.OtterSwitch
import dev.neoworks.otter.ui.components.PlainTextField
import dev.neoworks.otter.ui.components.ScreenHeader
import dev.neoworks.otter.ui.navigation.bottomBarClearance
import dev.neoworks.otter.vpn.VpnStatusStore
import kotlinx.coroutines.launch

/** App settings as glass groups: server address, TMDB, streaming and profiles. Mirrors routes/settings/+page.svelte. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenPlugins: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("App Settings", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomBarClearance()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ServerGroup()
            MetadataGroup(viewModel)
            StreamingGroup(viewModel)
            PlaybackGroup()
            ProfilesGroup(onOpenPlugins)
        }
    }
}

@Composable
private fun ServerGroup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(ServerSettings.baseUrl) }
    var checking by remember { mutableStateOf(false) }

    // A new server address applies after a restart, once it answers.
    fun connect() {
        scope.launch {
            checking = true
            val reachable = ServerSettings.check(draft)
            checking = false
            if (!reachable) {
                Toasts.push("No Otter server at ${ServerSettings.normalize(draft)}", ToastKind.ERROR)
                return@launch
            }
            ServerSettings.save(draft)
            restartApp(context)
        }
    }

    SettingsGroup("Server", footer = "The Otter backend this device talks to. Saved on this device only.") {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PlainTextField(draft, { typed -> draft = typed }, "http://192.168.1.10:8080", keyboardType = KeyboardType.Uri)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(connectLabel(checking), Modifier.weight(1f), enabled = !checking && draft.isNotBlank(), filled = true, onClick = ::connect)
                if (!ServerSettings.usesEmbeddedBackend) {
                    PillButton("Use this phone", Modifier.weight(1f), enabled = true, filled = false) {
                        ServerSettings.clear()
                        restartApp(context)
                    }
                }
            }
        }
    }
}

private fun connectLabel(checking: Boolean): String {
    if (checking) {
        return "Checking…"
    }
    return "Connect"
}

@Composable
private fun PillButton(label: String, modifier: Modifier, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    var fill = Color.Transparent
    var labelColor = Color.White.copy(alpha = 0.7f)
    var fontWeight = FontWeight.Normal
    if (filled) {
        fill = Color.White.copy(alpha = 0.1f)
        labelColor = Color.White
        fontWeight = FontWeight.SemiBold
    }
    var alpha = 1f
    if (!enabled) {
        alpha = 0.5f
    }
    Row(
        modifier.alpha(alpha).background(fill, CircleShape).clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = fontWeight, color = labelColor)
    }
}

// The embedded backend and every client connection start over with the new address.
private fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    Runtime.getRuntime().exit(0)
}

@Composable
private fun MetadataGroup(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    SettingsGroup("Metadata", footer = "TMDB provides titles, posters and recommendations.") {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TMDB API key", fontSize = 14.sp)
                Text("Get key", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.clickable { openApiKeyPage(context) })
            }
            var hadFocus by remember { mutableStateOf(false) }
            PlainTextField(
                value = viewModel.tmdbApiKey,
                onValueChange = { typed -> viewModel.tmdbApiKey = typed },
                placeholder = "Paste your key",
                isPassword = true,
                textColor = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.onFocusChanged { state ->
                    if (hadFocus && !state.isFocused) {
                        viewModel.saveApiKey()
                    }
                    hadFocus = state.isFocused
                },
            )
        }
        SettingsDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Language", fontSize = 14.sp)
            FilterSelect("Language", viewModel.languageOptions(), viewModel.tmdbLanguage, viewModel::chooseLanguage, showLabel = false)
        }
    }
}

private fun openApiKeyPage(context: Context) {
    val page = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org/settings/api"))
    context.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun StreamingGroup(viewModel: SettingsViewModel) {
    val vpn by VpnStatusStore.status.collectAsStateWithLifecycle()
    SettingsGroup("Streaming") {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Require VPN", fontSize = 14.sp)
                Text("No lookups or playback without a VPN", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
            }
            OtterSwitch(viewModel.requireVpn, viewModel::changeRequireVpn)
        }
        SettingsDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("VPN", fontSize = 14.sp)
            Text(vpnLine(vpn.known, vpn.connected), fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun PlaybackGroup() {
    val profile by ProfileSession.activeProfile.collectAsStateWithLifecycle()
    var autoSkip by remember { mutableStateOf(PlaybackSettings.autoSkipIntro) }
    val options = AutoSkipIntro.entries.map { option -> option to option.label }
    val footer = "Intro and recap times come from TheIntroDB. The skip button shows either way. " +
        "Captions and voice boost belong to the profile; voice boost only works on this phone, not while casting."
    SettingsGroup("Playback", footer = footer) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Skip intros automatically", fontSize = 14.sp)
                Text("Jumps over intros and recaps once", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
            }
            FilterSelect("Skip intros", options, autoSkip, { choice ->
                autoSkip = choice
                PlaybackSettings.autoSkipIntro = choice
            }, showLabel = false)
        }
        profile?.let { activeProfile ->
            SettingsDivider()
            CaptionSettingsRows(activeProfile)
        }
    }
}

private fun vpnLine(known: Boolean, connected: Boolean): String {
    if (!known) {
        return "Checking…"
    }
    if (connected) {
        return "Connected"
    }
    return "Not connected"
}

@Composable
private fun ProfilesGroup(onOpenPlugins: () -> Unit) {
    SettingsGroup("Profiles") {
        LinkRow("Manage Profiles", ProfileSession::logout)
        SettingsDivider()
        LinkRow("Plugins", onOpenPlugins)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp)
        Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, modifier = Modifier.size(18.dp).alpha(0.6f))
    }
}
