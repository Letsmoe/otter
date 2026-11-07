package com.letsmoe.otter.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.ui.title.ACTION_BUTTON_WIDTH
import com.letsmoe.otter.downloads.DownloadItem
import com.letsmoe.otter.downloads.DownloadsRepository
import com.letsmoe.otter.graphql.type.DownloadStatus
import com.letsmoe.otter.toasts.ToastKind
import com.letsmoe.otter.toasts.Toasts
import com.letsmoe.otter.ui.components.pressable
import kotlinx.coroutines.launch

enum class DownloadButtonStyle { LABELED, ICON }

/**
 * Starts (after asking for the source), shows and retries the download of a movie or episode.
 * LABELED is the title page's icon-over-label button, ICON the episode row's. Mirrors DownloadButton.svelte.
 */
@Composable
fun DownloadButton(
    mediaId: String,
    title: String,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    style: DownloadButtonStyle = DownloadButtonStyle.LABELED,
) {
    val items by DownloadsRepository.items.collectAsStateWithLifecycle()
    val item = items.firstOrNull { candidate -> candidate.mediaId == mediaId }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun onClick() {
        if (item != null && item.status != DownloadStatus.FAILED) {
            onOpenDownloads()
            return
        }
        picking = true
    }

    if (picking) {
        SourcePickerSheet(
            mediaId = mediaId,
            title = title,
            onPick = { streamId ->
                picking = false
                scope.launch { startDownload(mediaId, streamId) }
            },
            onClose = { picking = false },
        )
    }
    if (style == DownloadButtonStyle.LABELED) {
        LabeledButton(item, modifier, ::onClick)
        return
    }
    Box(modifier.pressable(onClick = ::onClick).size(40.dp).clip(CircleShape),contentAlignment = Alignment.Center) {
        DownloadStateIcon(item, 22.dp)
    }
}

@Composable
private fun LabeledButton(item: DownloadItem?, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.pressable(onClick = onClick).width(ACTION_BUTTON_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DownloadStateIcon(item, 26.dp)
        Text(downloadLabel(item), fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

private fun downloadLabel(item: DownloadItem?): String {
    if (item == null) {
        return "Download"
    }
    return when (item.status) {
        DownloadStatus.DONE -> "Downloaded"
        DownloadStatus.FAILED -> "Retry"
        DownloadStatus.QUEUED -> "Waiting"
        else -> "${Math.round(item.progress * 100)}%"
    }
}

private suspend fun startDownload(mediaId: String, streamId: String?) {
    try {
        DownloadsRepository.start(mediaId, streamId)
    } catch (error: Exception) {
        Toasts.push(error.message ?: "Could not start the download", ToastKind.ERROR)
    }
}
