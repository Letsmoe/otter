package com.letsmoe.otter.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A function asking for the notification permission (Android 13+) unless it's granted; adding a
 * title to My List calls it, since that's when its news starts to matter. [onGranted] runs once
 * notifications are allowed. With [openSettingsWhenRefused], a refusal (also the silent one when
 * Android or ColorOS won't show the dialog anymore) opens the app's notification settings.
 */
@Composable
fun rememberNotificationPermissionRequest(openSettingsWhenRefused: Boolean = false, onGranted: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            onGranted()
            return@rememberLauncherForActivityResult
        }
        if (openSettingsWhenRefused) {
            openNotificationSettings(context)
        }
    }
    return request@{
        if (PhoneNotifications.allowed(context)) {
            onGranted()
            return@request
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return@request
        }
        // Below Android 13 there is no dialog: notifications were switched off in the settings.
        if (openSettingsWhenRefused) {
            openNotificationSettings(context)
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
