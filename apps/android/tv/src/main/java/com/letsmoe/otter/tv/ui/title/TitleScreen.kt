package com.letsmoe.otter.tv.ui.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.tv.ui.components.CardRow
import com.letsmoe.otter.tv.ui.components.FailedNotice
import com.letsmoe.otter.tv.ui.components.HeroRowsPage
import com.letsmoe.otter.tv.ui.components.PageRow
import com.letsmoe.otter.tv.ui.components.PosterCard
import com.letsmoe.otter.tv.ui.components.REGULAR_POSTER_WIDTH
import com.letsmoe.otter.tv.ui.components.TopBar
import com.letsmoe.otter.tv.ui.home.ActorRow
import com.letsmoe.otter.tv.ui.home.topBarFade
import com.letsmoe.otter.ui.LoadState
import com.letsmoe.otter.ui.components.ActorItem
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.title.CastMember
import com.letsmoe.otter.ui.title.LinkedEpisode
import com.letsmoe.otter.ui.title.UpNext
import com.letsmoe.otter.ui.title.PlayRequest
import com.letsmoe.otter.ui.title.RailsState
import com.letsmoe.otter.ui.title.SeriesViewModel
import com.letsmoe.otter.ui.title.TitleDetail
import com.letsmoe.otter.ui.title.TitleViewModel
import com.letsmoe.otter.ui.title.movieCreditLines
import com.letsmoe.otter.ui.title.movieFacts
import com.letsmoe.otter.ui.title.moviePlayLabel
import com.letsmoe.otter.ui.title.seriesCreditLines
import com.letsmoe.otter.ui.title.seriesFacts
import com.letsmoe.otter.ui.title.seriesPlayLabel
import com.letsmoe.otter.vpn.VpnStatusStore

// Under a movie's hero: one row of posters. Under a series' hero: the seasons and episodes.
private val MOVIE_FIRST_ROW_SPACE = 222.dp
private val SERIES_FIRST_ROW_SPACE = 196.dp

/** What a title page does with what's selected on it. */
class TitleNavigation(
    val onPlay: (PlayRequest) -> Unit,
    val onOpenItem: (RailItem) -> Unit,
    val onOpenPerson: (String) -> Unit,
)

/** A movie or series page, loaded from its id; a series opens on [linkedEpisode] when one is given. */
@Composable
fun TitleScreen(titleId: String, linkedEpisode: LinkedEpisode, navigation: TitleNavigation) {
    val viewModel = viewModel(key = "tv-title-$titleId") { TitleViewModel(titleId) }
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        when (val state = detail) {
            LoadState.Loading -> Unit
            is LoadState.Failed -> FailedNotice(
                "Couldn't open this title",
                state.message,
                onRetry = viewModel::load,
                modifier = Modifier.align(Alignment.Center),
            )
            is LoadState.Loaded -> LoadedTitle(state.value, viewModel, linkedEpisode, navigation)
        }
        TopBar(modifier = topBarFade())
    }
}

@Composable
private fun LoadedTitle(detail: TitleDetail, viewModel: TitleViewModel, linkedEpisode: LinkedEpisode, navigation: TitleNavigation) {
    if (detail.mediaType == MediaType.SERIES) {
        SeriesTitle(detail, viewModel, linkedEpisode, navigation)
        return
    }
    MovieTitle(detail, viewModel, navigation)
}

@Composable
private fun MovieTitle(detail: TitleDetail, viewModel: TitleViewModel, navigation: TitleNavigation) {
    val streams by viewModel.streams.collectAsStateWithLifecycle()
    val rails by viewModel.rails.collectAsStateWithLifecycle()
    val play = PlayAction(
        label = moviePlayLabel(streams),
        enabled = !streams.nothingFound && !streams.vpnBlocked,
        onClick = { navigation.onPlay(PlayRequest(detail.id)) },
    )
    TitleLayout(detail, MOVIE_FIRST_ROW_SPACE, relatedRows(rails, detail.cast.map(::actorOf), navigation)) {
        TitleHeader(detail, movieFacts(detail), movieCreditLines(detail), play, viewModel::toggleWatchlist)
    }
}

@Composable
private fun SeriesTitle(detail: TitleDetail, viewModel: TitleViewModel, linkedEpisode: LinkedEpisode, navigation: TitleNavigation) {
    val seriesViewModel = viewModel(key = "tv-series-${detail.id}") { SeriesViewModel(detail.id, linkedEpisode) }
    val episodes by seriesViewModel.state.collectAsStateWithLifecycle()
    val rails by viewModel.rails.collectAsStateWithLifecycle()
    val vpn by VpnStatusStore.status.collectAsStateWithLifecycle()
    // Back from the player (same activity, no resume) or the app, Resume and the progress bars should be current.
    LaunchedEffect(Unit) { seriesViewModel.loadEpisodes() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { seriesViewModel.loadEpisodes() }
    val upNext = episodes.upNext
    val play = PlayAction(
        label = seriesPlayLabel(upNext, vpn.blocksStreaming),
        enabled = upNext != null && !vpn.blocksStreaming,
        onClick = { playUpNext(upNext, navigation.onPlay) },
    )
    val episodesRow = PageRow("episodes") {
        EpisodesSection(detail, episodes, onSelectSeason = seriesViewModel::selectSeason, onPlay = navigation.onPlay)
    }
    val rows = listOf(episodesRow) + relatedRows(rails, detail.cast.map(::actorOf), navigation)
    TitleLayout(detail, SERIES_FIRST_ROW_SPACE, rows) {
        TitleHeader(detail, seriesFacts(detail), seriesCreditLines(detail), play, viewModel::toggleWatchlist)
    }
}

private fun playUpNext(upNext: UpNext?, onPlay: (PlayRequest) -> Unit) {
    if (upNext == null) {
        return
    }
    onPlay(PlayRequest(upNext.episode.id))
}

/** The hero with the title's details over its backdrop, then [rows]. */
@Composable
private fun TitleLayout(detail: TitleDetail, firstRowSpace: Dp, rows: List<PageRow>, header: @Composable () -> Unit) {
    HeroRowsPage(
        firstRowSpace = firstRowSpace,
        rows = rows,
        backdrop = { modifier -> TitleBackdrop(detail.bannerUrl, modifier) },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            header()
        }
    }
}

/** "More like this" and the other rails of the title, then its cast. */
private fun relatedRows(rails: RailsState, cast: List<ActorItem>, navigation: TitleNavigation): List<PageRow> {
    val rows = mutableListOf<PageRow>()
    for (rail in rails.rails) {
        if (rail.items.isEmpty()) {
            continue
        }
        rows.add(PageRow(rail.key) { RailRow(rail.title, rail.items, navigation.onOpenItem) })
    }
    if (cast.isNotEmpty()) {
        rows.add(PageRow("cast") { ActorRow("Cast", cast, navigation.onOpenPerson) })
    }
    return rows
}

@Composable
private fun RailRow(title: String, items: List<RailItem>, onOpenItem: (RailItem) -> Unit) {
    CardRow(title) {
        items(items, key = { item -> item.id }) { item ->
            PosterCard(item, REGULAR_POSTER_WIDTH, onFocus = {}, onClick = { onOpenItem(item) })
        }
    }
}

private fun actorOf(member: CastMember): ActorItem {
    return ActorItem(member.personId, member.name, member.profileUrl, member.character)
}
