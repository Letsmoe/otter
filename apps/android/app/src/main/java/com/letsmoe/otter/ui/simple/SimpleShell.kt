package com.letsmoe.otter.ui.simple

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.letsmoe.otter.ui.ShellReloads
import com.letsmoe.otter.ui.components.LocalGlassBackdrop
import com.letsmoe.otter.ui.components.ToastHost
import com.letsmoe.otter.ui.navigation.Routes
import com.letsmoe.otter.ui.playerRoute

private const val HOME = "simple-home"
private const val CONTINUE = "simple-continue"
private const val MY_LIST = "simple-list"
private const val SEARCH = "simple-search"

/**
 * The whole app of a profile in simple mode: Continue, My List and Search, plain pages without
 * gestures or menus, and the player. Nothing else is reachable from here.
 */
@Composable
fun SimpleShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val backdrop = rememberHazeState()
    LaunchedEffect(backStackEntry?.destination?.route) {
        ShellReloads.setPlayerOpen(backStackEntry?.destination?.route == Routes.PLAYER)
    }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(backdrop)) {
                SimpleNavHost(navController)
            }
            ToastHost()
        }
    }
}

@Composable
private fun SimpleNavHost(navController: NavHostController) {
    val openPlayer: (String) -> Unit = { mediaId -> navController.navigate(Routes.player(mediaId)) }
    val goBack: () -> Unit = { navController.popBackStack() }
    NavHost(navController, startDestination = HOME) {
        composable(HOME) {
            SimpleHomeScreen(
                onContinue = { navController.navigate(CONTINUE) },
                onMyList = { navController.navigate(MY_LIST) },
                onSearch = { navController.navigate(SEARCH) },
            )
        }
        composable(CONTINUE) { SimpleContinueScreen(goBack, openPlayer) }
        composable(MY_LIST) { SimpleListScreen(goBack, openPlayer) }
        composable(SEARCH) { SimpleSearchScreen(goBack, openPlayer) }
        playerRoute(navController)
    }
}
