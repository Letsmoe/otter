package com.letsmoe.otter.ui.title

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.ui.components.pressable

private val tileGap = 3.dp
private val labelWidth = 26.dp
private val smallestTile = 15.dp
private val largestTile = 26.dp
// Left over so rounding never pushes the last tile out of the row.
private val tileSlack = 4.dp
private val tileShape = RoundedCornerShape(3.dp)
private val mutedColor = Color.White.copy(alpha = 0.55f)
private const val LEGEND_STEPS = 12

/**
 * The episodes of a series at a glance: a row per season, a tile per episode coloured by its TMDB
 * rating (brighter is better), a dot on the ones the profile watched. Tapping a tile names the
 * episode with its rating and a Play button.
 */
@Composable
fun SeasonHeatmap(episodes: List<SeasonEpisode>, onPlay: (SeasonEpisode) -> Unit, modifier: Modifier = Modifier) {
    val seasons = heatmapSeasons(episodes)
    if (!worthShowing(seasons)) {
        return
    }
    var selectedId by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Episode ratings", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = mutedColor)
        HeatmapGrid(seasons, selectedId, onSelect = { episode -> selectedId = episode.id })
        val selected = episodes.firstOrNull { episode -> episode.id == selectedId }
        if (selected == null) {
            HeatmapLegend()
        } else {
            SelectedEpisode(selected, onPlay)
        }
    }
}

@Composable
private fun HeatmapGrid(seasons: List<HeatmapSeason>, selectedId: String?, onSelect: (SeasonEpisode) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tileSize = tileSizeFor(maxWidth, seasons.maxOf { season -> season.episodes.size })
        Row {
            Column(Modifier.width(labelWidth), verticalArrangement = Arrangement.spacedBy(tileGap)) {
                for (season in seasons) {
                    Box(Modifier.height(tileSize), contentAlignment = Alignment.CenterStart) {
                        Text("S${season.seasonNumber}", fontSize = 11.sp, color = mutedColor, maxLines = 1)
                    }
                }
            }
            Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(tileGap)) {
                for (season in seasons) {
                    SeasonRow(season, tileSize, selectedId, onSelect)
                }
            }
        }
    }
}

// As large as fits all of the longest season on the screen, within what is still tappable.
private fun tileSizeFor(available: Dp, longestSeason: Int): Dp {
    val gaps = tileGap * (longestSeason - 1)
    val fitting = (available - labelWidth - gaps - tileSlack) / longestSeason
    return fitting.coerceIn(smallestTile, largestTile)
}

@Composable
private fun SeasonRow(season: HeatmapSeason, tileSize: Dp, selectedId: String?, onSelect: (SeasonEpisode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(tileGap)) {
        for (episode in season.episodes) {
            EpisodeTile(episode, tileSize, selected = episode.id == selectedId, onClick = { onSelect(episode) })
        }
    }
}

@Composable
private fun EpisodeTile(episode: SeasonEpisode, size: Dp, selected: Boolean, onClick: () -> Unit) {
    var fill = Color.Transparent
    var border = Color.White.copy(alpha = 0.22f)
    if (hasRating(episode)) {
        fill = heatColor(episode.voteAverage)
        border = Color.Transparent
    }
    var borderWidth = 1.dp
    if (selected) {
        border = Color.White
        borderWidth = 2.dp
    }
    Box(
        Modifier
            .pressable(onClick = onClick)
            .semantics { contentDescription = heatmapTileDescription(episode) }
            .size(size)
            .clip(tileShape)
            .background(fill)
            .border(borderWidth, border, tileShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isWatched(episode)) {
            Box(Modifier.size(size / 3).background(markColorOn(fill), CircleShape))
        }
    }
}

@Composable
private fun SelectedEpisode(episode: SeasonEpisode, onPlay: (SeasonEpisode) -> Unit) {
    var detail = heatmapRatingText(episode)
    if (isWatched(episode)) {
        detail += " · Watched"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(heatmapEpisodeTitle(episode), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = 12.sp, color = mutedColor)
        }
        PillButton("Play", Modifier, enabled = true, filled = true, onClick = { onPlay(episode) })
    }
}

// Low to high across the scale, with a dot for watched.
@Composable
private fun HeatmapLegend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Lower", fontSize = 11.sp, color = mutedColor)
        val steps = (0..LEGEND_STEPS).map { step -> heatLegendColor(step / LEGEND_STEPS.toFloat()) }
        Box(Modifier.width(96.dp).height(8.dp).clip(CircleShape).background(Brush.horizontalGradient(steps)))
        Text("Higher", fontSize = 11.sp, color = mutedColor)
        Box(Modifier.padding(start = 8.dp).size(7.dp).background(Color.White, CircleShape))
        Text("Watched", fontSize = 11.sp, color = mutedColor)
    }
}
