package dev.neoworks.otter.api

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.neoworks.otter.backend.EmbeddedBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Which backend this device talks to: the one running on the phone, or another server (e.g. a NAS)
 * saved in Settings. A new address applies after the app restarts. Mirrors lib/server.ts.
 */
object ServerSettings {

    private const val PREFERENCES = "server"
    private const val KEY_URL = "url"
    private const val CHECK_TIMEOUT_MILLISECONDS = 5000

    private lateinit var preferences: SharedPreferences

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }

    /** The address saved on this device, if any. */
    val storedUrl: String?
        get() = preferences.getString(KEY_URL, null)

    /** The saved address, else the backend the phone runs itself. */
    val baseUrl: String
        get() {
            val stored = storedUrl
            if (stored.isNullOrEmpty()) {
                return EmbeddedBackend.BASE_URL
            }
            return stored
        }

    val usesEmbeddedBackend: Boolean
        get() = storedUrl.isNullOrEmpty()

    fun save(url: String) {
        preferences.edit { putString(KEY_URL, normalize(url)) }
    }

    fun clear() {
        preferences.edit { remove(KEY_URL) }
    }

    /** Trims whitespace and trailing slashes and defaults the scheme to http. */
    fun normalize(url: String): String {
        var normalized = url.trim().trimEnd('/')
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        return normalized
    }

    /** Whether a backend answers its health check at the given address. */
    suspend fun check(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = URL("${normalize(url)}/health").openConnection() as HttpURLConnection
            connection.connectTimeout = CHECK_TIMEOUT_MILLISECONDS
            connection.readTimeout = CHECK_TIMEOUT_MILLISECONDS
            val reachable = connection.responseCode == 200
            connection.disconnect()
            reachable
        } catch (error: Exception) {
            false
        }
    }
}
