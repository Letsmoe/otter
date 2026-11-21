package com.letsmoe.otter.cast

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
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaStyleNotificationHelper
import com.letsmoe.otter.MainActivity

/**
 * Runs while a TV plays from this phone. The TV fetches every segment from the
 * on-device backend, so the app process, the CPU and Wi-Fi must stay up even
 * with the screen off. On a DLNA TV it also owns the media session whose
 * notification puts the TV's controls on the lock screen ([LockScreenControls]).
 */
class CastKeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var controls: LockScreenControls? = null
    private var deviceName: String? = null

    private val sessionListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshNotification()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = refreshNotification()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refreshNotification()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> controls?.togglePlay()
            ACTION_REPLAY -> controls?.replayWithCaptions()
            else -> start(intent?.getStringExtra(EXTRA_DEVICE_NAME))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseControls()
        wakeLock?.release()
        wifiLock?.release()
        wakeLock = null
        wifiLock = null
        super.onDestroy()
    }

    private fun start(name: String?) {
        deviceName = name
        followConnectedTelevision()
        startForegroundWith(notification())
        acquireLocks()
    }

    // A session exists for as long as the same DLNA TV is connected; a Chromecast has its own controls.
    private fun followConnectedTelevision() {
        val target = TelevisionConnection.target.value
        if (target !is TelevisionTarget.Dlna) {
            releaseControls()
            return
        }
        if (controls?.television === target.player) {
            return
        }
        releaseControls()
        val newControls = LockScreenControls(this, target.player, openAppIntent())
        newControls.player.addListener(sessionListener)
        controls = newControls
    }

    private fun releaseControls() {
        val current = controls ?: return
        current.player.removeListener(sessionListener)
        current.release()
        controls = null
    }

    private fun startForegroundWith(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            return
        }
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun refreshNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification())
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

    private fun openAppIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Casting", NotificationManager.IMPORTANCE_LOW)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(notificationTitle())
            .setContentText(streamingText())
            .setContentIntent(openAppIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
        addControls(builder)
        return builder.build()
    }

    private fun notificationTitle(): String {
        val title = controls?.player?.mediaMetadata?.title?.toString()
        if (title.isNullOrBlank()) {
            return "Otter"
        }
        return title
    }

    private fun streamingText(): String {
        val name = deviceName
        if (name == null) {
            return "Streaming to your TV"
        }
        return "Streaming to $name"
    }

    // Before Android 13 the shade shows these buttons; from 13 on the session's own controls replace them.
    private fun addControls(builder: NotificationCompat.Builder) {
        val current = controls ?: return
        var playPauseIcon = android.R.drawable.ic_media_pause
        var playPauseLabel = "Pause"
        if (!current.player.playWhenReady) {
            playPauseIcon = android.R.drawable.ic_media_play
            playPauseLabel = "Play"
        }
        builder
            .addAction(android.R.drawable.ic_media_rew, "Replay with captions", actionIntent(ACTION_REPLAY))
            .addAction(playPauseIcon, playPauseLabel, actionIntent(ACTION_PLAY_PAUSE))
            .setStyle(MediaStyleNotificationHelper.MediaStyle(current.session).setShowActionsInCompactView(0, 1))
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, CastKeepAliveService::class.java).setAction(action)
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        private const val CHANNEL_ID = "cast"
        private const val NOTIFICATION_ID = 4711
        private const val EXTRA_DEVICE_NAME = "deviceName"
        private const val ACTION_PLAY_PAUSE = "com.letsmoe.otter.cast.PLAY_PAUSE"
        private const val ACTION_REPLAY = "com.letsmoe.otter.cast.REPLAY"

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
