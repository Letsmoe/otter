package com.letsmoe.otter.tv.ui.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.letsmoe.otter.api.tmdbImageUrl
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.CardRow
import com.letsmoe.otter.tv.ui.components.SelectableChip
import com.letsmoe.otter.tv.ui.components.WIDE_CARD_WIDTH
import com.letsmoe.otter.tv.ui.components.WideCard
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.title.EpisodesState
import com.letsmoe.otter.ui.title.PlayRequest
import com.letsmoe.otter.ui.title.SeasonEpisode
import com.letsmoe.otter.ui.title.SeasonInfo
import com.letsmoe.otter.ui.title.TitleDetail
import com.letsmoe.otter.ui.title.formatRuntime

/** The season chips (with more than one season) over the shown season's episodes; an episode plays when selected. */
@Composable
fun EpisodesSection(detail: TitleDetail, episodes: EpisodesState, onSelectSeason: (Int) -> Unit, onPlay: (PlayRequest) -> Unit) {
    val shownSeason = episodes.shownSeason
    val shownEpisodes = episodes.episodes.filter { episode -> episode.seasonNumber == shownSeason }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (detail.seasons.size > 1) {
            SeasonChips(detail.seasons, shownSeason, onSelectSeason)
        }
        CardRow(episodesTitle(episodes, shownEpisodes)) {
            items(shownEpisodes, key = { episode -> episode.id }) { episode ->
                WideCard(
                    episodeCard(episode),
                    WIDE_CARD_WIDTH,
                    onFocus = {},
                    onClick = { onPlay(PlayRequest(episode.id)) },
                )
            }
        }
    }
}

private fun episodesTitle(episodes: EpisodesState, shownEpisodes: List<SeasonEpisode>): String {
    if (!episodes.loaded) {
        return "Episodes"
    }
    if (shownEpisodes.isEmpty()) {
        return "No episodes yet"
    }
    return "Episodes"
}

/** An episode drawn as a wide card: its still, "E3 · Name", the runtime and how far it's watched. */
private fun episodeCard(episode: SeasonEpisode): RailItem {
    return RailItem(
        id = episode.id,
        mediaType = MediaType.SERIES,
        title = "E${episode.episodeNumber} · ${episode.displayName}",
        year = null,
        posterUrl = null,
        backdropUrl = tmdbImageUrl(episode.stillPath, "w300"),
        subtitle = formatRuntime(episode.runtime),
        progress = episode.progress,
        episodeId = episode.id,
        seasonNumber = episode.seasonNumber,
    )
}

@Composable
private fun SeasonChips(seasons: List<SeasonInfo>, shownSeason: Int?, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = TvSpacing.screenHorizontal, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(seasons, key = { season -> season.seasonNumber }) { season ->
            SelectableChip(seasonLabel(season), selected = season.seasonNumber == shownSeason, onClick = { onSelect(season.seasonNumber) })
        }
    }
}

private fun seasonLabel(season: SeasonInfo): String {
    val name = season.name
    if (!name.isNullOrEmpty()) {
        return name
    }
    return "Season ${season.seasonNumber}"
}
