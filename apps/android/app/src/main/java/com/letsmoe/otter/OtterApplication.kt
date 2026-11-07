package com.letsmoe.otter

import android.app.Application
import com.letsmoe.otter.api.ServerSettings
import com.letsmoe.otter.backend.EmbeddedBackend
import com.letsmoe.otter.cast.CastSetup
import com.letsmoe.otter.cast.LocalNetworkRouting
import com.letsmoe.otter.cast.TelevisionConnection
import com.letsmoe.otter.ui.player.TelevisionPlayback
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.settings.PlaybackSettings

/** Starts the on-device backend and restores the active profile once per app process, before any activity. */
class OtterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        EmbeddedBackend(this).start()
        ServerSettings.initialize(this)
        PlaybackSettings.initialize(this)
        ProfileSession.initialize(this)
        LocalNetworkRouting.initialize(this)
        CastSetup.initialize(this)
        TelevisionConnection.initialize(this)
        TelevisionPlayback.initialize(this)
    }
}
