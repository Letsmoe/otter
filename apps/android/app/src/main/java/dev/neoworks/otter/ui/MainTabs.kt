package dev.neoworks.otter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.neoworks.otter.ui.components.WithoutBackdrop
import dev.neoworks.otter.ui.downloads.DownloadsScreen
import dev.neoworks.otter.ui.home.HomeScreen
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.navigation.Routes
import dev.neoworks.otter.ui.navigation.Tab
import dev.neoworks.otter.ui.profile.ProfileScreen
import dev.neoworks.otter.ui.together.TogetherButton
import dev.neoworks.otter.ui.watchlist.WatchlistScreen

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
    when (tab) {
        Tab.HOME -> HomeScreen(
            onOpenItem = onOpenItem,
            onOpenPerson = { personId -> navController.navigate(Routes.person(personId)) },
            onSearch = { navController.navigate(Routes.SEARCH) },
            headerActions = { TogetherButton(iconSize = 26.dp) },
        )
        Tab.MY_LIST -> WatchlistScreen(onOpenItem = onOpenItem)
        Tab.DOWNLOADS -> WithoutBackdrop {
            DownloadsScreen(
                onPlay = { item -> navController.navigate(Routes.player(item.mediaId, downloadId = item.id)) },
                onOpenSeries = { seriesId -> navController.navigate(Routes.seriesDownloads(seriesId)) },
                onSearch = { navController.navigate(Routes.SEARCH) },
            )
        }
        Tab.PROFILE -> WithoutBackdrop { ProfileScreen(profileNavigation(navController, openTab, onOpenItem)) }
    }
}
