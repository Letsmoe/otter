package dev.neoworks.otter.ui.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neoworks.otter.ui.components.FilterSelect
import dev.neoworks.otter.ui.components.SegmentedBar
import dev.neoworks.otter.ui.media.RailItem

private enum class SeriesTab(val label: String) {
    EPISODES("Episodes"),
    RELATED("More Like This"),
    TRAILERS("Trailers & More"),
}

private val seriesTabs = SeriesTab.entries.map { tab -> tab to tab.label }

private const val STARRING_COUNT = 3

/** A series: Play resumes or starts the up-next episode; the tabs hold episodes by season, related titles and trailers. */
@Composable
fun SeriesPage(
    detail: TitleDetail,
    episodes: EpisodesState,
    rails: RailsState,
    seriesViewModel: SeriesViewModel,
    actions: TitlePageActions,
    slots: TitleSlots,
    onPlay: (PlayRequest) -> Unit,
    onOpenItem: (RailItem) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(SeriesTab.EPISODES) }
    val upNext = episodes.upNext
    TitlePage(
        detail = detail,
        facts = seriesFacts(detail),
        playButton = PlayButtonState(
            label = seriesPlayLabel(upNext),
            busy = !episodes.loaded,
            ready = episodes.loaded && upNext != null,
            disabled = episodes.loaded && upNext == null,
            onClick = { upNext?.let { next -> onPlay(PlayRequest(next.episode.id)) } },
        ),
        creditLines = seriesCreditLines(detail),
        shareUrl = "https://www.themoviedb.org/tv/${detail.id.substringAfter(':')}",
        actions = actions,
        slots = slots,
        tabBar = { SegmentedBar(seriesTabs, tab, onSelect = { chosen -> tab = chosen }) },
        playExtras = { UpNextBar(upNext) },
    ) {
        when (tab) {
            SeriesTab.EPISODES -> EpisodesTab(detail, episodes, seriesViewModel, slots, onPlay)
            SeriesTab.RELATED -> RelatedTab(rails, onOpenItem)
            SeriesTab.TRAILERS -> TrailerTab(detail.trailerKey, detail.title, Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun UpNextBar(upNext: UpNext?) {
    if (upNext == null || !upNext.resume) {
        return
    }
    var progress = 0f
    if (upNext.episode.progress != null) {
        progress = upNext.episode.progress
    }
    UpNextProgress(progress, "${upNext.episode.code} · ${upNext.episode.displayName}")
}

@Composable
private fun EpisodesTab(detail: TitleDetail, episodes: EpisodesState, seriesViewModel: SeriesViewModel, slots: TitleSlots, onPlay: (PlayRequest) -> Unit) {
    val shownEpisodes = episodes.episodes.filter { episode -> episode.seasonNumber == episodes.shownSeason }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (detail.seasons.size > 1 && episodes.shownSeason != null) {
            val options = detail.seasons.map { season -> season.seasonNumber to seasonLabel(season) }
            FilterSelect("Season", options, episodes.shownSeason, seriesViewModel::selectSeason, showLabel = false)
        }
        when {
            !episodes.loaded && episodes.episodes.isEmpty() -> Spinner()
            shownEpisodes.isEmpty() -> Hint("No episodes yet.")
            else -> EpisodeList(shownEpisodes, onPlay = { episode -> onPlay(PlayRequest(episode.id)) }, downloadButton = slots.episodeDownloadButton)
        }
    }
}

@Composable
private fun Spinner() {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
}

private fun seasonLabel(season: SeasonInfo): String {
    if (!season.name.isNullOrEmpty()) {
        return season.name
    }
    return "Season ${season.seasonNumber}"
}

private fun seriesPlayLabel(upNext: UpNext?): String {
    if (upNext == null) {
        return "Play"
    }
    if (upNext.resume) {
        return "Resume ${upNext.episode.code}"
    }
    return "Play ${upNext.episode.code}"
}

private fun seriesFacts(detail: TitleDetail): List<String> {
    val facts = mutableListOf<String>()
    detail.year?.let { year -> facts.add(year.toString()) }
    if (detail.regularSeasonCount == 1) {
        facts.add("1 Season")
    }
    if (detail.regularSeasonCount > 1) {
        facts.add("${detail.regularSeasonCount} Seasons")
    }
    if (detail.voteAverage > 0) {
        facts.add("★ %.1f".format(detail.voteAverage))
    }
    if (detail.genres.isNotEmpty()) {
        facts.add(detail.genres.take(2).joinToString(" · "))
    }
    return facts
}

private fun seriesCreditLines(detail: TitleDetail): List<String> {
    if (detail.cast.isEmpty()) {
        return emptyList()
    }
    return listOf("Starring: " + detail.cast.take(STARRING_COUNT).joinToString(", ") { member -> member.name })
}
