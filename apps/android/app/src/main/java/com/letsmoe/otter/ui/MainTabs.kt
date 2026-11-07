package com.letsmoe.otter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.letsmoe.otter.graphql.type.MediaType
import com.letsmoe.otter.ui.components.WithoutBackdrop
import com.letsmoe.otter.ui.title.fetchUpNextEpisodeId
import kotlinx.coroutines.launch
import com.letsmoe.otter.ui.downloads.DownloadsScreen
import com.letsmoe.otter.ui.home.HomeScreen
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.navigation.Routes
import com.letsmoe.otter.ui.navigation.play
import com.letsmoe.otter.ui.player.PlayerRequest
import com.letsmoe.otter.ui.television.TelevisionButton
import com.letsmoe.otter.ui.navigation.Tab
import com.letsmoe.otter.ui.profile.ProfileScreen
import com.letsmoe.otter.ui.together.TogetherButton
import com.letsmoe.otter.ui.watchlist.WatchlistScreen

/**
 * The bottom bar's destinations side by side; swiping moves between them like the web app's
 * TabPager. Detail pages open on top of the whole pager.
 */
@Composable
fun MainTabs(pagerState: PagerState, navController: NavHostController, openTab: (Tab) -> Unit, onOpenItem: (RailItem) -> Unit) {
    // Neighbours stay composed so a swipe shows a ready page, not a loading one.
    HorizontalPager(pagerState, beyondViewportPageCount = 1, key = { page -> Tab.entries[page].name }) { page ->
        Box(Modifier.nestedScroll(keepSidewaysScroll)) {
            TabPage(Tab.entries[page], navController, openTab, onOpenItem)
        }
    }
}

// A poster row that reaches its end hands the rest of the swipe to its parent, which would flip
// the tab mid-browse. Swallow that leftover so only swipes outside rows change tabs.
private val keepSidewaysScroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = Offset(available.x, 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity(available.x, 0f)
}

@Composable
private fun TabPage(tab: Tab, navController: NavHostController, openTab: (Tab) -> Unit, onOpenItem: (RailItem) -> Unit) {
    val scope = rememberCoroutineScope()
    when (tab) {
        Tab.HOME -> HomeScreen(
            onOpenItem = onOpenItem,
            onPlayItem = { item -> scope.launch { playItem(navController, item, onOpenItem) } },
            onOpenPerson = { personId -> navController.navigate(Routes.person(personId)) },
            onSearch = { navController.navigate(Routes.SEARCH) },
        )
        Tab.MY_LIST -> WatchlistScreen(onOpenItem = onOpenItem, onSearch = { navController.navigate(Routes.SEARCH) })
        Tab.DOWNLOADS -> WithoutBackdrop {
            DownloadsScreen(
                onPlay = { item -> navController.play(PlayerRequest(item.mediaId, downloadId = item.id)) },
                onOpenSeries = { seriesId -> navController.navigate(Routes.seriesDownloads(seriesId)) },
                onSearch = { navController.navigate(Routes.SEARCH) },
            )
        }
        Tab.PROFILE -> WithoutBackdrop { ProfileScreen(profileNavigation(navController, openTab, onOpenItem)) }
    }
}

// Play on a card starts what the title page's Play would: the movie, or the series' up-next episode.
// A series without one (or a failed lookup) opens its page instead.
private suspend fun playItem(navController: NavHostController, item: RailItem, onOpenItem: (RailItem) -> Unit) {
    if (item.mediaType != MediaType.SERIES) {
        navController.play(PlayerRequest(item.id))
        return
    }
    val episodeId = fetchUpNextEpisodeId(item.id)
    if (episodeId == null) {
        onOpenItem(item)
        return
    }
    navController.play(PlayerRequest(episodeId))
}
