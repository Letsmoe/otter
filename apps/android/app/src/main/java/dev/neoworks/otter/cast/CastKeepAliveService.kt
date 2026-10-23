package dev.neoworks.otter.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import dev.neoworks.otter.MainActivity

/**
 * Runs while a TV plays from this phone. The TV fetches every segment from the
 * on-device backend, so the app process, the CPU and Wi-Fi must stay up even
 * with the screen off.
 */
class CastKeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME)
        startForegroundWith(notification(deviceName))
        acquireLocks()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.release()
        wifiLock?.release()
        wakeLock = null
        wifiLock = null
        super.onDestroy()
    }

    private fun startForegroundWith(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            return
        }
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun acquireLocks() {
        if (wakeLock == null) {
            val power = getSystemService(PowerManager::class.java)
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "otter:cast").apply { acquire() }
        }
        if (wifiLock == null) {
            val wifi = applicationContext.getSystemService(WifiManager::class.java)
            wifiLock = wifi.createWifiLock(wifiLockMode(), "otter:cast").apply { acquire() }
        }
    }

    @Suppress("DEPRECATION") // WIFI_MODE_FULL_HIGH_PERF is the pre-Q equivalent.
    private fun wifiLockMode(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        }
        return WifiManager.WIFI_MODE_FULL_HIGH_PERF
    }

    private fun notification(deviceName: String?): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Casting", NotificationManager.IMPORTANCE_LOW))
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        var text = "Streaming to your TV"
        if (deviceName != null) {
            text = "Streaming to $deviceName"
        }
        return notificationBuilder()
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Otter")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    @Suppress("DEPRECATION") // The channel-less builder is the only one before O.
    private fun notificationBuilder(): Notification.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Notification.Builder(this, CHANNEL_ID)
        }
        return Notification.Builder(this)
    }

    companion object {
        private const val CHANNEL_ID = "cast"
        private const val NOTIFICATION_ID = 4711
        private const val EXTRA_DEVICE_NAME = "deviceName"

        fun start(context: Context, deviceName: String?) {
            val intent = Intent(context, CastKeepAliveService::class.java).putExtra(EXTRA_DEVICE_NAME, deviceName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
                return
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CastKeepAliveService::class.java))
        }
    }
}
