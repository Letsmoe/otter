package com.letsmoe.otter.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.fill.Info
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.ui.components.POSTER_ROW_SPACE
import com.letsmoe.otter.tv.ui.components.HeroRowsPage
import com.letsmoe.otter.tv.ui.components.IconButton
import com.letsmoe.otter.tv.ui.components.PillButton
import com.letsmoe.otter.tv.ui.components.RatingButton
import com.letsmoe.otter.tv.ui.components.ProfileButton
import com.letsmoe.otter.tv.ui.components.TopBar
import com.letsmoe.otter.tv.ui.search.SearchScreen
import com.letsmoe.otter.tv.ui.settings.SettingsScreen
import com.letsmoe.otter.ui.home.HomeRow
import com.letsmoe.otter.ui.home.TitlesPageState
import com.letsmoe.otter.ui.home.TitlesPageViewModel
import com.letsmoe.otter.ui.home.pageLabels
import com.letsmoe.otter.ui.home.toRows
import com.letsmoe.otter.ui.home.withActorsRow
import com.letsmoe.otter.ui.media.RailItem
import kotlinx.coroutines.delay

// Rows wait this long for the featured title, so focus starts on its buttons rather than a card.
private const val FEATURED_WAIT_MILLISECONDS = 3_000L

/** Home: the Movies, Shows, Search and Settings tabs in the top bar over the selected [tab]. */
@Composable
fun HomeScreen(tab: HomeTab, onSelectTab: (HomeTab) -> Unit, onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit) {
    var tabsFocused by remember { mutableStateOf(false) }
    val page = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        key(tab) {
            Box(Modifier.pageFocusEntry(page)) {
                // A tab switched to from the tab bar leaves focus there.
                TabContent(tab, takeFocus = !tabsFocused, onOpenItem, onOpenPerson)
            }
        }
        AppTopBar(tab, page, selectOnFocus = true, onSelectTab, onTabsFocused = { focused -> tabsFocused = focused })
    }
}

/** A page under [AppTopBar]: Down from the tabs returns to what had focus here, or the first thing that can take it. */
fun Modifier.pageFocusEntry(page: FocusRequester): Modifier {
    return fillMaxSize().focusRequester(page).focusRestorer()
}

/** The wordmark, the home tabs and the profile, over home and title pages alike. */
@Composable
fun AppTopBar(
    tab: HomeTab,
    page: FocusRequester,
    selectOnFocus: Boolean,
    onSelectTab: (HomeTab) -> Unit,
    onTabsFocused: (Boolean) -> Unit = {},
) {
    TopBar(
        modifier = topBarFade(),
        tabs = {
            Box(Modifier.onFocusChanged { state -> onTabsFocused(state.hasFocus) }) {
                HomeTabs(tab, page, selectOnFocus, onSelect = onSelectTab)
            }
        },
        trailing = { ProfileButton() },
    )
}

@Composable
private fun TabContent(tab: HomeTab, takeFocus: Boolean, onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit) {
    if (tab == HomeTab.SEARCH) {
        SearchScreen(onOpenItem)
        return
    }
    if (tab == HomeTab.SETTINGS) {
        SettingsScreen()
        return
    }
    val mediaType = tab.mediaType ?: return
    TitlesHome(mediaType, takeFocus, onOpenItem, onOpenPerson)
}

/** Rows scroll under the top bar; the fade keeps it readable over them. */
fun topBarFade(): Modifier {
    return Modifier.background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.9f), 1f to Color.Transparent))
}

/** One home category (movies or shows): the hero cycling through featured titles, then the rows. */
@Composable
fun TitlesHome(mediaType: MediaType, takeFocus: Boolean, onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit) {
    val viewModel = viewModel(key = "tv-titles-$mediaType") { TitlesPageViewModel(mediaType) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from a title or the player (same activity, no resume), Continue Watching and My List should be current.
    LaunchedEffect(Unit) { viewModel.loadPersonalRows() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.loadPersonalRows() }
    val carousel = rememberHeroCarousel(mediaType, state)
    val actions = RowActions(onOpenItem = onOpenItem, onOpenPerson = onOpenPerson)
    val rows = rowsOnceFeaturedArrives(mediaType, state)
    // Below its own rows home keeps going with catalog rows as focus nears the end.
    val moreRowsViewModel = viewModel(key = "tv-more-rows-$mediaType") { MoreRowsViewModel(mediaType) }
    val moreRows by moreRowsViewModel.rows.collectAsStateWithLifecycle()
    val shownGenres = state.genreRails.map { rail -> rail.genre }
    HeroRowsPage(
        firstRowSpace = POSTER_ROW_SPACE,
        rows = homePageRows(rows + moreRows, state.actors, actions),
        backdrop = { modifier -> HeroBackdrop(carousel.current, modifier) },
        onNearEnd = { moreRowsViewModel.loadMore(shownGenres) },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Bottom),
            ) {
                HeroInfo(carousel.current)
                FeaturedActions(carousel, actions, takeFocus)
            }
        }
    }
}

/**
 * The page's rows, once the featured title is there or took too long: a card that took focus
 * before it would be pushed down by rows loading above it, and the page would follow.
 */
@Composable
private fun rowsOnceFeaturedArrives(mediaType: MediaType, state: TitlesPageState): List<HomeRow> {
    var waitedForFeatured by remember(mediaType) { mutableStateOf(false) }
    LaunchedEffect(mediaType) {
        delay(FEATURED_WAIT_MILLISECONDS)
        waitedForFeatured = true
    }
    if (!waitedForFeatured && state.featured == null) {
        return emptyList()
    }
    val profileName = ProfileSession.activeProfile.value?.name.orEmpty()
    return state.toRows(pageLabels(mediaType, profileName), recommendedFirst = true).withActorsRow(state.actors.isNotEmpty())
}

/**
 * More info, My List and the rating for the carousel's current title; left on More info and right
 * on the rating thumb move through the carousel. Focus starts here once, when the featured title first arrives; it
 * reloads on resume and may change, which must not pull focus back from the rows. Without
 * [takeFocus] (the tab bar has it), focus stays where it is.
 */
@Composable
private fun FeaturedActions(carousel: HeroCarousel, actions: RowActions, takeFocus: Boolean) {
    val current = carousel.current ?: return
    val openButton = remember { FocusRequester() }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (focusedOnce) {
            return@LaunchedEffect
        }
        focusedOnce = true
        if (takeFocus) {
            openButton.requestFocus()
        }
    }
    var myListIcon = PhosphorIcons.Bold.Plus
    if (carousel.isInMyList(current.item.id)) {
        myListIcon = PhosphorIcons.Bold.Check
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvSpacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(
            "More info",
            onClick = { actions.onOpenItem(current.item) },
            icon = PhosphorIcons.Fill.Info,
            primary = true,
            modifier = Modifier
                .focusRequester(openButton)
                .onPreviewKeyEvent { event -> carouselKeys(event, Key.DirectionLeft, carousel::previous) },
        )
        IconButton(myListIcon, "My List", onClick = carousel::toggleMyList)
        RatingButton(
            carousel.ratingOf(current.item.id),
            onRate = carousel::rate,
            thumbModifier = Modifier.onPreviewKeyEvent { event -> carouselKeys(event, Key.DirectionRight, carousel::next) },
        )
    }
}
