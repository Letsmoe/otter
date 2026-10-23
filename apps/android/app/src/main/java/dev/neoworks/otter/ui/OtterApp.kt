package dev.neoworks.otter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.ui.components.LocalGlassBackdrop
import dev.neoworks.otter.ui.components.LocalSheetHost
import dev.neoworks.otter.ui.components.SheetHostOutlet
import dev.neoworks.otter.ui.preview.TitlePreviewSheet
import dev.neoworks.otter.ui.components.SheetHostState
import dev.neoworks.otter.graphql.type.MediaType
import dev.neoworks.otter.ui.browse.BrowseScreen
import dev.neoworks.otter.ui.browse.LibraryScreen
import dev.neoworks.otter.ui.history.HistoryScreen
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.person.PersonScreen
import dev.neoworks.otter.ui.search.SearchScreen
import dev.neoworks.otter.ui.navigation.BottomBar
import dev.neoworks.otter.ui.navigation.Routes
import dev.neoworks.otter.ui.navigation.Tab
import dev.neoworks.otter.ui.navigation.tabForRoute
import dev.neoworks.otter.ui.profiles.ProfileGateScreen
import dev.neoworks.otter.ui.components.ToastHost
import dev.neoworks.otter.ui.theme.OtterTheme
import dev.neoworks.otter.ui.title.TitleScreen

@Composable
fun OtterApp() {
    OtterTheme {
        // Sets the default content color; bare Text would otherwise be black.
        Surface(color = MaterialTheme.colorScheme.background) {
            val activeProfile by ProfileSession.activeProfile.collectAsStateWithLifecycle()
            if (activeProfile == null) {
                ProfileGateScreen()
                ToastHost()
            } else {
                AppShell()
            }
        }
    }
}

/** The pages with the glass tab bar floating over them. */
@Composable
private fun AppShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val glassBackdrop = rememberHazeState()
    val sheetHost = remember { SheetHostState() }
    val pagerState = rememberPagerState { Tab.entries.size }
    val scope = rememberCoroutineScope()
    val openTab: (Tab) -> Unit = { tab -> navController.showTab(tab, pagerState, scope) }
    CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop, LocalSheetHost provides sheetHost) {
        Box(Modifier.fillMaxSize()) {
            OtterNavHost(navController, pagerState, openTab, Modifier.fillMaxSize().then(blurSourceFor(currentRoute, glassBackdrop)))
            AppServices(navController)
            if (currentRoute != Routes.PLAYER) {
                BottomBar(
                    activeTab = activeTab(currentRoute, pagerState),
                    onSelect = openTab,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            TitlePreviewSheet(
                onOpenItem = { item -> navController.openItem(item) },
                onOpenPerson = { personId -> navController.navigate(Routes.person(personId)) },
            )
            SheetHostOutlet(sheetHost)
            AppOverlays()
        }
    }
}

// A WebView recorded into a blur source plays audio but shows no picture, so title pages
// (with their YouTube trailer) aren't blurred behind the tab bar; it falls back to its tint.
private fun blurSourceFor(route: String?, glassBackdrop: HazeState): Modifier {
    if (route == Routes.TITLE) {
        return Modifier
    }
    return Modifier.hazeSource(glassBackdrop)
}

private val pageSlide = tween<IntOffset>(320, easing = FastOutSlowInEasing)
private val pageFade = tween<Float>(320, easing = FastOutSlowInEasing)

// Detail pages sit on top of the tab pager: a tab tap goes back to the pager, then slides to the tab.
private fun NavHostController.showTab(tab: Tab, pagerState: PagerState, scope: CoroutineScope) {
    if (currentDestination?.route != Routes.TABS) {
        popBackStack(Routes.TABS, inclusive = false)
    }
    scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
}

// On the pager the tab follows the swipe; elsewhere it's the tab the page belongs to.
private fun activeTab(route: String?, pagerState: PagerState): Tab? {
    if (route == Routes.TABS) {
        return Tab.entries[pagerState.targetPage]
    }
    return tabForRoute(route)
}

private fun NavHostController.openItem(item: RailItem) {
    navigate(Routes.title(item.id, item.seasonNumber, item.episodeId))
}

@Composable
private fun OtterNavHost(navController: NavHostController, pagerState: PagerState, openTab: (Tab) -> Unit, modifier: Modifier) {
    NavHost(
        navController = navController,
        startDestination = Routes.TABS,
        modifier = modifier,
        enterTransition = { slideInHorizontally(pageSlide) { width -> width } },
        exitTransition = { slideOutHorizontally(pageSlide) { width -> -width / 4 } + fadeOut(pageFade, targetAlpha = 0.6f) },
        // Back (and the predictive back gesture) slides the page off to the right instead of the default shrink.
        popEnterTransition = { slideInHorizontally(pageSlide) { width -> -width / 4 } + fadeIn(pageFade, initialAlpha = 0.6f) },
        popExitTransition = { slideOutHorizontally(pageSlide) { width -> width } },
        // The swipe-back gesture has its own pair, which defaults to shrinking the page into the centre.
        predictivePopEnterTransition = { slideInHorizontally(pageSlide) { width -> -width / 4 } + fadeIn(pageFade, initialAlpha = 0.6f) },
        predictivePopExitTransition = { slideOutHorizontally(pageSlide) { width -> width } },
    ) {
        composable(Routes.TABS) {
            MainTabs(pagerState, navController, openTab, onOpenItem = { item -> navController.openItem(item) })
        }
        accountRoutes(navController)
        composable(Routes.SEARCH) {
            SearchScreen(onOpenItem = { item -> navController.openItem(item) }, onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onOpenItem = { item -> navController.openItem(item) }, onBack = { navController.popBackStack() })
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenItem = { item -> navController.openItem(item) },
                onSeeAllMovies = { navController.navigate(Routes.browse("movie")) },
                onSeeAllSeries = { navController.navigate(Routes.browse("series")) },
            )
        }
        composable(Routes.BROWSE, arguments = listOf(navArgument("kind") { type = NavType.StringType })) { entry ->
            val isSeries = entry.arguments?.getString("kind") == "series"
            BrowseScreen(
                mediaType = if (isSeries) MediaType.SERIES else MediaType.MOVIE,
                title = if (isSeries) "TV Shows" else "Movies",
                onOpenItem = { item -> navController.openItem(item) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PERSON, arguments = listOf(navArgument("personId") { type = NavType.StringType })) { entry ->
            PersonScreen(
                personId = entry.arguments?.getString("personId").orEmpty(),
                onOpenItem = { item -> navController.openItem(item) },
                onOpenPerson = { personId -> navController.navigate(Routes.person(personId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Routes.TITLE,
            arguments = listOf(
                navArgument("titleId") { type = NavType.StringType },
                navArgument("season") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("episodeId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            TitleScreen(
                titleId = entry.arguments?.getString("titleId").orEmpty(),
                linkedSeasonNumber = entry.arguments?.getString("season")?.toIntOrNull(),
                linkedEpisodeId = entry.arguments?.getString("episodeId"),
                onPlay = { request -> navController.navigate(Routes.player(request.mediaId, request.streamId)) },
                slots = titleSlots(openTab),
                onBack = { navController.popBackStack() },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onOpenItem = { item -> navController.openItem(item) },
                onOpenPerson = { personId -> navController.navigate(Routes.person(personId)) },
            )
        }
    }
}
