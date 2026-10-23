package dev.neoworks.otter.cast

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent

private val chromecastSelector: MediaRouteSelector = MediaRouteSelector.Builder()
    .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
    .build()

/** Whether a Chromecast is on the network, kept current while shown. */
@Composable
fun rememberChromecastAvailable(): Boolean {
    val context = LocalContext.current
    var available by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val router = MediaRouter.getInstance(context)
        val update = { available = router.routes.any { route -> !route.isDefaultOrBluetooth && route.matchesSelector(chromecastSelector) } }
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = update()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = update()
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = update()
        }
        router.addCallback(chromecastSelector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        update()
        onDispose { router.removeCallback(callback) }
    }
    return available
}

/** The system Chromecast picker; picking one hands playback to it (CastPlayer). */
fun showChromecastPicker(context: Context) {
    val dialog = MediaRouteChooserDialog(context, androidx.media3.cast.R.style.AppThemeDialog)
    dialog.routeSelector = chromecastSelector
    dialog.show()
}
