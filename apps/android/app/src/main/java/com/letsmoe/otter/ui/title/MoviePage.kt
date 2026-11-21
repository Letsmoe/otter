package com.letsmoe.otter.ui.title

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.letsmoe.otter.links.movieLink
import com.letsmoe.otter.links.tmdbIdOf
import com.letsmoe.otter.ui.components.SegmentedBar
import com.letsmoe.otter.ui.media.RailItem

private enum class MovieTab(val label: String) {
    RELATED("More Like This"),
    TRAILERS("Trailers & More"),
    SOURCES("Sources"),
}

private val movieTabs = MovieTab.entries.map { tab -> tab to tab.label }

/** A movie: Play starts the best source; the tabs hold related titles, trailers and every source. */
@Composable
fun MoviePage(
    detail: TitleDetail,
    streams: StreamList,
    rails: RailsState,
    viewModel: TitleViewModel,
    actions: TitlePageActions,
    slots: TitleSlots,
    onPlay: (PlayRequest) -> Unit,
    onOpenItem: (RailItem) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MovieTab.RELATED) }
    val playLabel = moviePlayLabel(streams)
    TitlePage(
        detail = detail,
        facts = movieFacts(detail),
        playButton = PlayButtonState(
            label = playLabel,
            busy = streams.isBusy,
            ready = !streams.isBusy && !streams.vpnBlocked && streams.streams.isNotEmpty(),
            disabled = streams.nothingFound || streams.vpnBlocked,
            onClick = { onPlay(PlayRequest(detail.id)) },
        ),
        creditLines = movieCreditLines(detail),
        shareUrl = movieLink(tmdbIdOf(detail.id), detail.title),
        actions = actions,
        slots = slots,
        downloadTarget = DownloadTarget(detail.id, detail.title),
        tabBar = { SegmentedBar(movieTabs, tab, onSelect = { chosen -> tab = chosen }) },
    ) {
        when (tab) {
            MovieTab.RELATED -> RelatedTab(rails, onOpenItem)
            MovieTab.TRAILERS -> TrailerTab(detail.trailerKey, detail.title, Modifier.padding(horizontal = 12.dp))
            MovieTab.SOURCES -> SourceList(
                streams = streams,
                onPlay = { stream -> onPlay(PlayRequest(detail.id, stream.id)) },
                onSearchAgain = viewModel::searchAgain,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
