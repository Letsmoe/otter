package dev.neoworks.otter.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.X
import com.adamglin.phosphoricons.fill.Play
import dev.neoworks.otter.downloads.DownloadItem
import dev.neoworks.otter.downloads.DownloadsRepository
import dev.neoworks.otter.downloads.formatSize
import dev.neoworks.otter.toasts.ToastKind
import dev.neoworks.otter.toasts.Toasts
import dev.neoworks.otter.graphql.type.DownloadStatus
import dev.neoworks.otter.ui.components.frostedGlass
import dev.neoworks.otter.ui.theme.OtterColors

/** A movie or episode in the download list. Mirrors DownloadRow.svelte. */
@Composable
fun DownloadRow(
    item: DownloadItem,
    editing: Boolean,
    onPlay: (DownloadItem) -> Unit,
    onRetry: (DownloadItem) -> Unit,
    onDelete: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        DownloadThumbnail(item.imageUrl, playable = item.isPlayable, onClick = { onPlay(item) })
        Column(Modifier.weight(1f)) {
            Text(rowName(item), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(statusLine(item), fontSize = 12.sp, color = statusColor(item), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (editing) {
            Box(Modifier.size(36.dp).frostedGlass(CircleShape).clickable { onDelete(item) }, contentAlignment = Alignment.Center) {
                Icon(PhosphorIcons.Bold.X, contentDescription = "Delete download", modifier = Modifier.size(16.dp))
            }
        } else {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { if (item.status == DownloadStatus.FAILED) onRetry(item) }, contentAlignment = Alignment.Center) {
                DownloadStateIcon(item, 24.dp)
            }
        }
    }
}

@Composable
fun DownloadThumbnail(imageUrl: String?, playable: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(144.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(enabled = playable, onClick = onClick),
    ) {
        if (imageUrl != null) {
            AsyncImage(imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (playable) {
            Box(Modifier.align(Alignment.Center).size(40.dp).frostedGlass(CircleShape), contentAlignment = Alignment.Center) {
                Icon(PhosphorIcons.Fill.Play, contentDescription = "Play", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun rowName(item: DownloadItem): String {
    val episode = item.episode ?: return item.titleName
    var episodeName = "Episode ${episode.episodeNumber}"
    if (!episode.name.isNullOrEmpty()) {
        episodeName = episode.name
    }
    return "${episode.episodeNumber}. $episodeName"
}

private fun statusLine(item: DownloadItem): String {
    return when (item.status) {
        DownloadStatus.DONE -> formatSize(item.sizeBytes)
        DownloadStatus.QUEUED -> "Waiting"
        DownloadStatus.FAILED -> failureText(item)
        else -> progressText(item)
    }
}

private fun failureText(item: DownloadItem): String {
    if (item.error.isNullOrEmpty()) {
        return "Download failed"
    }
    return item.error
}

private fun progressText(item: DownloadItem): String {
    val size = formatSize(item.sizeBytes)
    val percent = "${Math.round(item.progress * 100)}%"
    if (size.isEmpty()) {
        return "Downloading · $percent"
    }
    return "Downloading · $percent · $size"
}

private fun statusColor(item: DownloadItem): Color {
    if (item.status == DownloadStatus.FAILED) {
        return OtterColors.error
    }
    return Color.White.copy(alpha = 0.55f)
}

/** Queues a failed download again; the backend tries the best links. */
suspend fun retryDownload(item: DownloadItem) {
    try {
        DownloadsRepository.start(item.mediaId, null)
    } catch (error: Exception) {
        Toasts.push(error.message ?: "Could not start the download", ToastKind.ERROR)
    }
}
