package dev.neoworks.otter.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neoworks.otter.downloads.DownloadItem
import dev.neoworks.otter.downloads.DownloadsRepository
import dev.neoworks.otter.toasts.ToastKind
import dev.neoworks.otter.toasts.Toasts
import dev.neoworks.otter.ui.components.ScreenHeader
import dev.neoworks.otter.ui.navigation.bottomBarClearance
import kotlinx.coroutines.launch

/** One series' downloaded episodes, grouped by season. Mirrors routes/downloads/[seriesId]/+page.svelte. */
@Composable
fun SeriesDownloadsScreen(seriesId: String, onPlay: (DownloadItem) -> Unit, onBack: () -> Unit) {
    val items by DownloadsRepository.items.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val episodes = items.filter { item -> item.titleId == seriesId }
    val seasons = episodes.map { item -> seasonOf(item) }.distinct().sorted()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = seriesName(episodes),
            onBack = onBack,
            actionLabel = editLabel(editing),
            onAction = { editing = !editing },
        )
        if (episodes.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
                Text("No downloaded episodes.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
            }
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomBarClearance()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            for (seasonNumber in seasons) {
                item(key = "season-$seasonNumber") {
                    SeasonSection(seasonNumber, episodesOf(episodes, seasonNumber), editing, onPlay, scope)
                }
            }
        }
    }
}

@Composable
private fun SeasonSection(
    seasonNumber: Int,
    episodes: List<DownloadItem>,
    editing: Boolean,
    onPlay: (DownloadItem) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Season $seasonNumber", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
        for (item in episodes) {
            DownloadRow(
                item = item,
                editing = editing,
                onPlay = onPlay,
                onRetry = { retried -> scope.launch { retryDownload(retried) } },
                onDelete = { deleted -> scope.launch { DownloadsRepository.remove(deleted.id) } },
            )
        }
    }
}

private fun seriesName(episodes: List<DownloadItem>): String {
    val first = episodes.firstOrNull() ?: return "Downloads"
    return first.titleName
}

private fun seasonOf(item: DownloadItem): Int = item.episode?.seasonNumber ?: 0

private fun episodesOf(episodes: List<DownloadItem>, seasonNumber: Int): List<DownloadItem> {
    return episodes.filter { item -> seasonOf(item) == seasonNumber }.sortedBy { item -> item.episode?.episodeNumber ?: 0 }
}

private fun editLabel(editing: Boolean): String {
    if (editing) {
        return "Done"
    }
    return "Edit"
}
