package com.letsmoe.otter.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.CaretRight
import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.ServerSettings
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.cast.lanStreamOrigin
import com.letsmoe.otter.graphql.CalendarFeedPathQuery
import com.letsmoe.otter.graphql.ResetCalendarFeedMutation
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.toasts.ToastKind
import com.letsmoe.otter.toasts.Toasts
import com.letsmoe.otter.ui.components.PRESSED_SCALE_LARGE
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.ui.components.pressable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The full address of a feed: [origin] is where the backend answers (no trailing slash needed), [path] the feed's path. */
fun calendarFeedUrl(origin: String, path: String): String {
    return origin.trimEnd('/') + path
}

/**
 * Where other devices reach this backend: the NAS's own address, or for the backend on this phone
 * its address on the Wi-Fi (served on the stream port); null while the phone has no Wi-Fi.
 */
private fun reachableOrigin(context: Context): String? {
    if (ServerSettings.usesEmbeddedBackend) {
        return lanStreamOrigin(context)
    }
    return ServerSettings.baseUrl
}

/** "Subscribe in your calendar": the profile's feed of upcoming episodes, to copy or share. */
@Composable
fun CalendarGroup() {
    val profile by ProfileSession.activeProfile.collectAsStateWithLifecycle()
    val activeProfile = profile ?: return
    val footer = "Upcoming episodes of the series this profile watches or has on My List, for the next 8 weeks. " +
        "Anyone with the address can read the calendar; \"New address\" makes the old one stop working. " +
        "From a backend on this phone it is only reachable on the Wi-Fi while Otter is running."
    SettingsGroup("Calendar", footer = footer) {
        var open by remember { mutableStateOf(false) }
        CalendarHeaderRow(open) { open = !open }
        if (open) {
            CalendarAddress(activeProfile.id)
        }
    }
}

@Composable
private fun CalendarHeaderRow(open: Boolean, onClick: () -> Unit) {
    var arrow = PhosphorIcons.Regular.CaretRight
    if (open) {
        arrow = PhosphorIcons.Regular.CaretDown
    }
    Row(
        Modifier.pressable(pressedScale = PRESSED_SCALE_LARGE, onClick = onClick).fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Subscribe in your calendar", fontSize = 14.sp)
        Icon(arrow, contentDescription = null, modifier = Modifier.size(18.dp).alpha(0.6f))
    }
}

@Composable
private fun CalendarAddress(profileId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(profileId) {
        path = loadFeedPath(profileId)
        failed = path == null
    }
    val origin = reachableOrigin(context)
    val feedPath = path
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (origin == null) {
            Note("Connect to Wi-Fi to get an address other devices can reach.")
            return@Column
        }
        if (failed) {
            Note("Couldn't get the address. Is the backend running?")
            return@Column
        }
        if (feedPath == null) {
            Note("Getting the address…")
            return@Column
        }
        val url = calendarFeedUrl(origin, feedPath)
        Text(url, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Copy", Modifier.weight(1f), enabled = true, filled = true) { copyAddress(context, url) }
            PillButton("Share", Modifier.weight(1f), enabled = true, filled = false) { shareAddress(context, url) }
            PillButton("New address", Modifier.weight(1f), enabled = true, filled = false) {
                scope.launch { path = resetFeedPath(profileId) ?: path }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
}

private suspend fun loadFeedPath(profileId: String): String? {
    return try {
        OtterApi.client.query(CalendarFeedPathQuery(profileId)).execute().dataOrThrow().calendarFeedPath
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        null
    }
}

private suspend fun resetFeedPath(profileId: String): String? {
    return try {
        OtterApi.client.mutation(ResetCalendarFeedMutation(profileId)).execute().dataOrThrow().resetCalendarFeed
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Toasts.push("Couldn't make a new address", ToastKind.ERROR)
        null
    }
}

private fun copyAddress(context: Context, url: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Otter calendar", url))
    Toasts.push("Address copied", ToastKind.SUCCESS)
}

private fun shareAddress(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(send, "Otter calendar"))
}
