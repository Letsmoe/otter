package com.letsmoe.otter.ui.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.regular.ArrowClockwise
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.Television
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.cast.DlnaRenderer
import com.letsmoe.otter.cast.TelevisionConnection
import com.letsmoe.otter.cast.TelevisionTarget
import com.letsmoe.otter.cast.discoverRenderers
import com.letsmoe.otter.cast.phoneVpnActive
import com.letsmoe.otter.ui.components.PRESSED_SCALE_LARGE
import com.letsmoe.otter.ui.components.SubtleFill
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.cast.rememberChromecastAvailable

private val rowShape = RoundedCornerShape(16.dp)

/**
 * Lists the DLNA TVs on the Wi-Fi and, when there is one, the Chromecast picker. Picking a DLNA TV
 * calls [onChoose]; a Chromecast connects through the system picker.
 */
@Composable
fun TelevisionSheetContent(onChoose: (DlnaRenderer) -> Unit) {
    val context = LocalContext.current
    var renderers by remember { mutableStateOf<List<DlnaRenderer>>(emptyList()) }
    var searching by remember { mutableStateOf(true) }
    var searchRound by remember { mutableIntStateOf(0) }
    val chromecastAvailable = rememberChromecastAvailable()
    val connected by TelevisionConnection.target.collectAsStateWithLifecycle()
    LaunchedEffect(searchRound) {
        searching = true
        renderers = discoverRenderers(context)
        searching = false
    }
    // Wraps its rows instead of filling the height: the glass sheet scrolls its content, where a weighted list gets no height.
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Play on TV", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        if (chromecastAvailable) {
            TelevisionRow("Chromecast…", current = connected is TelevisionTarget.Chromecast) { TelevisionConnection.connectChromecast(context) }
        }
        for (renderer in renderers) {
            TelevisionRow(renderer.name, current = isConnectedTo(connected, renderer)) { onChoose(renderer) }
        }
        SearchStatus(searching, renderers.isEmpty()) { searchRound += 1 }
    }
}

private fun isConnectedTo(connected: TelevisionTarget?, renderer: DlnaRenderer): Boolean {
    if (connected !is TelevisionTarget.Dlna) {
        return false
    }
    return connected.renderer.controlUrl == renderer.controlUrl
}

/** A TV as a card: icon, name, whether it's connected, and an arrow that says it can be tapped. */
@Composable
private fun TelevisionRow(name: String, current: Boolean, onClick: () -> Unit) {
    var detail = "Tap to connect"
    var trailingIcon = PhosphorIcons.Regular.CaretRight
    var frame = Color.Transparent
    if (current) {
        detail = "Connected"
        trailingIcon = PhosphorIcons.Bold.Check
        frame = Color.White.copy(alpha = 0.25f)
    }
    Row(
        Modifier
            .pressable(pressedScale = PRESSED_SCALE_LARGE, onClick = onClick)
            .fillMaxWidth()
            .background(SubtleFill, rowShape)
            .border(1.dp, frame, rowShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(Color.White.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Regular.Television, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1)
        }
        Icon(trailingIcon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SearchStatus(searching: Boolean, nothingFound: Boolean, onSearchAgain: () -> Unit) {
    if (searching) {
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            Text("Looking for TVs…", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        return
    }
    if (nothingFound) {
        Text(nothingFoundMessage(LocalContext.current), fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
    }
    SearchAgainLink(onSearchAgain)
}

// Some VPNs send even local traffic into their tunnel, which hides the TV.
private fun nothingFoundMessage(context: Context): String {
    if (phoneVpnActive(context)) {
        return "No TV found. Your VPN may be hiding it: allow LAN connections in the VPN's settings, or pause it."
    }
    return "No TV found. Is it on and on the same Wi-Fi?"
}

// A small link, not a button: searching again is the exception, picking a TV the point.
@Composable
private fun SearchAgainLink(onClick: () -> Unit) {
    Row(
        Modifier.pressable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Regular.ArrowClockwise, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
        Text("Search again", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
    }
}
