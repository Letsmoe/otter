package dev.neoworks.otter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.neoworks.otter.downloads.DownloadsRepository
import dev.neoworks.otter.notifications.NotificationItem
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.together.TogetherSession
import dev.neoworks.otter.ui.components.ToastHost
import dev.neoworks.otter.ui.components.WithoutBackdrop
import dev.neoworks.otter.ui.downloads.DownloadButton
import dev.neoworks.otter.ui.downloads.DownloadButtonStyle
import dev.neoworks.otter.ui.downloads.SeriesDownloadsScreen
import dev.neoworks.otter.ui.navigation.Routes
import dev.neoworks.otter.ui.navigation.Tab
import dev.neoworks.otter.ui.notifications.NotificationsScreen
import dev.neoworks.otter.ui.player.PlayerRequest
import dev.neoworks.otter.ui.player.PlayerScreen
import dev.neoworks.otter.ui.plugins.PluginsScreen
import dev.neoworks.otter.ui.profile.ProfileFlightOverlay
import dev.neoworks.otter.ui.profile.ProfileNavigation
import dev.neoworks.otter.ui.settings.SettingsScreen
import dev.neoworks.otter.ui.title.TitleSlots
import dev.neoworks.otter.ui.together.TogetherButton
import dev.neoworks.otter.ui.together.TogetherChatSheet
import dev.neoworks.otter.ui.together.TogetherSheet
import dev.neoworks.otter.ui.media.RailItem

/** The player, downloads, profile page, settings, plugins and notifications. */
fun NavGraphBuilder.accountRoutes(navController: NavHostController) {
    composable(Routes.SERIES_DOWNLOADS, arguments = listOf(navArgument("seriesId") { type = NavType.StringType })) { entry ->
        WithoutBackdrop {
            SeriesDownloadsScreen(
                seriesId = entry.arguments?.getString("seriesId").orEmpty(),
                onPlay = { item -> navController.navigate(Routes.player(item.mediaId, downloadId = item.id)) },
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
        ),
    ) { entry ->
        val request = PlayerRequest(
            mediaId = entry.arguments?.getString("mediaId").orEmpty(),
            streamId = entry.arguments?.getString("streamId"),
            downloadId = entry.arguments?.getString("downloadId"),
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
        togetherButton = { TogetherButton(glass = true) },
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
    val remoteMedia by TogetherSession.remoteMedia.collectAsStateWithLifecycle()
    LaunchedEffect(remoteMedia) {
        val started = remoteMedia ?: return@LaunchedEffect
        if (navController.currentDestination?.route != Routes.PLAYER) {
            navController.navigate(Routes.player(started.mediaId))
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
