package com.letsmoe.otter.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.letsmoe.otter.AppScope
import com.letsmoe.otter.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "OtterUpdater"
private const val LATEST_RELEASE_URL = "https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/latest"
private const val TIMEOUT_MILLISECONDS = 15_000
private const val UPDATES_DIRECTORY = "updates"
private const val APK_FILE = "otter-update.apk"

/** A published release with an APK attached. */
data class AppRelease(val version: String, val apkUrl: String, val apkSize: Long)

sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class ReadyToInstall(val release: AppRelease, val apk: File) : UpdateState
    data class Failed(val release: AppRelease, val message: String) : UpdateState
}

/**
 * Installs new versions from the repository's GitHub releases: checks the latest release once per
 * start, downloads its APK on request and hands it to Android's package installer.
 */
object AppUpdater {

    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = mutableState

    private var checked = false

    fun checkForUpdate() {
        if (checked) {
            return
        }
        checked = true
        AppScope.launch {
            val release = fetchLatestRelease() ?: return@launch
            if (!isNewerVersion(release.version, BuildConfig.VERSION_NAME)) {
                return@launch
            }
            mutableState.value = UpdateState.Available(release)
        }
    }

    fun dismiss() {
        mutableState.value = UpdateState.None
    }

    fun downloadAndInstall(context: Context, release: AppRelease) {
        val applicationContext = context.applicationContext
        AppScope.launch {
            mutableState.value = UpdateState.Downloading(release, 0f)
            try {
                val apk = downloadApk(applicationContext, release)
                mutableState.value = UpdateState.ReadyToInstall(release, apk)
                install(applicationContext, apk)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.w(TAG, "update download failed", error)
                mutableState.value = UpdateState.Failed(release, "Download failed")
            }
        }
    }

    /**
     * Opens the system installer. Without the "install unknown apps" permission it opens that
     * setting instead; the banner keeps the downloaded APK so the user can try again.
     */
    fun install(context: Context, apk: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val permission = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            context.startActivity(permission.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val installer = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(installer)
    }

    private suspend fun fetchLatestRelease(): AppRelease? = withContext(Dispatchers.IO) {
        try {
            val connection = openConnection(LATEST_RELEASE_URL)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext null
            }
            val body = connection.inputStream.bufferedReader().use { reader -> reader.readText() }
            connection.disconnect()
            parseRelease(JSONObject(body))
        } catch (error: Exception) {
            Log.w(TAG, "update check failed", error)
            null
        }
    }

    private suspend fun downloadApk(context: Context, release: AppRelease): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, UPDATES_DIRECTORY)
        directory.mkdirs()
        val target = File(directory, APK_FILE)
        val partial = File(directory, "$APK_FILE.part")
        val connection = openConnection(release.apkUrl)
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }
        connection.inputStream.use { input ->
            partial.outputStream().use { output -> copyWithProgress(input, output, release) }
        }
        connection.disconnect()
        if (!partial.renameTo(target)) {
            throw IllegalStateException("could not move the download into place")
        }
        target
    }

    private fun copyWithProgress(input: java.io.InputStream, output: java.io.OutputStream, release: AppRelease) {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                return
            }
            output.write(buffer, 0, read)
            copied += read
            reportProgress(release, copied)
        }
    }

    private fun reportProgress(release: AppRelease, copied: Long) {
        if (release.apkSize <= 0) {
            return
        }
        // Whole percent steps only: the banner doesn't need an update per buffer.
        val progress = (copied * 100 / release.apkSize).coerceIn(0, 100) / 100f
        val current = mutableState.value
        if (current is UpdateState.Downloading && current.progress == progress) {
            return
        }
        mutableState.value = UpdateState.Downloading(release, progress)
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLISECONDS
        connection.readTimeout = TIMEOUT_MILLISECONDS
        connection.instanceFollowRedirects = true
        return connection
    }
}

/** The release's tag and first .apk asset; null for releases without an APK. */
private fun parseRelease(release: JSONObject): AppRelease? {
    val assets = release.optJSONArray("assets") ?: return null
    for (index in 0 until assets.length()) {
        val asset = assets.getJSONObject(index)
        if (asset.optString("name").endsWith(".apk")) {
            return AppRelease(release.getString("tag_name"), asset.getString("browser_download_url"), asset.optLong("size"))
        }
    }
    return null
}
