package com.letsmoe.otter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.letsmoe.otter.downloads.DownloadsRepository
import com.letsmoe.otter.notifications.NotificationItem
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.together.TogetherSession
import com.letsmoe.otter.ui.components.ToastHost
import com.letsmoe.otter.ui.components.WithoutBackdrop
import com.letsmoe.otter.ui.downloads.DownloadButton
import com.letsmoe.otter.ui.downloads.DownloadButtonStyle
import com.letsmoe.otter.ui.downloads.SeriesDownloadsScreen
import com.letsmoe.otter.ui.navigation.Routes
import com.letsmoe.otter.ui.navigation.play
import com.letsmoe.otter.ui.player.TelevisionPlayback
import com.letsmoe.otter.ui.navigation.Tab
import com.letsmoe.otter.ui.notifications.NotificationsScreen
import com.letsmoe.otter.ui.player.PlayerRequest
import com.letsmoe.otter.ui.player.PlayerScreen
import com.letsmoe.otter.ui.plugins.PluginsScreen
import com.letsmoe.otter.ui.profile.ProfileFlightOverlay
import com.letsmoe.otter.ui.profile.ProfileNavigation
import com.letsmoe.otter.ui.settings.SettingsScreen
import com.letsmoe.otter.ui.title.TitleSlots
import com.letsmoe.otter.links.IncomingLinks
import com.letsmoe.otter.ui.television.TelevisionButton
import com.letsmoe.otter.ui.together.TogetherButton
import com.letsmoe.otter.ui.together.TogetherChatSheet
import com.letsmoe.otter.ui.together.TogetherSheet
import com.letsmoe.otter.ui.media.RailItem

/** The player, downloads, profile page, settings, plugins and notifications. */
fun NavGraphBuilder.accountRoutes(navController: NavHostController) {
    composable(Routes.SERIES_DOWNLOADS, arguments = listOf(navArgument("seriesId") { type = NavType.StringType })) { entry ->
        WithoutBackdrop {
            SeriesDownloadsScreen(
                seriesId = entry.arguments?.getString("seriesId").orEmpty(),
                onPlay = { item -> navController.play(PlayerRequest(item.mediaId, downloadId = item.id)) },
                onBack = { navController.popBackStack() },
            )
        }
    }
    composable(Routes.NOTIFICATIONS) {
        WithoutBackdrop {
            NotificationsScreen(
                onOpenNotification = { notification -> navController.openNotification(notification) },
                onBack = { navController.popBackStack() },
            )
        }
    }
    composable(Routes.SETTINGS) {
        WithoutBackdrop { SettingsScreen(onBack = { navController.popBackStack() }, onOpenPlugins = { navController.navigate(Routes.PLUGINS) }) }
    }
    composable(Routes.PLUGINS) {
        WithoutBackdrop { PluginsScreen(onBack = { navController.popBackStack() }) }
    }
    composable(
        Routes.PLAYER,
        arguments = listOf(
            navArgument("mediaId") { type = NavType.StringType },
            navArgument("streamId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("downloadId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("start") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { entry ->
        val request = PlayerRequest(
            mediaId = entry.arguments?.getString("mediaId").orEmpty(),
            streamId = entry.arguments?.getString("streamId"),
            downloadId = entry.arguments?.getString("downloadId"),
            startSeconds = entry.arguments?.getString("start")?.toDoubleOrNull(),
        )
        PlayerScreen(request, onBack = { navController.popBackStack() })
    }
}

fun profileNavigation(navController: NavHostController, openTab: (Tab) -> Unit, onOpenItem: (RailItem) -> Unit): ProfileNavigation {
    return ProfileNavigation(
        onOpenItem = onOpenItem,
        onOpenNotification = { notification -> navController.openNotification(notification) },
        onSearch = { navController.navigate(Routes.SEARCH) },
        onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
        onDownloads = { openTab(Tab.DOWNLOADS) },
        onHistory = { navController.navigate(Routes.HISTORY) },
        onPlugins = { navController.navigate(Routes.PLUGINS) },
        onSettings = { navController.navigate(Routes.SETTINGS) },
    )
}

private fun NavHostController.openNotification(notification: NotificationItem) {
    navigate(Routes.title(notification.titleId, notification.seasonNumber, notification.episodeId))
}

/** What the title pages plug in: the Watch Together button and the download buttons. */
fun titleSlots(openTab: (Tab) -> Unit): TitleSlots {
    val openDownloads = { openTab(Tab.DOWNLOADS) }
    return TitleSlots(
        headerButtons = {
            TelevisionButton(glass = true)
            TogetherButton(glass = true)
        },
        downloadButton = { mediaId, title -> DownloadButton(mediaId, title, openDownloads) },
        episodeDownloadButton = { mediaId, label -> DownloadButton(mediaId, label, openDownloads, style = DownloadButtonStyle.ICON) },
    )
}

/** Keeps the account-wide stores running while a profile is signed in, and opens what the room plays. */
@Composable
fun AppServices(navController: NavHostController) {
    val activeProfile by ProfileSession.activeProfile.collectAsStateWithLifecycle()
    LaunchedEffect(activeProfile?.id) { DownloadsRepository.follow(activeProfile?.id) }
    LaunchedEffect(Unit) { TogetherSession.start() }
    val linkedRoute by IncomingLinks.pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(linkedRoute) {
        val route = linkedRoute ?: return@LaunchedEffect
        IncomingLinks.consumed()
        navController.navigate(route)
    }
    val remoteMedia by TogetherSession.remoteMedia.collectAsStateWithLifecycle()
    LaunchedEffect(remoteMedia) {
        val started = remoteMedia ?: return@LaunchedEffect
        // A title already playing on the TV follows the room by itself.
        if (TelevisionPlayback.player.value != null) {
            return@LaunchedEffect
        }
        if (navController.currentDestination?.route != Routes.PLAYER) {
            navController.play(PlayerRequest(started.mediaId))
        }
    }
}

/** Toasts, the Watch Together sheet and the profile flight over everything. */
@Composable
fun AppOverlays() {
    ToastHost()
    TogetherSheet()
    TogetherChatSheet()
    ProfileFlightOverlay()
}
