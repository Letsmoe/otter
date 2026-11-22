package com.letsmoe.otter.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.tv.ui.home.HomeScreen
import com.letsmoe.otter.tv.ui.home.HomeTab
import com.letsmoe.otter.tv.ui.person.PersonScreen
import com.letsmoe.otter.tv.ui.player.TvPlayerScreen
import com.letsmoe.otter.tv.ui.title.ScenesScreen
import com.letsmoe.otter.tv.ui.title.VideoScreen
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

    /** A YouTube video of a title, full screen. */
    data class Video(val videoKey: String) : TvRoute {
        override val key = "video-$videoKey"
    }

    /** A title's scene stills, full screen, starting at [index]. */
    data class Scenes(val imageUrls: List<String>, val index: Int) : TvRoute {
        override val key = "scenes-$index-${imageUrls.hashCode()}"
    }

    data class Person(val personId: String) : TvRoute {
        override val key = "person-$personId"
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

/** How screens move around: open a route, go back, or return home on one of its tabs. */
private class TvNavigator(
    val homeTab: HomeTab,
    val open: (TvRoute) -> Unit,
    val back: () -> Unit,
    val openHomeTab: (HomeTab) -> Unit,
)

@Composable
private fun TvNavigation() {
    var stack by remember { mutableStateOf(listOf<TvRoute>(TvRoute.Home)) }
    // Shared by home and the title pages' top bar, which returns home on the tab chosen there.
    var homeTab by rememberSaveable { mutableStateOf(HomeTab.MOVIES) }
    // Screens left for a deeper one keep their scroll position, tab and the like for when Back returns.
    val savedStates = rememberSaveableStateHolder()
    val navigator = TvNavigator(
        homeTab = homeTab,
        open = { route -> stack = stack + route },
        back = { stack = stack.dropLast(1) },
        openHomeTab = { tab ->
            homeTab = tab
            stack = listOf(TvRoute.Home)
        },
    )
    BackHandler(enabled = stack.size > 1, onBack = navigator.back)
    val current = stack.last()
    savedStates.SaveableStateProvider(current.key) {
        RouteContent(current, navigator)
    }
}

@Composable
private fun RouteContent(route: TvRoute, navigator: TvNavigator) {
    val openItem = { item: RailItem -> navigator.open(titleRouteOf(item)) }
    val openPerson = { personId: String -> navigator.open(TvRoute.Person(personId)) }
    when (route) {
        TvRoute.Home -> HomeScreen(navigator.homeTab, navigator.openHomeTab, onOpenItem = openItem, onOpenPerson = openPerson)
        is TvRoute.Title -> TitleScreen(route.titleId, route.linkedEpisode, titleNavigation(navigator, openItem, openPerson))
        // The player handles Back itself: it closes its panels and controls first.
        is TvRoute.Player -> TvPlayerScreen(route.mediaId, route.streamId, onBack = navigator.back)
        is TvRoute.Video -> VideoScreen(route.videoKey, onFinished = navigator.back)
        is TvRoute.Scenes -> ScenesScreen(route.imageUrls, route.index)
        is TvRoute.Person -> PersonScreen(route.personId, onOpenItem = openItem, onOpenPerson = openPerson)
    }
}

private fun titleNavigation(navigator: TvNavigator, openItem: (RailItem) -> Unit, openPerson: (String) -> Unit): TitleNavigation {
    return TitleNavigation(
        onPlay = { request -> navigator.open(TvRoute.Player(request.mediaId, request.streamId)) },
        onOpenItem = openItem,
        onOpenPerson = openPerson,
        onPlayVideo = { videoKey -> navigator.open(TvRoute.Video(videoKey)) },
        onOpenScenes = { imageUrls, index -> navigator.open(TvRoute.Scenes(imageUrls, index)) },
        homeTab = navigator.homeTab,
        onOpenHomeTab = navigator.openHomeTab,
    )
}

/** A card opens its title; Continue Watching cards open the series on their episode. */
private fun titleRouteOf(item: RailItem): TvRoute {
    return TvRoute.Title(item.id, LinkedEpisode(seasonNumber = item.seasonNumber, episodeId = item.episodeId))
}
