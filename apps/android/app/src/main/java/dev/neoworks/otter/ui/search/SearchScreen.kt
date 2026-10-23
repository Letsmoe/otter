package dev.neoworks.otter.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neoworks.otter.ui.components.FilterSelect
import dev.neoworks.otter.ui.components.PosterRail
import dev.neoworks.otter.ui.components.PosterSize
import dev.neoworks.otter.ui.components.PosterTile
import dev.neoworks.otter.ui.components.SegmentedBar
import dev.neoworks.otter.ui.components.WithoutBackdrop
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.media.onlyKind
import dev.neoworks.otter.ui.media.withoutRepeats
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.ui.navigation.bottomBarClearance

private const val TOP_RESULT_COUNT = 6
private val typeOptions = SearchType.entries.map { type -> type to type.label }

/** Search bar, a type switch (All, Movies, TV Shows) and, for one type, filters; results as rails or a grid. */
@Composable
fun SearchScreen(onOpenItem: (RailItem) -> Unit, onBack: () -> Unit, viewModel: SearchViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WithoutBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SearchField(state.query, viewModel::onQueryChange, viewModel::clearQuery, onBack)
                SegmentedBar(typeOptions, state.type, onSelect = viewModel::selectType)
            }
            FilterRow(state, viewModel::selectFilter)
            SearchResults(state, onOpenItem)
        }
    }
}

@Composable
private fun FilterRow(state: SearchState, onSelect: (FilterGroup, String) -> Unit) {
    if (state.filterGroups.isEmpty()) {
        return
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.filterGroups, key = { group -> group.key }) { group ->
            val options = group.options.map { option -> option.value to option.label }
            val selected = chosenValue(state, group)
            FilterSelect(group.label, options, selected, onSelect = { value -> onSelect(group, value) })
        }
    }
}

private fun chosenValue(state: SearchState, group: FilterGroup): String {
    val chosen = state.chosenFilters[group.key]
    if (chosen == null) {
        return group.defaultValue
    }
    return chosen
}

@Composable
private fun SearchResults(state: SearchState, onOpenItem: (RailItem) -> Unit) {
    when {
        !state.hasQuery -> TrendingResults(state, onOpenItem)
        state.searching && state.results.isEmpty() -> CenteredSpinner()
        state.results.isEmpty() -> NoResults(state.query.trim())
        state.type == SearchType.ALL -> RailResults(state, onOpenItem)
        else -> GridResults(state, onOpenItem)
    }
}

@Composable
private fun TrendingResults(state: SearchState, onOpenItem: (RailItem) -> Unit) {
    PosterRail("Trending Now", state.trending, onOpenItem)
}

@Composable
private fun CenteredSpinner() {
    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}

@Composable
private fun NoResults(query: String) {
    Text(
        "No results for “$query”.",
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp).alpha(0.6f),
    )
}

@Composable
private fun RailResults(state: SearchState, onOpenItem: (RailItem) -> Unit) {
    val rails = withoutRepeats(
        listOf(
            state.results.take(TOP_RESULT_COUNT),
            state.results.onlyKind(MediaType.MOVIE),
            state.results.onlyKind(MediaType.SERIES),
        ) + state.relatedRows.map { row -> row.items },
    )
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomBarClearance()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        railItem("Top Results", rails[0], onOpenItem, PosterSize.LARGE)
        railItem("More Movies", rails[1], onOpenItem)
        railItem("More TV Shows", rails[2], onOpenItem)
        state.relatedRows.forEachIndexed { index, row -> railItem(row.title, rails[3 + index], onOpenItem) }
    }
}

private fun LazyListScope.railItem(title: String, items: List<RailItem>, onOpenItem: (RailItem) -> Unit, size: PosterSize = PosterSize.REGULAR) {
    if (items.isEmpty()) {
        return
    }
    item(key = title) { PosterRail(title, items, onOpenItem, size = size) }
}

@Composable
private fun GridResults(state: SearchState, onOpenItem: (RailItem) -> Unit) {
    var alpha = 1f
    if (state.searching) {
        alpha = 0.6f
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().alpha(alpha),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomBarClearance()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItems(state.results, key = { item -> item.id }) { item -> PosterTile(item, onClick = { onOpenItem(item) }) }
    }
}
