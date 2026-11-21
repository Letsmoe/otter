package com.letsmoe.otter.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.letsmoe.otter.tv.ui.components.FocusPivot
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

// Room for the top bar above the hero text.
private val TOP_BAR_SPACE = 84.dp
private val HERO_INFO_HEIGHT = 120.dp
// Scrolling fast through a row shouldn't reload the backdrop for every card it passes.
private const val HERO_SETTLE_MILLISECONDS = 250L

/** Home: the Movies and Shows tabs in the top bar over the selected category. */
@Composable
fun HomeScreen(onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.MOVIES) }
    Box(Modifier.fillMaxSize()) {
        key(tab) {
            TitlesHome(tab.mediaType, onOpenItem, onOpenPerson)
        }
        TopBar(
            center = { HomeTabs(tab, onSelect = { selected -> tab = selected }) },
            trailing = { ProfileButton() },
        )
    }
}

/** One home category (movies or shows): the hero for the focused title, then the rows. */
@Composable
fun TitlesHome(mediaType: MediaType, onOpenItem: (RailItem) -> Unit, onOpenPerson: (String) -> Unit) {
    val viewModel = viewModel(key = "tv-titles-$mediaType") { TitlesPageViewModel(mediaType) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.loadPersonalRows() }
    var focusedItem by remember(mediaType) { mutableStateOf<RailItem?>(null) }
    var settledItem by remember(mediaType) { mutableStateOf<RailItem?>(null) }
    LaunchedEffect(focusedItem) {
        delay(HERO_SETTLE_MILLISECONDS)
        settledItem = focusedItem
    }
    val hero = heroFor(settledItem, state)
    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(hero)
        Column(Modifier.fillMaxSize().padding(top = TOP_BAR_SPACE)) {
            Box(Modifier.height(HERO_INFO_HEIGHT), contentAlignment = Alignment.BottomStart) {
                HeroInfo(hero)
            }
            val actions = RowActions(
                onFocusItem = { item -> focusedItem = item },
                onOpenItem = onOpenItem,
                onOpenPerson = onOpenPerson,
            )
            RowsList(state, mediaType, actions, onToggleMyList = viewModel::toggleFeaturedInMyList, onFocusFeatured = { focusedItem = null })
        }
    }
}

// Before any card has focus the featured title fills the hero, with its genres.
private fun heroFor(focused: RailItem?, state: TitlesPageState): HeroTitle? {
    if (focused != null) {
        return HeroTitle(focused)
    }
    val featured = state.featured ?: return null
    return HeroTitle(featured.item, featured.genres)
}

@Composable
private fun RowsList(
    state: TitlesPageState,
    mediaType: MediaType,
    actions: RowActions,
    onToggleMyList: () -> Unit,
    onFocusFeatured: () -> Unit,
) {
    val profileName = ProfileSession.activeProfile.value?.name.orEmpty()
    val rows = state.toRows(pageLabels(mediaType, profileName)).withActorsRow(state.actors.isNotEmpty())
    FocusPivot(0.dp) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 20.dp, bottom = 240.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item(key = "featured") {
                FeaturedActions(state, actions, onToggleMyList, onFocusFeatured)
            }
            homeRows(rows, state.actors, actions)
        }
    }
}

/** Open and My List for the featured title; focus starts here when the page opens. */
@Composable
private fun FeaturedActions(state: TitlesPageState, actions: RowActions, onToggleMyList: () -> Unit, onFocusFeatured: () -> Unit) {
    val featured = state.featured ?: return
    val openButton = remember { FocusRequester() }
    LaunchedEffect(featured.item.id) { openButton.requestFocus() }
    var myListIcon = PhosphorIcons.Bold.Plus
    if (state.featuredInMyList) {
        myListIcon = PhosphorIcons.Bold.Check
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TvSpacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
