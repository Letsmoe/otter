package dev.neoworks.otter.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.BookmarkSimple as BookmarkSimpleFill
import com.adamglin.phosphoricons.fill.DownloadSimple as DownloadSimpleFill
import com.adamglin.phosphoricons.fill.House as HouseFill
import com.adamglin.phosphoricons.regular.BookmarkSimple
import com.adamglin.phosphoricons.regular.DownloadSimple
import com.adamglin.phosphoricons.regular.House
import com.adamglin.phosphoricons.regular.UserCircle

object Routes {
    /** The four bottom-bar tabs in one swipeable pager; the tab routes below only name them. */
    const val TABS = "tabs"
    const val HOME = "home"
    const val MY_LIST = "my-list"
    const val DOWNLOADS = "downloads"
    const val PROFILE = "profile"
    const val TITLE = "title/{titleId}?season={season}&episode={episodeId}"
    const val SEARCH = "search"
    const val BROWSE = "browse/{kind}"
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val PERSON = "person/{personId}"
    const val PLAYER = "player/{mediaId}?streamId={streamId}&downloadId={downloadId}"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val PLUGINS = "plugins"
    const val SERIES_DOWNLOADS = "downloads/{seriesId}"

    /** A series page can open on one season and episode, e.g. from Continue Watching. */
    fun title(titleId: String, seasonNumber: Int? = null, episodeId: String? = null): String {
        var route = "title/${android.net.Uri.encode(titleId)}"
        if (seasonNumber != null && episodeId != null) {
            route += "?season=$seasonNumber&episode=${android.net.Uri.encode(episodeId)}"
        }
        return route
    }

    fun browse(kind: String) = "browse/$kind"
    fun person(personId: String) = "person/${android.net.Uri.encode(personId)}"

    /** Plays a movie or episode: on one chosen link, from a finished download, or on its best link. */
    fun player(mediaId: String, streamId: String? = null, downloadId: String? = null): String {
        var route = "player/${android.net.Uri.encode(mediaId)}?"
        if (streamId != null) {
            route += "streamId=${android.net.Uri.encode(streamId)}&"
        }
        if (downloadId != null) {
            route += "downloadId=${android.net.Uri.encode(downloadId)}&"
        }
        return route.trimEnd('?', '&')
    }

    fun seriesDownloads(seriesId: String) = "downloads/${android.net.Uri.encode(seriesId)}"
}

/** Main destinations in the bottom bar; mirrors apps/mobile/src/lib/tabs.ts. */
enum class Tab(val route: String, val label: String, val icon: ImageVector, val activeIcon: ImageVector) {
    HOME(Routes.HOME, "Home", PhosphorIcons.Regular.House, PhosphorIcons.Fill.HouseFill),
    MY_LIST(Routes.MY_LIST, "My List", PhosphorIcons.Regular.BookmarkSimple, PhosphorIcons.Fill.BookmarkSimpleFill),
    DOWNLOADS(Routes.DOWNLOADS, "Downloads", PhosphorIcons.Regular.DownloadSimple, PhosphorIcons.Fill.DownloadSimpleFill),

    // Shows the active profile's avatar instead of the icon.
    PROFILE(Routes.PROFILE, "Profile", PhosphorIcons.Regular.UserCircle, PhosphorIcons.Regular.UserCircle),
}

/** Further routes that belong to a tab, like `matches` in apps/mobile/src/lib/tabs.ts. */
private val routesOfProfileTab = listOf(Routes.PROFILE, Routes.HISTORY, Routes.SETTINGS, Routes.PLUGINS, Routes.NOTIFICATIONS)

fun tabForRoute(route: String?): Tab? {
    if (route in routesOfProfileTab) {
        return Tab.PROFILE
    }
    if (route == Routes.SERIES_DOWNLOADS) {
        return Tab.DOWNLOADS
    }
    return Tab.entries.firstOrNull { tab -> tab.route == route }
}
