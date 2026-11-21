package com.letsmoe.otter.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
import com.letsmoe.otter.tv.ui.components.ManualFocusScroll
import com.letsmoe.otter.tv.ui.components.PillButton
import com.letsmoe.otter.tv.ui.components.ProfileButton
import com.letsmoe.otter.tv.ui.components.TopBar
import com.letsmoe.otter.ui.home.TitlesPageState
import com.letsmoe.otter.ui.home.TitlesPageViewModel
import com.letsmoe.otter.ui.home.pageLabels
import com.letsmoe.otter.ui.home.toRows
import com.letsmoe.otter.ui.home.withActorsRow
import com.letsmoe.otter.ui.media.RailItem
import kotlinx.coroutines.delay

// What's left under the hero at the top of the page: one row of posters.
private val FIRST_ROW_SPACE = 222.dp
// Rows below the first scroll to just under the top bar.
private val ROWS_TOP_LINE = 56.dp
private val ROW_SPACING = 12.dp
// The backdrop moves at this share of the scroll speed: the parallax.
private const val PARALLAX_FACTOR = 0.5f
// Scrolling fast through a row shouldn't reload the backdrop for every card it passes.
private const val HERO_SETTLE_MILLISECONDS = 250L
// Focus on the featured buttons; rows count from 0.
private const val HERO_FOCUSED = -1
// Rows wait this long for the featured title, so focus starts on its buttons rather than a card.
private const val FEATURED_WAIT_MILLISECONDS = 3_000L

/** Home: the Movies and Shows tabs in the top bar over the selected category. */
@Composable
fun HomeScreen(onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.MOVIES) }
    Box(Modifier.fillMaxSize()) {
        key(tab) {
            TitlesHome(tab.mediaType, onOpenItem, onOpenPerson)
        }
        // The rows scroll under the top bar; the fade keeps it readable over them.
        TopBar(
            modifier = Modifier.background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.9f), 1f to Color.Transparent)),
            center = { HomeTabs(tab, onSelect = { selected -> tab = selected }) },
            trailing = { ProfileButton() },
        )
    }
}

/**
 * One home category (movies or shows): a hero for the featured or focused title with one row
 * under it. Further rows scroll the hero away and fill the screen; the backdrop follows slower.
 */
@Composable
fun TitlesHome(mediaType: MediaType, onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit) {
    val viewModel = viewModel(key = "tv-titles-$mediaType") { TitlesPageViewModel(mediaType) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.loadPersonalRows() }
    val hero = rememberSettledHero(mediaType)
    val heroTitle = heroFor(hero.settledItem, state)
    val listState = rememberLazyListState()
    var focusedRow by remember(mediaType) { mutableIntStateOf(HERO_FOCUSED) }
    ScrollFocusedRowIntoPlace(listState, focusedRow)
    var waitedForFeatured by remember(mediaType) { mutableStateOf(false) }
    LaunchedEffect(mediaType) {
        delay(FEATURED_WAIT_MILLISECONDS)
        waitedForFeatured = true
    }
    val showRows = waitedForFeatured || state.featured != null
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = maxHeight - FIRST_ROW_SPACE
        HeroBackdrop(heroTitle, Modifier.parallax(listState, heroHeight))
        val actions = RowActions(
            onFocusItem = { item -> hero.focusedItem = item },
            onOpenItem = onOpenItem,
            onOpenPerson = onOpenPerson,
        )
        val page = HomePage(state, mediaType, actions, viewModel::toggleFeaturedInMyList, showRows)
        val heroArea = HeroArea(heroTitle, heroHeight, onFeaturedFocused = { hero.focusedItem = null })
        HomeList(page, heroArea, listState, screenHeight = maxHeight) { row -> focusedRow = row }
    }
}

/** The card the hero follows; [settledItem] catches up once focus rests on a card for a moment. */
private class HeroState {
    var focusedItem by mutableStateOf<RailItem?>(null)
    var settledItem by mutableStateOf<RailItem?>(null)
}

@Composable
private fun rememberSettledHero(mediaType: MediaType): HeroState {
    val hero = remember(mediaType) { HeroState() }
    LaunchedEffect(hero.focusedItem) {
        delay(HERO_SETTLE_MILLISECONDS)
        hero.settledItem = hero.focusedItem
    }
    return hero
}

// Before any card has focus the featured title fills the hero, with its genres.
private fun heroFor(focused: RailItem?, state: TitlesPageState): HeroTitle? {
    if (focused != null) {
        return HeroTitle(focused)
    }
    val featured = state.featured ?: return null
    return HeroTitle(featured.item, featured.genres)
}

/** The hero and the first row stay at the top; any later row scrolls to just under the top bar. */
@Composable
private fun ScrollFocusedRowIntoPlace(listState: LazyListState, focusedRow: Int) {
    val topLine = with(LocalDensity.current) { ROWS_TOP_LINE.roundToPx() }
    LaunchedEffect(focusedRow) {
        if (focusedRow <= 0) {
            listState.animateScrollToItem(0)
            return@LaunchedEffect
        }
        // Item 0 is the hero, so row n is item n + 1; a negative offset leaves room above it.
        listState.animateScrollToItem(focusedRow + 1, -topLine)
    }
}

/** Moves the backdrop up at a fraction of the scroll and fades it out as the hero leaves. */
private fun Modifier.parallax(listState: LazyListState, heroHeight: Dp): Modifier {
    return graphicsLayer {
        val heroPixels = heroHeight.toPx()
        val scrolled = heroScrollOf(listState, heroPixels)
        translationY = -scrolled * PARALLAX_FACTOR
        alpha = 1f - (scrolled / heroPixels).coerceIn(0f, 1f)
    }
}

// Past the hero the exact distance doesn't matter: the backdrop is gone by then.
private fun heroScrollOf(listState: LazyListState, heroPixels: Float): Float {
    if (listState.firstVisibleItemIndex > 0) {
        return heroPixels
    }
    return listState.firstVisibleItemScrollOffset.toFloat()
}

/** What the home list draws and does. */
private class HomePage(
    val state: TitlesPageState,
    val mediaType: MediaType,
    val actions: RowActions,
    val onToggleMyList: () -> Unit,
    val showRows: Boolean,
)

/** The list's first item: the hero's text and the featured buttons. */
private class HeroArea(val title: HeroTitle?, val height: Dp, val onFeaturedFocused: () -> Unit)

@Composable
private fun HomeList(
    page: HomePage,
    heroArea: HeroArea,
    listState: LazyListState,
    screenHeight: Dp,
    onRowFocused: (Int) -> Unit,
) {
    val profileName = ProfileSession.activeProfile.value?.name.orEmpty()
    val rows = page.state.toRows(pageLabels(page.mediaType, profileName)).withActorsRow(page.state.actors.isNotEmpty())
    ManualFocusScroll {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Room for the last row to scroll up to the top line too.
            contentPadding = PaddingValues(bottom = screenHeight),
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        ) {
            item(key = "hero") {
                HeroItem(page, heroArea, onFocused = { onRowFocused(HERO_FOCUSED) })
            }
            if (page.showRows) {
                homeRows(rows, page.state.actors, page.actions, onRowFocused)
            }
        }
    }
}

/** The hero's text and the featured buttons, at the bottom of the space above the first row. */
@Composable
private fun HeroItem(page: HomePage, heroArea: HeroArea, onFocused: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().height(heroArea.height),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
    ) {
        HeroInfo(heroArea.title)
        FeaturedActions(page, onFocusFeatured = {
            heroArea.onFeaturedFocused()
            onFocused()
        })
    }
}

/**
 * Open and My List for the featured title. Focus starts here once, when the featured title first
 * arrives; it reloads on resume and may change, which must not pull focus back from the rows.
 */
@Composable
private fun FeaturedActions(page: HomePage, onFocusFeatured: () -> Unit) {
    val featured = page.state.featured ?: return
    val openButton = remember { FocusRequester() }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            focusedOnce = true
            openButton.requestFocus()
        }
    }
    var myListIcon = PhosphorIcons.Bold.Plus
    if (page.state.featuredInMyList) {
        myListIcon = PhosphorIcons.Bold.Check
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TvSpacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PillButton(
            "More info",
            onClick = { page.actions.onOpenItem(featured.item) },
            icon = PhosphorIcons.Fill.Info,
            primary = true,
            modifier = Modifier.focusRequester(openButton).onFocusGained(onFocusFeatured),
        )
        PillButton("My List", onClick = page.onToggleMyList, icon = myListIcon, modifier = Modifier.onFocusGained(onFocusFeatured))
    }
}

private fun Modifier.onFocusGained(action: () -> Unit): Modifier {
    return onFocusChanged { state ->
        if (state.isFocused) {
            action()
        }
    }
}
