package dev.neoworks.otter.ui.title

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.neoworks.otter.ui.components.SegmentedBar
import dev.neoworks.otter.ui.media.RailItem

private enum class MovieTab(val label: String) {
    RELATED("More Like This"),
    TRAILERS("Trailers & More"),
    SOURCES("Sources"),
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
) {
    var tab by rememberSaveable { mutableStateOf(MovieTab.RELATED) }
    val playLabel = moviePlayLabel(streams)
    TitlePage(
        detail = detail,
        facts = movieFacts(detail),
        playButton = PlayButtonState(
            label = playLabel,
            busy = streams.isBusy,
            ready = !streams.isBusy && streams.streams.isNotEmpty(),
            disabled = streams.nothingFound,
            onClick = { onPlay(PlayRequest(detail.id)) },
        ),
        creditLines = movieCreditLines(detail),
        shareUrl = "https://www.themoviedb.org/movie/${detail.id.substringAfter(':')}",
        actions = actions,
        slots = slots,
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

private fun moviePlayLabel(streams: StreamList): String {
    if (streams.isBusy) {
        return "Searching sources…"
    }
    if (streams.nothingFound) {
        return "No sources found"
    }
    return "Play"
}

private fun movieFacts(detail: TitleDetail): List<String> {
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
