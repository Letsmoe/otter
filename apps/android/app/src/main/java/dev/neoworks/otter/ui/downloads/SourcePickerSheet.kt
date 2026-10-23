package dev.neoworks.otter.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.SealCheck
import com.adamglin.phosphoricons.fill.Sparkle
import dev.neoworks.otter.ui.components.GlassBottomSheet
import dev.neoworks.otter.ui.player.SourceStream
import dev.neoworks.otter.ui.player.followSourceStreams
import dev.neoworks.otter.ui.theme.OtterColors

private val groupShape = RoundedCornerShape(24.dp)

/**
 * Asks which source to download from: "Best available" (the backend tries the top links) or one
 * specific link. Links arrive live as plugins answer. Mirrors SourcePickerSheet.svelte.
 */
@Composable
fun SourcePickerSheet(mediaId: String, title: String, onPick: (String?) -> Unit, onClose: () -> Unit) {
    var streams by remember { mutableStateOf(emptyList<SourceStream>()) }
    var searching by remember { mutableStateOf(true) }
    LaunchedEffect(mediaId) {
        followSourceStreams(mediaId) { update, done ->
            streams = update
            searching = !done
        }
    }
    GlassBottomSheet(onDismiss = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("Download from", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(title, fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f), groupShape).padding(horizontal = 16.dp)) {
                BestAvailableRow { onPick(null) }
                for (stream in streams) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    StreamRow(stream) { onPick(stream.id) }
                }
            }
            SearchStatus(searching, streams.isEmpty())
        }
    }
}

@Composable
private fun BestAvailableRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(Color.White.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Fill.Sparkle, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Column {
            Text("Best available", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Tries the top sources until one works", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun StreamRow(stream: SourceStream, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(streamName(stream), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (stream.playable == true) {
                    Icon(PhosphorIcons.Fill.SealCheck, contentDescription = "Known to play", tint = OtterColors.success, modifier = Modifier.size(14.dp))
                }
            }
            val details = streamDetails(stream)
            if (details.isNotEmpty()) {
                Text(details, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            stream.plugin,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun streamName(stream: SourceStream): String {
    if (stream.label.isNullOrEmpty()) {
        return "Link"
    }
    return stream.label
}

private fun streamDetails(stream: SourceStream): String {
    val parts = mutableListOf<String>()
    stream.quality?.let { quality -> parts.add(quality) }
    stream.language?.let { language -> parts.add(language) }
    val speed = formatTransferSpeed(stream.speedBytesPerSecond)
    if (speed != null) {
        parts.add("~$speed")
    }
    return parts.joinToString(" · ")
}

fun formatTransferSpeed(bytesPerSecond: Double?): String? {
    if (bytesPerSecond == null || bytesPerSecond <= 0) {
        return null
    }
    if (bytesPerSecond >= 1_000_000) {
        return String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSecond / 1_000_000)
    }
    if (bytesPerSecond >= 1_000) {
        return String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSecond / 1_000)
    }
    return "${Math.round(bytesPerSecond)} B/s"
}

@Composable
private fun SearchStatus(searching: Boolean, empty: Boolean) {
    if (searching) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White.copy(alpha = 0.55f))
            Text("Searching sites…", fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
        }
        return
    }
    if (empty) {
        Text("No sources found yet.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
