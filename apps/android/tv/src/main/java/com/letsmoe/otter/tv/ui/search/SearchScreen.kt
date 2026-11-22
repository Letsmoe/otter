package com.letsmoe.otter.tv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
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
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.CARD_SPACING
import com.letsmoe.otter.tv.ui.components.PillButton
import com.letsmoe.otter.tv.ui.components.PosterCard
import com.letsmoe.otter.tv.ui.components.REGULAR_POSTER_WIDTH
import com.letsmoe.otter.tv.ui.components.SelectField
import com.letsmoe.otter.tv.ui.components.SelectableChip
import com.letsmoe.otter.tv.ui.components.TextFieldLook
import com.letsmoe.otter.tv.ui.components.TvTextField
import com.letsmoe.otter.ui.media.RailItem

// Room for the top bar above the page.
private val TOP_BAR_SPACE = 72.dp
private val PANEL_WIDTH = 250.dp
// The next page loads once focus is this many titles from the end.
private const val LOAD_AHEAD_ITEMS = 12

/**
 * Search: the field, the category (movies or shows) and the filters on the left, the results on
 * the right as a poster grid that keeps loading as focus nears its end. Without a query the grid
 * shows the catalog, sorted and narrowed by the same filters.
 */
@Composable
fun SearchScreen(onOpenItem: (RailItem) -> Unit) {
    val viewModel = viewModel(key = "tv-search") { TvSearchViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier.fillMaxSize().padding(top = TOP_BAR_SPACE, start = TvSpacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SearchPanel(state, viewModel)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ResultsHeading(state)
            ResultsGrid(state, onOpenItem, onNearEnd = viewModel::loadMore)
        }
    }
}

@Composable
private fun SearchPanel(state: TvSearchState, viewModel: TvSearchViewModel) {
    Column(
        modifier = Modifier
            .width(PANEL_WIDTH)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 6.dp, bottom = TvSpacing.screenVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            look = TextFieldLook(
                placeholder = "Search",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                icon = PhosphorIcons.Bold.MagnifyingGlass,
                compact = true,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        CategoryChips(state.mediaType, viewModel::selectMediaType)
        FilterFields(state, viewModel)
    }
}

@Composable
private fun CategoryChips(selected: MediaType, onSelect: (MediaType) -> Unit) {
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectableChip("Movies", selected = selected == MediaType.MOVIE, onClick = { onSelect(MediaType.MOVIE) })
        SelectableChip("Shows", selected = selected == MediaType.SERIES, onClick = { onSelect(MediaType.SERIES) })
    }
}

/** Sort (only without a query: search results stay in relevance order), genre, years, rating and length or seasons. */
@Composable
private fun FilterFields(state: TvSearchState, viewModel: TvSearchViewModel) {
    val filters = state.filters
    val fieldModifier = Modifier.fillMaxWidth()
    if (!state.hasQuery) {
        SelectField("Sort by", sortOptions, filters.sort, { value -> viewModel.changeFilters { current -> current.copy(sort = value) } }, fieldModifier)
    }
    SelectField("Genre", genreOptions(state.genres), filters.genre, { value -> viewModel.changeFilters { current -> current.copy(genre = value) } }, fieldModifier)
    SelectField("Released", yearOptions, filters.years, { value -> viewModel.changeFilters { current -> current.copy(years = value) } }, fieldModifier)
    SelectField("Rating", ratingOptions, filters.rating, { value -> viewModel.changeFilters { current -> current.copy(rating = value) } }, fieldModifier)
    if (state.mediaType == MediaType.MOVIE) {
        SelectField("Length", lengthOptions, filters.length, { value -> viewModel.changeFilters { current -> current.copy(length = value) } }, fieldModifier)
    } else {
        SelectField("Seasons", seasonOptions, filters.seasons, { value -> viewModel.changeFilters { current -> current.copy(seasons = value) } }, fieldModifier)
    }
    if (filters != SearchFilters()) {
        PillButton("Reset filters", onClick = { viewModel.changeFilters { SearchFilters() } }, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The category as the heading, with what it shows under it. */
@Composable
private fun ResultsHeading(state: TvSearchState) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(categoryName(state.mediaType), fontSize = TvType.screenTitle, fontWeight = FontWeight.SemiBold)
        Text(resultsNote(state), fontSize = TvType.detail, color = TvColors.secondaryContent)
    }
}

@Composable
private fun ResultsGrid(state: TvSearchState, onOpenItem: (RailItem) -> Unit, onNearEnd: () -> Unit) {
    val gridState = rememberLazyGridState()
    LoadNearEnd(gridState, onNearEnd)
    // A new search or filter starts at the top.
    LaunchedEffect(state.query, state.mediaType, state.filters) { gridState.scrollToItem(0) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.FixedSize(REGULAR_POSTER_WIDTH),
        modifier = Modifier.fillMaxSize(),
        // Room for the focused poster to grow without being clipped.
        contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp, end = TvSpacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        verticalArrangement = Arrangement.spacedBy(CARD_SPACING + 6.dp),
    ) {
        items(state.items, key = { item -> item.id }) { item ->
            PosterCard(item, REGULAR_POSTER_WIDTH, onFocus = {}, onClick = { onOpenItem(item) })
        }
    }
}

@Composable
private fun LoadNearEnd(gridState: LazyGridState, onNearEnd: () -> Unit) {
    LaunchedEffect(gridState) {
        snapshotFlow { nearEnd(gridState) }.collect { near ->
            if (near) {
                onNearEnd()
            }
        }
    }
}

private fun nearEnd(gridState: LazyGridState): Boolean {
    val layout = gridState.layoutInfo
    val lastVisible = layout.visibleItemsInfo.lastOrNull() ?: return false
    return lastVisible.index >= layout.totalItemsCount - LOAD_AHEAD_ITEMS
}

private fun categoryName(mediaType: MediaType): String {
    if (mediaType == MediaType.SERIES) {
        return "Shows"
    }
    return "Movies"
}

private fun resultsNote(state: TvSearchState): String {
    if (state.failed && state.items.isEmpty()) {
        return "Couldn't load titles. Change a filter to try again."
    }
    if (state.items.isEmpty() && state.loading) {
        return "Loading…"
    }
    if (state.items.isEmpty()) {
        return "Nothing matches. Try other filters."
    }
    if (state.hasQuery) {
        return "Results for “${state.query.trim()}”"
    }
    return sortOptions.first { option -> option.value == state.filters.sort }.label
}
