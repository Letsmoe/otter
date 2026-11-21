package com.letsmoe.otter.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.letsmoe.otter.MainActivity
import com.letsmoe.otter.links.PLAY_FROM_WIDGET_ACTION
import com.letsmoe.otter.links.PLAY_FROM_WIDGET_MEDIA_ID
import com.letsmoe.otter.ui.theme.OtterColors

private val backgroundColor = ColorProvider(OtterColors.base200)
private val textColor = ColorProvider(OtterColors.baseContent)
private val mutedTextColor = ColorProvider(OtterColors.secondaryContent)
private val posterPlaceholderColor = ColorProvider(OtterColors.base300)

/**
 * The home screen widget: the profile's continue-watching titles as posters with their progress.
 * A tap starts the title, resuming where it stopped (on the TV when one is connected).
 */
class ContinueWatchingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = loadWidgetContent(context)
        provideContent { WidgetContent(context, content) }
    }
}

@Composable
private fun WidgetContent(context: Context, content: WidgetContentState) {
    Column(GlanceModifier.fillMaxSize().background(backgroundColor).cornerRadius(20.dp).padding(12.dp)) {
        Text("Continue watching", style = TextStyle(color = mutedTextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.height(8.dp))
        when (content) {
            is WidgetContentState.NoProfile -> Message("Open Otter and pick a profile")
            is WidgetContentState.Titles -> TitleRow(context, content.titles)
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = mutedTextColor, fontSize = 13.sp))
    }
}

@Composable
private fun TitleRow(context: Context, titles: List<WidgetTitle>) {
    if (titles.isEmpty()) {
        Message("Nothing to continue yet")
        return
    }
    Row(GlanceModifier.fillMaxSize()) {
        for (title in titles) {
            TitleCard(context, title)
        }
    }
}

@Composable
private fun RowScope.TitleCard(context: Context, title: WidgetTitle) {
    Column(
        GlanceModifier.defaultWeight().fillMaxSize().padding(horizontal = 4.dp).clickable(actionStartActivity(playIntent(context, title))),
    ) {
        Poster(title)
        Text(title.title, maxLines = 1, style = TextStyle(color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        title.subtitle?.let { subtitle ->
            Text(subtitle, maxLines = 1, style = TextStyle(color = mutedTextColor, fontSize = 10.sp))
        }
        title.progress?.let { progress ->
            LinearProgressIndicator(progress, GlanceModifier.fillMaxWidth().padding(top = 3.dp), textColor, mutedTextColor)
        }
    }
}

@Composable
private fun ColumnScope.Poster(title: WidgetTitle) {
    val poster = title.poster
    if (poster == null) {
        Box(GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(10.dp).background(posterPlaceholderColor)) {}
        return
    }
    Image(
        provider = ImageProvider(poster),
        contentDescription = title.title,
        contentScale = ContentScale.Crop,
        modifier = GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(10.dp),
    )
}

// The data makes every title's intent distinct, so the system doesn't hand one title's tap to another.
private fun playIntent(context: Context, title: WidgetTitle): Intent {
    return Intent(context, MainActivity::class.java)
        .setAction(PLAY_FROM_WIDGET_ACTION)
        .setData(Uri.parse("otter-widget://play/${Uri.encode(title.mediaId)}"))
        .putExtra(PLAY_FROM_WIDGET_MEDIA_ID, title.mediaId)
}
