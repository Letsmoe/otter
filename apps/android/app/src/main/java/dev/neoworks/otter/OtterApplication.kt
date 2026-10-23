package dev.neoworks.otter

import android.app.Application
import dev.neoworks.otter.api.ServerSettings
import dev.neoworks.otter.backend.EmbeddedBackend
import dev.neoworks.otter.cast.CastSetup
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.settings.PlaybackSettings

/** Starts the on-device backend and restores the active profile once per app process, before any activity. */
class OtterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        EmbeddedBackend(this).start()
        ServerSettings.initialize(this)
        PlaybackSettings.initialize(this)
        ProfileSession.initialize(this)
        CastSetup.initialize(this)
    }
}
