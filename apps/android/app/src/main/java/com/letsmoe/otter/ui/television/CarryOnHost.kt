package com.letsmoe.otter.ui.television

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.toasts.Toasts
import com.letsmoe.otter.ui.player.PlayerRequest
import com.letsmoe.otter.ui.player.TelevisionPlayback

/**
 * Opens the player on the phone for a title that left the TV because the phone left the Wi-Fi. The
 * flow is only read while the app is in front, so a title that was carried over while the phone was
 * locked opens as soon as the app is.
 */
@Composable
fun CarryOnHost(onContinueOnPhone: (PlayerRequest) -> Unit) {
    val request by TelevisionPlayback.carryOn.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        val carriedOver = request ?: return@LaunchedEffect
        TelevisionPlayback.carryOnHandled()
        Toasts.push("You left the Wi-Fi: carrying on on your phone")
        onContinueOnPhone(carriedOver)
    }
}
