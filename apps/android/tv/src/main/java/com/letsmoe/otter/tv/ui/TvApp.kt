package com.letsmoe.otter.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.tv.home.HomeScreen
import com.letsmoe.otter.tv.ui.profiles.ProfileGateScreen

/** The profile gate until someone picks a profile, then the app. */
@Composable
fun TvApp() {
    val activeProfile by ProfileSession.activeProfile.collectAsStateWithLifecycle()
    if (activeProfile == null) {
        ProfileGateScreen()
        return
    }
    HomeScreen()
}
