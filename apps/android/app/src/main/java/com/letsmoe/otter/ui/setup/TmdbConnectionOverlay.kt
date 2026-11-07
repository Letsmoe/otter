package com.letsmoe.otter.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letsmoe.otter.ui.components.PillButton

/** Over everything while TMDB doesn't answer; downloads still play, so it can be dismissed. */
@Composable
fun TmdbConnectionOverlay(connection: TmdbConnectionViewModel = viewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(connection, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { connection.watch() }
    }
    if (!connection.visible) {
        return
    }
    SetupCard {
        SetupHeading(
            "Can't reach TMDB",
            "Titles, posters and search come from The Movie Database, and it isn't answering. " +
                "A VPN or network filter may be blocking it: try another VPN server or check your connection. Downloads still play.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(retryLabel(connection.checking), Modifier.fillMaxWidth(), enabled = !connection.checking, filled = true, onClick = connection::retry)
            PillButton("Continue anyway", Modifier.fillMaxWidth(), enabled = true, filled = false, onClick = connection::dismiss)
        }
    }
}

private fun retryLabel(checking: Boolean): String {
    if (checking) {
        return "Checking…"
    }
    return "Try again"
}
