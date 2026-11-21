package com.letsmoe.otter.tv

import android.app.Application
import com.letsmoe.otter.api.ServerSettings
import com.letsmoe.otter.backend.EmbeddedBackend
import com.letsmoe.otter.network.ConnectivityStore
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.reporting.ErrorReporting
import com.letsmoe.otter.settings.PlaybackSettings

/** Starts the on-device backend and restores the active profile once per app process, before any activity. */
class TvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // First, so crashes while starting are reported too.
        ErrorReporting.initialize(this)
        EmbeddedBackend(this).start()
        ServerSettings.initialize(this)
        PlaybackSettings.initialize(this)
        ProfileSession.initialize(this)
        ConnectivityStore.initialize(this)
    }
}
