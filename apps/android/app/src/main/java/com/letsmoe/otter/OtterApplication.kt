package com.letsmoe.otter

import android.app.Application
import com.letsmoe.otter.api.ServerSettings
import com.letsmoe.otter.backend.EmbeddedBackend
import com.letsmoe.otter.cast.CastSetup
import com.letsmoe.otter.cast.LocalNetworkRouting
import com.letsmoe.otter.cast.TelevisionConnection
import com.letsmoe.otter.network.ConnectivityStore
import com.letsmoe.otter.notifications.NotificationCheckWorker
import com.letsmoe.otter.ui.player.TelevisionPlayback
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.reporting.ErrorReporting
import com.letsmoe.otter.settings.PlaybackSettings
import com.letsmoe.otter.updates.UpdateSettings
import com.letsmoe.otter.vpn.VpnStatusStore

/** Starts the on-device backend and restores the active profile once per app process, before any activity. */
class OtterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // First, so crashes while starting are reported too.
        ErrorReporting.initialize(this)
        EmbeddedBackend(this).start()
        ServerSettings.initialize(this)
        PlaybackSettings.initialize(this)
        UpdateSettings.initialize(this)
        ProfileSession.initialize(this)
        LocalNetworkRouting.initialize(this)
        VpnStatusStore.initialize(this)
        ConnectivityStore.initialize(this)
        CastSetup.initialize(this)
        TelevisionConnection.initialize(this)
        TelevisionPlayback.initialize(this)
        NotificationCheckWorker.schedule(this)
    }
}
