package com.letsmoe.otter.updates

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.letsmoe.otter.BuildConfig

/**
 * Where updates come from. Release: the latest GitHub release. Nightly: the rolling "nightly"
 * pre-release CI builds on every push to main.
 */
enum class UpdateChannel(val label: String, val releasePath: String) {
    RELEASE("Release", "releases/latest"),
    NIGHTLY("Nightly", "releases/tags/nightly"),
}

/** The chosen update channel, saved on this device only. */
object UpdateSettings {

    private const val PREFERENCES = "updates"
    private const val KEY_CHANNEL = "channel"

    private lateinit var preferences: SharedPreferences

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }

    /** Defaults to the channel of the installed build. */
    var channel: UpdateChannel
        get() {
            val stored = preferences.getString(KEY_CHANNEL, null)
            return UpdateChannel.entries.firstOrNull { option -> option.name == stored } ?: installedChannel()
        }
        set(value) {
            preferences.edit { putString(KEY_CHANNEL, value.name) }
        }

    fun installedChannel(): UpdateChannel {
        if (BuildConfig.UPDATE_CHANNEL == "nightly") {
            return UpdateChannel.NIGHTLY
        }
        return UpdateChannel.RELEASE
    }
}

private val buildNumberPattern = Regex("-build(\\d+)\\.apk$")

/** CI names APKs "otter-v1.2.0-build57.apk"; the number is the CI run that built it. Null for older names. */
fun buildNumberOf(assetName: String): Int? {
    val match = buildNumberPattern.find(assetName) ?: return null
    return match.groupValues[1].toIntOrNull()
}

/**
 * Whether [release] is newer than the installed app: by build number when both are known, else
 * by version. A local build has build number 0.
 */
fun isNewerThanInstalled(release: AppRelease, installedBuild: Int, installedVersion: String): Boolean {
    if (release.build != null && installedBuild > 0) {
        return release.build > installedBuild
    }
    if (release.channel == UpdateChannel.NIGHTLY) {
        return release.build != null
    }
    return isNewerVersion(release.version, installedVersion)
}

/** Whether [release] is the APK already installed, so installing it would change nothing. */
fun isInstalled(release: AppRelease, installedBuild: Int): Boolean {
    return release.build != null && release.build == installedBuild
}
