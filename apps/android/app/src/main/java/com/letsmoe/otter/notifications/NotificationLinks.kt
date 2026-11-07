package com.letsmoe.otter.notifications

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A tapped phone notification's title page, waiting for the app's navigation to open it. */
object NotificationLinks {

    private val mutablePendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = mutablePendingRoute

    /** Takes the route out of a notification's tap intent; false when [intent] isn't one. */
    fun handle(intent: Intent?): Boolean {
        val link = intent?.dataString ?: return false
        if (!link.startsWith(TITLE_LINK_PREFIX)) {
            return false
        }
        val route = Uri.parse(link).getQueryParameter("route") ?: return false
        mutablePendingRoute.value = route
        return true
    }

    fun consumed() {
        mutablePendingRoute.value = null
    }
}
