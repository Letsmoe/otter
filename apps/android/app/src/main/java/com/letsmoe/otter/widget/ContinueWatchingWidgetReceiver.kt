package com.letsmoe.otter.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.letsmoe.otter.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The player's last progress reaches the backend within this time of the app going away.
private const val PROGRESS_SAVED_MILLISECONDS = 3000L

/** The system's entry for the continue-watching widget; it refreshes by itself every 30 minutes. */
class ContinueWatchingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueWatchingWidget()
}

/** Updates the widgets (if any are placed) shortly after what is being watched changed. */
fun refreshContinueWatchingWidgetSoon(context: Context) {
    val applicationContext = context.applicationContext
    AppScope.launch(Dispatchers.Default) {
        delay(PROGRESS_SAVED_MILLISECONDS)
        ContinueWatchingWidget().updateAll(applicationContext)
    }
}
