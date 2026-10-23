package dev.neoworks.otter.ui.home

import androidx.compose.foundation.background
import dev.neoworks.otter.profiles.ProfileSession
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.ui.components.blurredCopyBehind
import dev.neoworks.otter.ui.components.GlassBackdropBox
import dev.neoworks.otter.ui.components.WithoutBackdrop
import dev.neoworks.otter.ui.media.RailItem

enum class HomeCategory(val label: String) {
    MOVIES("Movies"),
    TV("TV Shows"),
}

/**
 * Home: the "For <profile>" header floating over the page, which starts with the category chips
 * and the featured card on a gradient tinted by the card's poster.
 * [headerActions] is the slot for the Watch Together button left of search.
 */
@Composable
fun HomeScreen(
    onOpenItem: (RailItem) -> Unit,
    onOpenPerson: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    headerActions: @Composable RowScope.() -> Unit = {},
) {
    var category by rememberSaveable { mutableStateOf(HomeCategory.MOVIES) }
    val moviesListState = rememberLazyListState()
    val seriesListState = rememberLazyListState()
    val genreListState = rememberLazyListState()
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var categoriesOpen by remember { mutableStateOf(false) }
    val activeListState = listStateFor(category, selectedGenre, moviesListState, seriesListState, genreListState)
    var headerHeight by remember { mutableStateOf(0.dp) }
    var featuredPoster by remember { mutableStateOf<String?>(null) }
    val ambientColor = rememberAmbientColor(featuredPoster)
    val density = LocalDensity.current
    // derivedStateOf: recompose when crossing the top, not on every scrolled pixel.
    val scrolled by remember(activeListState) { derivedStateOf { isScrolled(activeListState) } }
    val page = HomePage(
        topPadding = headerHeight,
        ambientColor = ambientColor,
        onFeaturedPoster = { posterUrl -> featuredPoster = posterUrl },
        onOpenItem = onOpenItem,
        onOpenPerson = onOpenPerson,
        chips = {
            CategoryChips(
                selected = category,
                selectedGenre = selectedGenre,
                onSelect = { chosen -> category = chosen },
                onOpenCategories = { categoriesOpen = true },
                onClearGenre = { selectedGenre = null },
            )
        },
    )
    GlassBackdropBox(modifier.fillMaxSize()) { backdropSource ->
        Box(backdropSource.fillMaxSize().blurredCopyBehind(radius = 60.dp, dim = 0.15f)) {
            WithoutBackdrop {
                CategoryContent(category, selectedGenre, activeListState, page)
            }
        }
        HomeHeader(
            profileName = ProfileSession.activeProfile.value?.name.orEmpty(),
            ambientColor = ambientColor,
            scrolled = scrolled,
            onSearch = onSearch,
            modifier = Modifier.onSizeChanged { size ->
                if (size.height > 0) {
                    headerHeight = with(density) { size.height.toDp() }
                }
            },
            trailingActions = headerActions,
        )
    }
    if (categoriesOpen) {
        CategoriesSheet(
            selectedGenre = selectedGenre,
            onPick = { genre ->
                selectedGenre = genre
                categoriesOpen = false
            },
            onDismiss = { categoriesOpen = false },
        )
    }
}

/** What every category page needs from the Home screen around it. */
private class HomePage(
    val topPadding: Dp,
    val ambientColor: Color,
    val onFeaturedPoster: (String?) -> Unit,
    val onOpenItem: (RailItem) -> Unit,
    val onOpenPerson: (String) -> Unit,
    val chips: @Composable () -> Unit,
)

private fun listStateFor(
    category: HomeCategory,
    genre: String?,
    movies: LazyListState,
    series: LazyListState,
    genrePage: LazyListState,
): LazyListState {
    if (genre != null) {
        return genrePage
    }
    if (category == HomeCategory.MOVIES) {
        return movies
    }
    return series
}

private fun isScrolled(listState: LazyListState): Boolean =
    listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0

@Composable
private fun CategoryContent(category: HomeCategory, genre: String?, listState: LazyListState, page: HomePage) {
    var mediaType = MediaType.MOVIE
    if (category == HomeCategory.TV) {
        mediaType = MediaType.SERIES
    }
    if (genre != null) {
        GenrePage(mediaType, genre, listState, page.topPadding, page.ambientColor, page.onFeaturedPoster, page.onOpenItem, page.chips)
        return
    }
    TitlesPage(
        mediaType = mediaType,
        listState = listState,
        topPadding = page.topPadding,
        ambientColor = page.ambientColor,
        onFeaturedPoster = page.onFeaturedPoster,
        onOpenItem = page.onOpenItem,
        onOpenPerson = page.onOpenPerson,
        chips = page.chips,
    )
}
