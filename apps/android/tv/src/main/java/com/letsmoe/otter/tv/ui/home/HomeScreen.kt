package com.letsmoe.otter.tv.ui.home

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.letsmoe.otter.tv.ui.components.BackdropRowsPage
import com.letsmoe.otter.tv.ui.components.PillButton
import com.letsmoe.otter.tv.ui.components.ProfileButton
import com.letsmoe.otter.tv.ui.components.TopBar
import com.letsmoe.otter.tv.ui.search.SearchScreen
import com.letsmoe.otter.ui.home.HomeRow
import com.letsmoe.otter.ui.home.TitlesPageState
import com.letsmoe.otter.ui.home.TitlesPageViewModel
import com.letsmoe.otter.ui.home.pageLabels
import com.letsmoe.otter.ui.home.toRows
import com.letsmoe.otter.ui.home.withActorsRow
import com.letsmoe.otter.ui.media.RailItem
import kotlinx.coroutines.delay

// Scrolling fast through a row shouldn't reload the backdrop for every card it passes.
private const val HERO_SETTLE_MILLISECONDS = 250L
// Rows wait this long for the featured title, so focus starts on its buttons rather than a card.
private const val FEATURED_WAIT_MILLISECONDS = 3_000L

/** Home: the Search, Movies and Shows tabs in the top bar over the selected one. */
@Composable
fun HomeScreen(onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.MOVIES) }
    Box(Modifier.fillMaxSize()) {
        key(tab) {
            TabContent(tab, onOpenItem, onOpenPerson)
        }
        TopBar(
            modifier = topBarFade(),
            tabs = { HomeTabs(tab, onSelect = { selected -> tab = selected }) },
            trailing = { ProfileButton() },
        )
    }
}

@Composable
private fun TabContent(tab: HomeTab, onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit) {
    val mediaType = tab.mediaType
    if (mediaType == null) {
        SearchScreen(onOpenItem)
        return
    }
    TitlesHome(mediaType, onOpenItem, onOpenPerson)
}

/** Rows scroll under the top bar; the fade keeps it readable over them. */
fun topBarFade(): Modifier {
    return Modifier.background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.9f), 1f to Color.Transparent))
}

/** One home category (movies or shows): a hero for the featured or focused title, then the rows. */
@Composable
fun TitlesHome(mediaType: MediaType, onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit) {
    val viewModel = viewModel(key = "tv-titles-$mediaType") { TitlesPageViewModel(mediaType) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from a title or the player (same activity, no resume), Continue Watching and My List should be current.
    LaunchedEffect(Unit) { viewModel.loadPersonalRows() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.loadPersonalRows() }
    val hero = rememberSettledHero(mediaType)
    // The hero cycles while no card has focus; on a card it shows that card's title.
    val carousel = rememberHeroCarousel(mediaType, state, rotating = hero.focusedItem == null)
    val heroTitle = heroFor(hero.settledItem, carousel)
    val actions = RowActions(
        onFocusItem = { item -> hero.focusedItem = item },
        onOpenItem = onOpenItem,
        onOpenPerson = onOpenPerson,
    )
    val rows = rowsOnceFeaturedArrives(mediaType, state)
    BackdropRowsPage(
        rows = homePageRows(rows, state.actors, actions),
        backdrop = { HeroBackdrop(heroTitle) },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
        ) {
            HeroInfo(heroTitle)
            FeaturedActions(
                carousel,
                actions,
                browsingRows = hero.focusedItem != null,
                onFocusFeatured = { hero.focusedItem = null },
            )
        }
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

// Without a focused card the hero shows the carousel's current title.
private fun heroFor(focused: RailItem?, carousel: HeroCarousel): HeroTitle? {
    if (focused != null) {
        return HeroTitle(focused)
    }
    return carousel.current
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
    return state.toRows(pageLabels(mediaType, profileName)).withActorsRow(state.actors.isNotEmpty())
}

/**
 * More info and My List for the carousel's current title; left on More info and right on My List
 * move through the carousel. Focus starts here once, when the featured title first arrives; it
 * reloads on resume and may change, which must not pull focus back from the rows.
 *
 * While [browsingRows], the buttons fade out but stay focusable, so Up from the first row still
 * lands on them.
 */
@Composable
private fun FeaturedActions(
    carousel: HeroCarousel,
    actions: RowActions,
    browsingRows: Boolean,
    onFocusFeatured: () -> Unit,
) {
    val current = carousel.current ?: return
    val openButton = remember { FocusRequester() }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            focusedOnce = true
            openButton.requestFocus()
        }
    }
    var myListIcon = PhosphorIcons.Bold.Plus
    if (carousel.isInMyList(current.item.id)) {
        myListIcon = PhosphorIcons.Bold.Check
    }
    var targetAlpha = 1f
    if (browsingRows) {
        targetAlpha = 0f
    }
    val alpha by animateFloatAsState(targetAlpha, label = "featuredActionsAlpha")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvSpacing.screenHorizontal)
            .graphicsLayer { this.alpha = alpha },
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
                .onFocusGained(onFocusFeatured)
                .onPreviewKeyEvent { event -> carouselKeys(event, Key.DirectionLeft, carousel::previous) },
        )
        PillButton(
            "My List",
            onClick = carousel::toggleMyList,
            icon = myListIcon,
            modifier = Modifier
                .onFocusGained(onFocusFeatured)
                .onPreviewKeyEvent { event -> carouselKeys(event, Key.DirectionRight, carousel::next) },
        )
        CarouselDots(carousel, Modifier.padding(start = 10.dp))
    }
}

private fun Modifier.onFocusGained(action: () -> Unit): Modifier {
    return onFocusChanged { state ->
        if (state.isFocused) {
            action()
        }
    }
}
