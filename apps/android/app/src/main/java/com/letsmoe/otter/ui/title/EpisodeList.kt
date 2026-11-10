package com.letsmoe.otter.ui.title

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.letsmoe.otter.api.tmdbImageUrl
import com.letsmoe.otter.ui.components.PRESSED_SCALE_LARGE
import com.letsmoe.otter.ui.components.frostedGlass
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.theme.OtterColors

private val stillShape = RoundedCornerShape(6.dp)
private const val OVERVIEW_COLLAPSED_LINES = 3

/**
 * Netflix's episode rows: still with a play badge and progress, "1. Name", runtime and a
 * download slot, the description underneath. [downloadButton] gets the episode id and its label.
 */
@Composable
fun EpisodeList(
    episodes: List<SeasonEpisode>,
    onPlay: (SeasonEpisode) -> Unit,
    modifier: Modifier = Modifier,
    downloadButton: @Composable (mediaId: String, label: String) -> Unit = { _, _ -> },
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        for (episode in episodes) {
            EpisodeRow(episode, onPlay, downloadButton)
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: SeasonEpisode,
    onPlay: (SeasonEpisode) -> Unit,
    downloadButton: @Composable (mediaId: String, label: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            EpisodeStill(episode, onClick = { onPlay(episode) })
            Column(Modifier.weight(1f).pressable(pressedScale = PRESSED_SCALE_LARGE) { onPlay(episode) }) {
                Text("${episode.episodeNumber}. ${episode.displayName}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val runtime = formatRuntime(episode.runtime)
                if (runtime != null) {
                    Text(runtime, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }
            downloadButton(episode.id, "${episode.code} ${episode.displayName}")
        }
        if (!episode.overview.isNullOrEmpty()) {
            EpisodeOverview(episode.overview)
        }
    }
}

// Tapping the description shows all of it, tapping again folds it back.
@Composable
private fun EpisodeOverview(overview: String) {
    var expanded by remember(overview) { mutableStateOf(false) }
    var maxLines = OVERVIEW_COLLAPSED_LINES
    if (expanded) {
        maxLines = Int.MAX_VALUE
    }
    Text(
        overview,
        fontSize = 12.sp,
        lineHeight = 19.sp,
        color = Color.White.copy(alpha = 0.6f),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .animateContentSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded },
    )
}

@Composable
private fun EpisodeStill(episode: SeasonEpisode, onClick: () -> Unit) {
    Box(Modifier.pressable(onClick = onClick).width(128.dp).aspectRatio(16f / 9f).clip(stillShape).background(Color.White.copy(alpha = 0.05f))) {
        val stillUrl = tmdbImageUrl(episode.stillPath, "w780")
        if (stillUrl != null) {
            AsyncImage(stillUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.align(Alignment.Center).size(36.dp).frostedGlass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Fill.Play, contentDescription = "Play ${episode.displayName}", modifier = Modifier.size(16.dp))
        }
        if (episode.progress != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(episode.progress).background(OtterColors.baseContent))
            }
        }
    }
}

/** "2h 5m" or "45m". */
fun formatRuntime(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) {
        return null
    }
    val hours = minutes / 60
    if (hours == 0) {
        return "${minutes % 60}m"
    }
    return "${hours}h ${minutes % 60}m"
}
