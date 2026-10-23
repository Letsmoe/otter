package dev.neoworks.otter.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.DownloadSimple
import com.adamglin.phosphoricons.regular.PencilSimple
import dev.neoworks.otter.downloads.DownloadGroup
import dev.neoworks.otter.downloads.DownloadItem
import dev.neoworks.otter.downloads.DownloadsRepository
import dev.neoworks.otter.downloads.groupDownloads
import dev.neoworks.otter.graphql.fragment.ProfileFields
import dev.neoworks.otter.graphql.type.DownloadStatus
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.profiles.avatarImage
import dev.neoworks.otter.ui.components.frostedGlass
import dev.neoworks.otter.ui.navigation.bottomBarClearance
import kotlinx.coroutines.launch

/**
 * Netflix's Downloads: the profile, then one row per movie and per series (episodes behind a
 * chevron), an edit mode to delete, and a way to find more. Mirrors routes/downloads/+page.svelte.
 */
@Composable
fun DownloadsScreen(
    onPlay: (DownloadItem) -> Unit,
    onOpenSeries: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val items by DownloadsRepository.items.collectAsStateWithLifecycle()
    val loaded by DownloadsRepository.loaded.collectAsStateWithLifecycle()
    val activeProfile by ProfileSession.activeProfile.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val groups = groupDownloads(items)
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = bottomBarClearance()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val profile = activeProfile
        if (profile != null && items.isNotEmpty()) {
            item { DownloadsHeader(profile, editing) { editing = !editing } }
        }
        items(groups, key = { group -> group.titleId }) { group ->
            if (group.isSeries) {
                SeriesGroupRow(group) { onOpenSeries(group.titleId) }
            } else {
                MovieRow(group.items.first(), editing, onPlay, scope)
            }
        }
        if (items.isEmpty() && loaded) {
            item { EmptyDownloads() }
        }
        item { FindMoreButton(onSearch) }
    }
}

@Composable
private fun MovieRow(item: DownloadItem, editing: Boolean, onPlay: (DownloadItem) -> Unit, scope: kotlinx.coroutines.CoroutineScope) {
    DownloadRow(
        item = item,
        editing = editing,
        onPlay = onPlay,
        onRetry = { retried -> scope.launch { retryDownload(retried) } },
        onDelete = { deleted -> scope.launch { DownloadsRepository.remove(deleted.id) } },
    )
}

@Composable
private fun DownloadsHeader(profile: ProfileFields, editing: Boolean, onToggleEditing: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(avatarImage(profile.avatarUrl, profile.name), contentDescription = null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)))
        Text(profile.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        var buttonModifier = Modifier.size(40.dp).clip(CircleShape)
        if (editing) {
            buttonModifier = Modifier.size(40.dp).frostedGlass(CircleShape)
        }
        Box(buttonModifier.clickable(onClick = onToggleEditing), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Regular.PencilSimple, contentDescription = "Edit downloads", modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SeriesGroupRow(group: DownloadGroup, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(144.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.05f))) {
            if (group.imageUrl != null) {
                AsyncImage(group.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f)) {
            Text(group.titleName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(episodeCount(group.items.size), fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
            Text(groupStatus(group), fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
        }
        Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, modifier = Modifier.size(22.dp))
    }
}

private fun episodeCount(count: Int): String {
    if (count == 1) {
        return "1 Episode"
    }
    return "$count Episodes"
}

// Summarises the series' episodes: still running ones first.
private fun groupStatus(group: DownloadGroup): String {
    val running = group.items.count { item -> item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED }
    val failed = group.items.count { item -> item.status == DownloadStatus.FAILED }
    if (running > 0) {
        return "Downloading $running"
    }
    if (failed > 0) {
        return "$failed failed"
    }
    return "Downloaded"
}

@Composable
private fun EmptyDownloads() {
    Column(
        Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(PhosphorIcons.Regular.DownloadSimple, contentDescription = null, modifier = Modifier.size(56.dp).alpha(0.4f))
        Text("No downloads yet", fontWeight = FontWeight.SemiBold)
        Text(
            "Movies and episodes you download play here, even offline.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(280.dp).alpha(0.6f),
        )
    }
}

@Composable
private fun FindMoreButton(onSearch: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.frostedGlass(CircleShape).clickable(onClick = onSearch).padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("See What You Can Download", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
