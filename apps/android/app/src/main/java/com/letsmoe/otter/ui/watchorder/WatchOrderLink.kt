package com.letsmoe.otter.ui.watchorder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letsmoe.otter.ui.LoadState
import com.letsmoe.otter.ui.components.pressable

/** On a movie's page: its franchise's artwork, which opens the watch-order guide. Draws nothing until the guide is loaded. */
@Composable
fun WatchOrderLink(collectionId: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = viewModel(key = "watch-order-link-$collectionId") { WatchOrderViewModel(collectionId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val loaded = state as? LoadState.Loaded ?: return
    if (loaded.value.parts.size < 2) {
        return
    }
    Box(modifier.pressable(onClick = onOpen)) {
        WatchOrderArtwork(loaded.value, Modifier.fillMaxWidth())
    }
}
