package com.letsmoe.otter.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.tv.ui.home.HomeScreen
import com.letsmoe.otter.tv.ui.player.TvPlayerScreen
import com.letsmoe.otter.tv.ui.profiles.ProfileGateScreen
import com.letsmoe.otter.tv.ui.title.TitleNavigation
import com.letsmoe.otter.tv.ui.title.TitleScreen
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.title.LinkedEpisode

/** A screen of the TV app; the app keeps a stack of them and Back pops one. */
sealed interface TvRoute {
    val key: String

    data object Home : TvRoute {
        override val key = "home"
    }

    data class Title(val titleId: String, val linkedEpisode: LinkedEpisode) : TvRoute {
        override val key = "title-$titleId"
    }

    data class Player(val mediaId: String, val streamId: String?) : TvRoute {
        override val key = "player-$mediaId"
    }
}

/** The profile gate until someone picks a profile, then the app. */
@Composable
fun TvApp() {
    val activeProfile by ProfileSession.activeProfile.collectAsStateWithLifecycle()
    if (activeProfile == null) {
        ProfileGateScreen()
        return
    }
    TvNavigation()
}

@Composable
private fun TvNavigation() {
    var stack by remember { mutableStateOf(listOf<TvRoute>(TvRoute.Home)) }
    // Screens left for a deeper one keep their scroll position, tab and the like for when Back returns.
    val savedStates = rememberSaveableStateHolder()
    val open = { route: TvRoute -> stack = stack + route }
    val back = { stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1, onBack = back)
    val current = stack.last()
    savedStates.SaveableStateProvider(current.key) {
        RouteContent(current, open, back)
    }
}

@Composable
private fun RouteContent(route: TvRoute, open: (TvRoute) -> Unit, back: () -> Unit) {
    val openItem = { item: RailItem -> open(titleRouteOf(item)) }
    when (route) {
        TvRoute.Home -> HomeScreen(onOpenItem = openItem, onOpenPerson = {})
        is TvRoute.Title -> TitleScreen(
            route.titleId,
            route.linkedEpisode,
            // Person pages come later; until then actors do nothing.
            TitleNavigation(
                onPlay = { request -> open(TvRoute.Player(request.mediaId, request.streamId)) },
                onOpenItem = openItem,
                onOpenPerson = {},
            ),
        )
        // The player handles Back itself: it closes its panels and controls first.
        is TvRoute.Player -> TvPlayerScreen(route.mediaId, route.streamId, onBack = back)
    }
}

/** A card opens its title; Continue Watching cards open the series on their episode. */
private fun titleRouteOf(item: RailItem): TvRoute {
    return TvRoute.Title(item.id, LinkedEpisode(seasonNumber = item.seasonNumber, episodeId = item.episodeId))
}
