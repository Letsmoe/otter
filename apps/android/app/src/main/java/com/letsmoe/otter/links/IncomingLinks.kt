package com.letsmoe.otter.links

import android.content.Intent
import android.net.Uri
import com.letsmoe.otter.notifications.TITLE_LINK_PREFIX
import com.letsmoe.otter.ui.navigation.Routes
import com.letsmoe.otter.ui.player.PlayerRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What a tap on a title of the home screen widget sends to the app, with the title's id as the extra. */
const val PLAY_FROM_WIDGET_ACTION = "com.letsmoe.otter.PLAY_FROM_WIDGET"
const val PLAY_FROM_WIDGET_MEDIA_ID = "mediaId"

/**
 * Links that open a page from outside the app (a tapped notification, a shared link, the widget), waiting
 * for the app's navigation to open them.
 */
object IncomingLinks {

    private val mutablePendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = mutablePendingRoute

    private val mutablePendingPlay = MutableStateFlow<PlayerRequest?>(null)
    /** A title to start playing, from the home screen widget. */
    val pendingPlay: StateFlow<PlayerRequest?> = mutablePendingPlay

    /** Takes the page out of [intent]; false when it isn't a link to one. */
    fun handle(intent: Intent?): Boolean {
        if (handlePlayFromWidget(intent)) {
            return true
        }
        val link = intent?.dataString ?: return false
        val route = routeOf(link) ?: return false
        mutablePendingRoute.value = route
        return true
    }

    fun consumed() {
        mutablePendingRoute.value = null
    }

    fun playStarted() {
        mutablePendingPlay.value = null
    }

    private fun handlePlayFromWidget(intent: Intent?): Boolean {
        if (intent?.action != PLAY_FROM_WIDGET_ACTION) {
            return false
        }
        val mediaId = intent.getStringExtra(PLAY_FROM_WIDGET_MEDIA_ID) ?: return false
        mutablePendingPlay.value = PlayerRequest(mediaId)
        return true
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
