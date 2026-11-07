package com.letsmoe.otter.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.letsmoe.otter.MainActivity
import com.letsmoe.otter.R
import com.letsmoe.otter.ui.navigation.Routes

private const val CHANNEL_ID = "my_list_news"

/** Title links in a notification's tap intent: otter://title?route=<encoded app route>. */
const val TITLE_LINK_PREFIX = "otter://title"

/** News about titles on My List in the phone's notification shade. */
object PhoneNotifications {

    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /** Posts [item]; [profileName] names whose news it is when several profiles share the phone. */
    @Suppress("MissingPermission") // Checked by allowed().
    suspend fun post(context: Context, item: NotificationItem, profileName: String?) {
        if (!allowed(context)) {
            return
        }
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.titleName)
            .setContentText(contentText(item))
            .setSubText(profileName)
            .setAutoCancel(true)
        if (item.titleId.isNotEmpty()) {
            builder.setContentIntent(openTitleIntent(context, item))
        }
        val image = loadImage(context, item.imageUrl)
        if (image != null) {
            builder.setLargeIcon(image)
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(image).bigLargeIcon(null as Bitmap?))
        }
        NotificationManagerCompat.from(context).notify(item.id.hashCode(), builder.build())
    }

    private fun contentText(item: NotificationItem): String {
        if (item.body.isNullOrBlank()) {
            return item.heading
        }
        return "${item.heading} · ${item.body}"
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "My List news", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "New episodes, releases and found sources for titles on My List"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openTitleIntent(context: Context, item: NotificationItem): PendingIntent {
        val route = Routes.title(item.titleId, item.seasonNumber, item.episodeId)
        val link = Uri.parse(TITLE_LINK_PREFIX).buildUpon().appendQueryParameter("route", route).build()
        val intent = Intent(Intent.ACTION_VIEW, link, context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(context, item.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private suspend fun loadImage(context: Context, imageUrl: String?): Bitmap? {
        if (imageUrl == null) {
            return null
        }
        val request = ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request)
        if (result !is SuccessResult) {
            return null
        }
        return result.image.toBitmap()
    }
}
