package com.letsmoe.otter.reporting

import android.app.Application
import android.content.Context
import android.util.Log
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

private const val TAG = "OtterErrors"
private const val PREFERENCES = "error_reporting"
private const val ENABLED = "enabled"
private const val CAST_LOGS = "cast_logs"

// Public ingest key of the Otter PostHog project; it can only send events.
private const val PROJECT_TOKEN = "phc_kWtXWGtB5xAPwYhcu7uphFdBUgLTdhdK3g8NPvSF9EFa"
private const val HOST = "https://eu.i.posthog.com"

/**
 * Sends crashes and reported errors to PostHog error tracking, so failures on
 * other people's phones can be investigated. On unless turned off in Settings.
 * Only exceptions are sent: no screen views, sessions or person profiles.
 */
object ErrorReporting {

    private lateinit var application: Application

    fun initialize(application: Application) {
        this.application = application
        val config = PostHogAndroidConfig(apiKey = PROJECT_TOKEN, host = HOST)
        config.captureApplicationLifecycleEvents = false
        config.captureScreenViews = false
        config.captureDeepLinks = false
        config.sessionReplay = false
        config.surveys = false
        config.preloadFeatureFlags = false
        config.remoteConfig = false
        config.personProfiles = PersonProfiles.NEVER
        config.errorTrackingConfig.autoCapture = true
        config.logs.serviceName = "otter-android"
        config.optOut = !isEnabled()
        PostHogAndroid.setup(application, config)
    }

    fun isEnabled(): Boolean {
        // Not set up outside the app, e.g. in unit tests.
        if (!::application.isInitialized) {
            return false
        }
        return preferences().getBoolean(ENABLED, true)
    }

    fun setEnabled(enabled: Boolean) {
        preferences().edit().putBoolean(ENABLED, enabled).apply()
        if (enabled) {
            PostHog.optIn()
        } else {
            PostHog.optOut()
        }
    }

    /** Reports a caught error; [kind] names where it happened (e.g. "dlna.playback"). */
    fun capture(error: Throwable, kind: String, properties: Map<String, Any?> = emptyMap()) {
        if (!isEnabled()) {
            return
        }
        try {
            PostHog.captureException(error, presentValues(properties) + ("kind" to kind))
        } catch (reportingError: Exception) {
            Log.w(TAG, "reporting failed", reportingError)
        }
    }

    /** Whether every cast is logged in detail; opt-in, as a daily cast sends hundreds of lines. */
    fun isCastLogging(): Boolean {
        if (!::application.isInitialized) {
            return false
        }
        return preferences().getBoolean(CAST_LOGS, false)
    }

    fun setCastLogging(enabled: Boolean) {
        preferences().edit().putBoolean(CAST_LOGS, enabled).apply()
    }

    /** Sends a line about a cast (e.g. one exchange with a TV) to PostHog logs, if cast logging is on. */
    fun logCast(message: String, attributes: Map<String, Any?> = emptyMap()) {
        if (!isEnabled() || !isCastLogging()) {
            return
        }
        try {
            PostHog.logger.info(message, presentValues(attributes))
        } catch (reportingError: Exception) {
            Log.w(TAG, "logging failed", reportingError)
        }
    }

    private fun presentValues(values: Map<String, Any?>): Map<String, Any> {
        return values.filterValues { value -> value != null }.mapValues { entry -> entry.value as Any }
    }

    private fun preferences() = application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
