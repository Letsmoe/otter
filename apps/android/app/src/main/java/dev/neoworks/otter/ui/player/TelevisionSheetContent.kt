package dev.neoworks.otter.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Television
import dev.neoworks.otter.cast.DlnaRenderer
import dev.neoworks.otter.cast.discoverRenderers
import dev.neoworks.otter.cast.rememberChromecastAvailable
import dev.neoworks.otter.cast.showChromecastPicker

/** Lists the DLNA TVs on the Wi-Fi and, when there is one, the Chromecast picker; picking one moves playback there. */
@Composable
fun TelevisionSheetContent(state: PlayerUiState, onChoose: (DlnaRenderer) -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    var renderers by remember { mutableStateOf<List<DlnaRenderer>>(emptyList()) }
    var searching by remember { mutableStateOf(true) }
    var searchRound by remember { mutableIntStateOf(0) }
    val chromecastAvailable = rememberChromecastAvailable()
    LaunchedEffect(searchRound) {
        searching = true
        renderers = discoverRenderers(context)
        searching = false
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Play on TV", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (state.castsToTelevision) {
            PlayingOnTelevision(state.castDeviceName.orEmpty(), onStop)
        }
        if (chromecastAvailable) {
            TelevisionRow("Chromecast…", current = state.isCasting && !state.castsToTelevision) { showChromecastPicker(context) }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(renderers, key = { renderer -> renderer.controlUrl }) { renderer ->
                TelevisionRow(renderer.name, current = state.castsToTelevision && renderer.name == state.castDeviceName) { onChoose(renderer) }
            }
        }
        SearchStatus(searching, renderers.isEmpty()) { searchRound += 1 }
    }
}

@Composable
private fun PlayingOnTelevision(deviceName: String, onStop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.08f), CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Playing on $deviceName", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("Stop", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickableWithoutRipple(onClick = onStop))
    }
}

@Composable
private fun TelevisionRow(name: String, current: Boolean, onClick: () -> Unit) {
    var color = Color.White.copy(alpha = 0.7f)
    var weight = FontWeight.Normal
    if (current) {
        color = Color.White
        weight = FontWeight.Bold
    }
    Row(
        Modifier.fillMaxWidth().clickableWithoutRipple(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Regular.Television, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(name, color = color, fontWeight = weight, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchStatus(searching: Boolean, nothingFound: Boolean, onSearchAgain: () -> Unit) {
    if (searching) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            Text("Looking for TVs…", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        return
    }
    var message = "Search again"
    if (nothingFound) {
        message = "No TV found. Is it on and on the same Wi-Fi? Search again"
    }
    Text(message, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.clickableWithoutRipple(onClick = onSearchAgain))
}
