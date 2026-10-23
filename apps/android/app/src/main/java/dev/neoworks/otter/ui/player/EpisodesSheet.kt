package dev.neoworks.otter.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.Play

/**
 * Netflix's episodes panel: season switch on top, the season's episodes as a horizontal row of
 * cards, the playing one marked. Mirrors EpisodesSheet.svelte.
 */
@Composable
fun EpisodesSheetContent(episodes: List<PlayerEpisode>, currentEpisodeId: String, onEpisode: (String) -> Unit) {
    val seasonNumbers = episodes.map { episode -> episode.seasonNumber }.distinct()
    var shownSeason by remember { mutableIntStateOf(initialSeason(episodes, currentEpisodeId)) }
    val seasonEpisodes = episodes.filter { episode -> episode.seasonNumber == shownSeason }
    Column(Modifier.fillMaxSize().padding(top = 24.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyRow(contentPadding = PaddingValues(start = 24.dp, end = 80.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasonNumbers) { seasonNumber ->
                SeasonChip(seasonLabel(seasonNumber), seasonNumber == shownSeason) { shownSeason = seasonNumber }
            }
        }
        EpisodeRow(seasonEpisodes, currentEpisodeId, onEpisode)
    }
}

private fun initialSeason(episodes: List<PlayerEpisode>, currentEpisodeId: String): Int {
    val current = episodes.firstOrNull { episode -> episode.id == currentEpisodeId }
    if (current != null) {
        return current.seasonNumber
    }
    return episodes.firstOrNull()?.seasonNumber ?: 1
}

private fun seasonLabel(seasonNumber: Int): String {
    if (seasonNumber == 0) {
        return "Specials"
    }
    return "Season $seasonNumber"
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var fill = Color.White.copy(alpha = 0.1f)
    var content = Color.White.copy(alpha = 0.7f)
    if (selected) {
        fill = Color.White
        content = Color.Black
    }
    Box(Modifier.height(36.dp).background(fill, CircleShape).clickableWithoutRipple(onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EpisodeRow(episodes: List<PlayerEpisode>, currentEpisodeId: String, onEpisode: (String) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(episodes.size) {
        val currentIndex = episodes.indexOfFirst { episode -> episode.id == currentEpisodeId }
        if (currentIndex > 0) {
            listState.scrollToItem(currentIndex)
        }
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(episodes, key = { episode -> episode.id }) { episode ->
            EpisodeCard(episode, episode.id == currentEpisodeId) { onEpisode(episode.id) }
        }
    }
}

@Composable
private fun EpisodeCard(episode: PlayerEpisode, current: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(224.dp).clickableWithoutRipple(onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EpisodeStill(episode, current)
        Text(
            "${episode.episodeNumber}. ${episodeName(episode.name, episode.episodeNumber)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!episode.overview.isNullOrEmpty()) {
            Text(episode.overview, fontSize = 12.sp, lineHeight = 16.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EpisodeStill(episode: PlayerEpisode, current: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    var frame = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(Color.White.copy(alpha = 0.05f))
    if (current) {
        frame = frame.border(2.dp, Color.White, shape)
    }
    Box(frame) {
        if (episode.stillUrl != null) {
            AsyncImage(episode.stillUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.align(Alignment.Center).size(40.dp).playerGlass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Fill.Play, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        if (episode.progress != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(episode.progress).background(Color.White))
            }
        }
    }
}
