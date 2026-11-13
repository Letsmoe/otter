package com.letsmoe.otter.links

import android.content.Intent
import android.net.Uri
import com.letsmoe.otter.notifications.TITLE_LINK_PREFIX
import com.letsmoe.otter.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Links that open a page from outside the app (a tapped notification, a shared link), waiting
 * for the app's navigation to open them.
 */
object IncomingLinks {

    private val mutablePendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = mutablePendingRoute

    /** Takes the page out of [intent]; false when it isn't a link to one. */
    fun handle(intent: Intent?): Boolean {
        val link = intent?.dataString ?: return false
        val route = routeOf(link) ?: return false
        mutablePendingRoute.value = route
        return true
    }

    fun consumed() {
        mutablePendingRoute.value = null
    }

    private fun routeOf(link: String): String? {
        if (link.startsWith(TITLE_LINK_PREFIX)) {
            return Uri.parse(link).getQueryParameter("route")
        }
        val target = parseShareLink(link) ?: return null
        return routeFor(target)
    }
}

/** The app page a shared link opens. */
fun routeFor(target: SharedTarget): String {
    return when (target) {
        is SharedTarget.Movie -> Routes.title("movie:${target.tmdbId}")
        is SharedTarget.Series -> Routes.title("series:${target.tmdbId}")
        is SharedTarget.Episode -> Routes.titleAtEpisodeNumber("series:${target.seriesTmdbId}", target.seasonNumber, target.episodeNumber)
    }
}
