package com.letsmoe.otter.tv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.MagnifyingGlass
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.CARD_SPACING
import com.letsmoe.otter.tv.ui.components.PosterCard
import com.letsmoe.otter.tv.ui.components.REGULAR_POSTER_WIDTH
import com.letsmoe.otter.tv.ui.components.SelectableChip
import com.letsmoe.otter.tv.ui.components.TextFieldLook
import com.letsmoe.otter.tv.ui.components.TvTextField
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.search.FilterGroup
import com.letsmoe.otter.ui.search.SearchState
import com.letsmoe.otter.ui.search.SearchType
import com.letsmoe.otter.ui.search.SearchViewModel

// Room for the top bar above the search field.
private val TOP_BAR_SPACE = 64.dp
private val FIELD_WIDTH = 360.dp

/** Search: a field, what to search and its filters, then the results as a poster grid (trending until something is typed). */
@Composable
fun SearchScreen(onOpenItem: (RailItem) -> Unit) {
    val viewModel = viewModel(key = "tv-search") { SearchViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().padding(top = TOP_BAR_SPACE),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SearchBar(state, viewModel)
        for (group in state.filterGroups) {
            FilterRow(group, chosenOption(state, group), onSelect = { value -> viewModel.selectFilter(group, value) })
        }
        Text(
            resultsTitle(state),
            fontSize = TvType.rowTitle,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = TvSpacing.screenHorizontal, top = 6.dp),
        )
        PosterGrid(shownTitles(state), onOpenItem)
    }
}

@Composable
private fun SearchBar(state: SearchState, viewModel: SearchViewModel) {
    Row(
        modifier = Modifier.padding(horizontal = TvSpacing.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TvTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            look = TextFieldLook(
                placeholder = "Titles, people, genres",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                icon = PhosphorIcons.Bold.MagnifyingGlass,
            ),
            modifier = Modifier.width(FIELD_WIDTH),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (type in SearchType.entries) {
                SelectableChip(type.label, selected = type == state.type, onClick = { viewModel.selectType(type) })
            }
        }
    }
}

/** One filter group (Rating, Length, Seasons): its label, then a chip per option. */
@Composable
private fun FilterRow(group: FilterGroup, chosen: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = TvSpacing.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(group.label, fontSize = TvType.detail, color = TvColors.secondaryContent, modifier = Modifier.width(64.dp))
        for (option in group.options) {
            SelectableChip(option.label, selected = option.value == chosen, onClick = { onSelect(option.value) })
        }
    }
}

@Composable
private fun PosterGrid(titles: List<RailItem>, onOpenItem: (RailItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.FixedSize(REGULAR_POSTER_WIDTH),
        modifier = Modifier.fillMaxSize(),
        // Room for the focused poster to grow without being clipped.
        contentPadding = PaddingValues(horizontal = TvSpacing.screenHorizontal, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        verticalArrangement = Arrangement.spacedBy(CARD_SPACING + 6.dp),
    ) {
        items(titles, key = { item -> item.id }) { item ->
            PosterCard(item, REGULAR_POSTER_WIDTH, onFocus = {}, onClick = { onOpenItem(item) })
        }
    }
}

// A group nobody touched shows its first option ("Any") as chosen.
private fun chosenOption(state: SearchState, group: FilterGroup): String {
    val chosen = state.chosenFilters[group.key]
    if (chosen == null) {
        return group.defaultValue
    }
    return chosen
}

// Without a query the grid shows what's trending.
private fun shownTitles(state: SearchState): List<RailItem> {
    if (!state.hasQuery) {
        return state.trending
    }
    return state.results
}

private fun resultsTitle(state: SearchState): String {
    if (!state.hasQuery) {
        return "Trending"
    }
    if (state.searching && state.results.isEmpty()) {
        return "Searching…"
    }
    if (state.results.isEmpty()) {
        return "Nothing found for “${state.query}”"
    }
    return "Results"
}
