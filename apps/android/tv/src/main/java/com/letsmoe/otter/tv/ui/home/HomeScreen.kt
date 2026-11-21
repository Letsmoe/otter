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
import androidx.compose.ui.focus.onFocusChanged
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
import com.letsmoe.otter.tv.ui.components.HeroRowsPage
import com.letsmoe.otter.tv.ui.components.PageRow
import com.letsmoe.otter.tv.ui.components.PillButton
import com.letsmoe.otter.tv.ui.components.ProfileButton
import com.letsmoe.otter.tv.ui.components.TopBar
import com.letsmoe.otter.tv.ui.search.SearchScreen
import com.letsmoe.otter.ui.home.TitlesPageState
import com.letsmoe.otter.ui.home.TitlesPageViewModel
import com.letsmoe.otter.ui.home.pageLabels
import com.letsmoe.otter.ui.home.toRows
import com.letsmoe.otter.ui.home.withActorsRow
import com.letsmoe.otter.ui.media.RailItem
import kotlinx.coroutines.delay

// What's left under the hero at the top of the page: one row of posters.
private val FIRST_ROW_SPACE = 222.dp
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
            center = { HomeTabs(tab, onSelect = { selected -> tab = selected }) },
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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.loadPersonalRows() }
    val hero = rememberSettledHero(mediaType)
    val heroTitle = heroFor(hero.settledItem, state)
    val actions = RowActions(
        onFocusItem = { item -> hero.focusedItem = item },
        onOpenItem = onOpenItem,
        onOpenPerson = onOpenPerson,
    )
    HeroRowsPage(
        firstRowSpace = FIRST_ROW_SPACE,
        rows = rowsOnceFeaturedArrives(mediaType, state, actions),
        backdrop = { modifier -> HeroBackdrop(heroTitle, modifier) },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom)) {
            HeroInfo(heroTitle)
            FeaturedActions(state, actions, viewModel::toggleFeaturedInMyList, onFocusFeatured = { hero.focusedItem = null })
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

// Before any card has focus the featured title fills the hero, with its genres.
private fun heroFor(focused: RailItem?, state: TitlesPageState): HeroTitle? {
    if (focused != null) {
        return HeroTitle(focused)
    }
    val featured = state.featured ?: return null
    return HeroTitle(featured.item, featured.genres)
}

/**
 * The page's rows, once the featured title is there or took too long: a card that took focus
 * before it would be pushed down by rows loading above it, and the page would follow.
 */
@Composable
private fun rowsOnceFeaturedArrives(mediaType: MediaType, state: TitlesPageState, actions: RowActions): List<PageRow> {
    var waitedForFeatured by remember(mediaType) { mutableStateOf(false) }
    LaunchedEffect(mediaType) {
        delay(FEATURED_WAIT_MILLISECONDS)
        waitedForFeatured = true
    }
    if (!waitedForFeatured && state.featured == null) {
        return emptyList()
    }
    val profileName = ProfileSession.activeProfile.value?.name.orEmpty()
    val rows = state.toRows(pageLabels(mediaType, profileName)).withActorsRow(state.actors.isNotEmpty())
    return homePageRows(rows, state.actors, actions)
}

/**
 * Open and My List for the featured title. Focus starts here once, when the featured title first
 * arrives; it reloads on resume and may change, which must not pull focus back from the rows.
 */
@Composable
private fun FeaturedActions(state: TitlesPageState, actions: RowActions, onToggleMyList: () -> Unit, onFocusFeatured: () -> Unit) {
    val featured = state.featured ?: return
    val openButton = remember { FocusRequester() }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            focusedOnce = true
            openButton.requestFocus()
        }
    }
    var myListIcon = PhosphorIcons.Bold.Plus
    if (state.featuredInMyList) {
        myListIcon = PhosphorIcons.Bold.Check
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TvSpacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PillButton(
            "More info",
            onClick = { actions.onOpenItem(featured.item) },
            icon = PhosphorIcons.Fill.Info,
            primary = true,
            modifier = Modifier.focusRequester(openButton).onFocusGained(onFocusFeatured),
        )
        PillButton("My List", onClick = onToggleMyList, icon = myListIcon, modifier = Modifier.onFocusGained(onFocusFeatured))
    }
}

private fun Modifier.onFocusGained(action: () -> Unit): Modifier {
    return onFocusChanged { state ->
        if (state.isFocused) {
            action()
        }
    }
}
