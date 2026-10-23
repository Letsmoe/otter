package dev.neoworks.otter.backend

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import android.util.Log
import dev.neoworks.otter.BuildConfig
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.thread

/**
 * Runs the Go backend on the phone. The binary ships as a native library
 * (libotter.so, built by `bun run build:backend`) because the app's native
 * library folder is the only place Android lets an app execute files from.
 * The API listens on loopback only, at [BASE_URL]; streams are also served
 * on the LAN port for casting.
 */
class EmbeddedBackend(context: Context) {

    private val context = context.applicationContext
    private val dataDir = File(this.context.filesDir, "otter")
    private val seedDir = File(this.context.filesDir, "plugin-seed")
    private val dnsFile = File(dataDir, "dns-servers")
    private var restarts = 0

    /** Prepares the plugin seed and DNS file, then starts the backend on a background thread. */
    fun start() {
        thread(name = "otter-backend") { prepareAndRun() }
    }

    /** Runs the backend, restarting it when it crashes. */
    private fun prepareAndRun() {
        try {
            dataDir.mkdirs()
            refreshPluginSeed()
            watchDnsServers()
        } catch (error: IOException) {
            Log.e(TAG, "preparing backend failed", error)
            return
        }
        while (restarts <= MAX_RESTARTS) {
            val exitCode = runOnce()
            restarts++
            Log.e(TAG, "backend exited with $exitCode, restart $restarts/$MAX_RESTARTS")
            Thread.sleep(1000L * restarts)
        }
    }

    /** Starts the backend process and blocks until it exits, forwarding its log to logcat. */
    private fun runOnce(): Int {
        val binary = context.applicationInfo.nativeLibraryDir + "/libotter.so"
        val builder = ProcessBuilder(binary).redirectErrorStream(true)
        builder.environment().putAll(environment())
        return try {
            val process = builder.start()
            forwardLog(process.inputStream)
            process.waitFor()
        } catch (error: IOException) {
            Log.e(TAG, "starting backend failed", error)
            -1
        }
    }

    private fun environment(): Map<String, String> = mapOf(
        "DATA_DIR" to dataDir.absolutePath,
        "HOST" to "127.0.0.1",
        "PORT" to BuildConfig.BACKEND_PORT.toString(),
        // Streams and blobs only, private-network clients only: what a Chromecast plays.
        "LAN_STREAM_PORT" to BuildConfig.BACKEND_LAN_PORT.toString(),
        "PUBLIC_URL" to BASE_URL,
        "PLUGIN_DIR" to seedDir.absolutePath,
        "DNS_SERVERS_FILE" to dnsFile.absolutePath,
        "DNS_SERVERS" to PUBLIC_DNS_SERVERS,
        // Without cgo the backend can't detect the Android version, which it
        // needs to list network interfaces (WebRTC, VPN check) on Android 11+.
        "ANDROID_SDK_INT" to Build.VERSION.SDK_INT.toString(),
        // Go's os.TempDir defaults to /data/local/tmp, which apps can't write.
        "TMPDIR" to context.cacheDir.absolutePath,
    )

    private fun forwardLog(output: InputStream) {
        output.bufferedReader().forEachLine { line -> Log.i(TAG, line) }
    }

    /**
     * Extracts the plugin bundles shipped in the APK (assets/plugins) once per
     * install or update of the app. The backend loads them from there.
     */
    private fun refreshPluginSeed() {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val updateTime = packageUpdateTime()
        if (seedDir.isDirectory && preferences.getLong(SEEDED_UPDATE_TIME, 0) == updateTime) {
            return
        }
        seedDir.deleteRecursively()
        copyAssets(context.assets, "plugins", seedDir)
        preferences.edit().putLong(SEEDED_UPDATE_TIME, updateTime).apply()
    }

    private fun packageUpdateTime(): Long {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        } catch (error: PackageManager.NameNotFoundException) {
            0
        }
    }

    private fun copyAssets(assets: AssetManager, path: String, target: File) {
        val children = assets.list(path)
        if (children.isNullOrEmpty()) {
            copyAsset(assets, path, target)
            return
        }
        target.mkdirs()
        for (child in children) {
            copyAssets(assets, "$path/$child", File(target, child))
        }
    }

    private fun copyAsset(assets: AssetManager, path: String, target: File) {
        assets.open(path).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    /**
     * Keeps dnsFile listing the current network's DNS servers. Go can't ask
     * Android for them (pure-Go build, no resolv.conf); the backend re-reads the
     * file on every lookup, so Wi-Fi/mobile/VPN switches take effect at once.
     */
    private fun watchDnsServers() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivity.activeNetwork
        if (activeNetwork != null) {
            writeDnsServers(connectivity.getLinkProperties(activeNetwork))
        }
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                writeDnsServers(properties)
            }
        })
    }

    /** Writes the public DNS servers, then the network's, to dnsFile, one per line. */
    private fun writeDnsServers(properties: LinkProperties?) {
        if (properties == null) {
            return
        }
        val networkServers = properties.dnsServers.mapNotNull { server -> server.hostAddress }
        val servers = PUBLIC_DNS_SERVERS.split(",") + networkServers
        val temporary = File(dataDir, ".dns-servers.tmp")
        try {
            temporary.writeText(servers.joinToString("\n"))
        } catch (error: IOException) {
            Log.w(TAG, "writing DNS servers failed", error)
            return
        }
        if (!temporary.renameTo(dnsFile)) {
            Log.w(TAG, "replacing DNS server file failed")
        }
    }

    companion object {
        private const val TAG = "OtterBackend"
        val BASE_URL = "http://127.0.0.1:${BuildConfig.BACKEND_PORT}"

        /**
         * Asked before the network's own DNS servers: provider resolvers block
         * streaming sites (e.g. the German CUII list) by answering with a
         * landing page. The network's servers stay as the fallback.
         */
        private const val PUBLIC_DNS_SERVERS = "1.1.1.1,8.8.8.8"
        private const val MAX_RESTARTS = 5
        private const val PREFERENCES = "embedded-backend"
        private const val SEEDED_UPDATE_TIME = "seededUpdateTime"
    }
}
