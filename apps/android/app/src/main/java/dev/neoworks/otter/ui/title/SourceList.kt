package dev.neoworks.otter.ui.title

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.fill.SealCheck
import com.adamglin.phosphoricons.regular.ArrowClockwise
import dev.neoworks.otter.graphql.fragment.StreamLink
import dev.neoworks.otter.ui.theme.OtterColors

private val mutedText = Color.White.copy(alpha = 0.5f)

/** Every stream link found for the title, best first; tapping one plays it. */
@Composable
fun SourceList(
    streams: StreamList,
    onPlay: (StreamLink) -> Unit,
    onSearchAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (streams.searching) {
            StillSearching(foundSome = streams.streams.isNotEmpty())
        }
        for (stream in streams.streams) {
            SourceRow(stream, onClick = { onPlay(stream) })
        }
        if (streams.nothingFound) {
            Text("No sources found.", fontSize = 14.sp, color = mutedText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
        }
        if (!streams.searching) {
            SearchAgainButton(onSearchAgain)
        }
    }
}

/** Sites answer one by one, some take half a minute: an empty or short list isn't final yet. */
@Composable
private fun StillSearching(foundSome: Boolean) {
    var label = "Searching sites for sources…"
    if (foundSome) {
        label = "Searching more sites…"
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp)
        Text(label, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun SourceRow(stream: StreamLink, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Fill.Play, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            SourceTitle(stream)
            Text(sourceDetails(stream), fontSize = 12.sp, color = mutedText)
        }
        Text(
            stream.plugin,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SourceTitle(stream: StreamLink) {
    var label = "Link"
    if (stream.label != null) {
        label = stream.label
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (stream.playable == true) {
            Icon(PhosphorIcons.Fill.SealCheck, contentDescription = "Works", tint = OtterColors.success, modifier = Modifier.size(14.dp))
        }
    }
}

/** Quality, language and measured speed, e.g. "1080p  DE  ~2.4 MB/s". */
private fun sourceDetails(stream: StreamLink): String {
    val parts = mutableListOf<String>()
    stream.quality?.let { quality -> parts.add(quality) }
    stream.language?.let { language -> parts.add(language) }
    val speed = formatSpeed(stream.speedBps)
    if (speed != null) {
        parts.add("~$speed")
    }
    return parts.joinToString("  ")
}

/** "2.4 MB/s"; null without a measurement. */
fun formatSpeed(bytesPerSecond: Double?): String? {
    if (bytesPerSecond == null || bytesPerSecond <= 0) {
        return null
    }
    if (bytesPerSecond >= 1_000_000) {
        return "%.1f MB/s".format(bytesPerSecond / 1_000_000)
    }
    if (bytesPerSecond >= 1_000) {
        return "%.0f KB/s".format(bytesPerSecond / 1_000)
    }
    return "${Math.round(bytesPerSecond)} B/s"
}

@Composable
private fun SearchAgainButton(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.Regular.ArrowClockwise, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Text("Search again", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
    }
}
