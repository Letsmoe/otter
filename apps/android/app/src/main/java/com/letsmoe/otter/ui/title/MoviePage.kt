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
import com.letsmoe.otter.ui.watchorder.WatchOrderLink

private enum class MovieTab(val label: String) {
    RELATED("More Like This"),
    TRAILERS("Trailers & More"),
    SOURCES("Sources"),
    BOOKMARKS("Bookmarks"),
}

private val movieTabs = MovieTab.entries.map { tab -> tab to tab.label }

private const val STARRING_COUNT = 3

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
    onOpenWatchOrder: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MovieTab.RELATED) }
    val playLabel = moviePlayLabel(streams, detail.rewatching)
    TitlePage(
        detail = detail,
        facts = movieFacts(detail),
        playButton = PlayButtonState(
            label = playLabel,
            busy = streams.isBusy,
            ready = !streams.isBusy && !streams.vpnBlocked && streams.streams.isNotEmpty(),
            disabled = streams.nothingFound || streams.vpnBlocked,
            onClick = { onPlay(PlayRequest(detail.id)) },
            remainingMinutes = remainingMinutes(detail.runtime, detail.resumeFraction),
        ),
        creditLines = movieCreditLines(detail),
        shareUrl = movieLink(tmdbIdOf(detail.id), detail.title),
        actions = actions,
        slots = slots,
        downloadTarget = DownloadTarget(detail.id, detail.title),
        tabBar = { SegmentedBar(movieTabs, tab, onSelect = { chosen -> tab = chosen }) },
        rewatch = RewatchBarState(
            offer = rewatchOffer(detail.finishedBefore, detail.rewatching),
            onStart = { actions.onStartRewatch { onPlay(PlayRequest(detail.id)) } },
            onStop = { actions.onStopRewatch {} },
        ),
        playExtras = { FranchiseLink(detail.collectionId, onOpenWatchOrder) },
    ) {
        when (tab) {
            MovieTab.RELATED -> RelatedTab(rails, onOpenItem)
            MovieTab.TRAILERS -> TrailerTab(detail.trailerKey, detail.title, Modifier.padding(horizontal = 12.dp))
            MovieTab.BOOKMARKS -> BookmarksTab(detail.id, { null }, onPlay, Modifier.padding(horizontal = 12.dp))
            MovieTab.SOURCES -> SourceList(
                streams = streams,
                onPlay = { stream -> onPlay(PlayRequest(detail.id, stream.id)) },
                onSearchAgain = viewModel::searchAgain,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun FranchiseLink(collectionId: String?, onOpenWatchOrder: (String) -> Unit) {
    if (collectionId == null) {
        return
    }
    WatchOrderLink(collectionId, onOpen = { onOpenWatchOrder(collectionId) })
}

private fun moviePlayLabel(streams: StreamList, rewatching: Boolean): String {
    if (streams.vpnBlocked) {
        return "Connect to a VPN to watch"
    }
    if (streams.isBusy) {
        return "Searching sources…"
    }
    if (streams.nothingFound) {
        return "No sources found"
    }
    if (rewatching) {
        return "Continue rewatch"
    }
    return "Play"
}

/** "2014", "2h 49m", "★ 8.5" and the first two genres. */
fun movieFacts(detail: TitleDetail): List<String> {
    val facts = mutableListOf<String>()
    detail.year?.let { year -> facts.add(year.toString()) }
    formatRuntime(detail.runtime)?.let { runtime -> facts.add(runtime) }
    if (detail.voteAverage > 0) {
        facts.add("★ %.1f".format(detail.voteAverage))
    }
    if (detail.genres.isNotEmpty()) {
        facts.add(detail.genres.take(2).joinToString(" · "))
    }
    return facts
}

private fun movieCreditLines(detail: TitleDetail): List<String> {
    val lines = mutableListOf<String>()
    if (detail.cast.isNotEmpty()) {
        lines.add("Starring: " + detail.cast.take(STARRING_COUNT).joinToString(", ") { member -> member.name })
    }
    if (detail.directors.isNotEmpty()) {
        lines.add("Director: " + detail.directors.joinToString(", ") { director -> director.name })
    }
    return lines
}
