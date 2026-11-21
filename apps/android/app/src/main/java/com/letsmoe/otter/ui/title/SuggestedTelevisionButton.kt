package com.letsmoe.otter.ui.title

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.cast.SuggestedTelevision
import com.letsmoe.otter.cast.TelevisionConnection
import com.letsmoe.otter.ui.components.PillButton

/**
 * "Play on <TV>" under the page's Play button when a TV that Otter cast to before is on the
 * Wi-Fi and none is connected yet. It connects that TV and plays like the Play button does.
 */
@Composable
fun SuggestedTelevisionButton(enabled: Boolean, onPlay: () -> Unit) {
    val context = LocalContext.current
    val connected by TelevisionConnection.target.collectAsStateWithLifecycle()
    val suggested by SuggestedTelevision.television.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { SuggestedTelevision.refresh(context) }
    val television = suggested
    if (connected != null || television == null) {
        return
    }
    PillButton("Play on ${television.name}", Modifier.fillMaxWidth(), enabled, filled = false) {
        TelevisionConnection.connectDlna(television)
        onPlay()
    }
}
