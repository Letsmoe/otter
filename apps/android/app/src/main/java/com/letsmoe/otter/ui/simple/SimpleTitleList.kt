package com.letsmoe.otter.ui.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.Play
import com.letsmoe.otter.toasts.ToastKind
import com.letsmoe.otter.toasts.Toasts
import com.letsmoe.otter.ui.components.PRESSED_SCALE_LARGE
import com.letsmoe.otter.ui.components.SubtleFill
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.title.fetchUpNextEpisodeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Starts what a tapped title plays: a movie at once, a series on the episode it is up to. */
class SimpleStarter(private val scope: CoroutineScope, private val openPlayer: (String) -> Unit) {

    var starting by mutableStateOf(false)
        private set

    fun start(item: RailItem) {
        if (starting) {
            return
        }
        val playId = directPlayId(item)
        if (playId != null) {
            openPlayer(playId)
            return
        }
        starting = true
        scope.launch { startSeries(item) }
    }

    private suspend fun startSeries(series: RailItem) {
        val episodeId = fetchUpNextEpisodeId(series.id)
        starting = false
        if (episodeId == null) {
            Toasts.push("Sorry, this could not be started. Please try again.", ToastKind.ERROR)
            return
        }
        openPlayer(episodeId)
    }
}

@Composable
fun rememberSimpleStarter(openPlayer: (String) -> Unit): SimpleStarter {
    val scope = rememberCoroutineScope()
    return remember { SimpleStarter(scope, openPlayer) }
}

/** Big rows of titles, each one a single tap to watch; [emptyText] says what to do when there are none. */
@Composable
fun SimpleTitleList(items: List<RailItem>, loaded: Boolean, emptyText: String, starter: SimpleStarter, modifier: Modifier = Modifier) {
    Box(modifier) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items, key = { item -> "${item.id}|${item.episodeId}" }) { item ->
                SimpleTitleRow(item, onPlay = { starter.start(item) })
            }
            if (items.isEmpty() && loaded && emptyText.isNotEmpty()) {
                item { SimpleNotice(emptyText) }
            }
        }
        if (starter.starting) {
            SimpleBusyOverlay("One moment…")
        }
    }
}

@Composable
private fun SimpleTitleRow(item: RailItem, onPlay: () -> Unit) {
    Row(
        Modifier
            .pressable(pressedScale = PRESSED_SCALE_LARGE, onClick = onPlay)
            .fillMaxWidth()
            .background(SubtleFill, RoundedCornerShape(24.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            item.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(88.dp).height(132.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, fontSize = SimpleSize.itemTitle, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(simpleSubtitle(item), fontSize = SimpleSize.body, modifier = Modifier.alpha(0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            WatchedBar(item.progress)
        }
        PlayDisc()
    }
}

@Composable
private fun WatchedBar(progress: Float?) {
    if (progress == null) {
        return
    }
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
        color = Color.White,
        trackColor = Color.White.copy(alpha = 0.2f),
    )
}

@Composable
private fun PlayDisc() {
    Box(Modifier.size(72.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
        Icon(PhosphorIcons.Fill.Play, contentDescription = "Watch", tint = Color.Black, modifier = Modifier.size(36.dp))
    }
}

@Composable
fun SimpleNotice(text: String) {
    Text(
        text,
        fontSize = SimpleSize.itemTitle,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 12.dp, end = 12.dp).alpha(0.8f),
    )
}

@Composable
fun SimpleBusyOverlay(text: String) {
    // Swallows taps so nothing underneath can be started twice.
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = SimpleSize.heading, fontWeight = FontWeight.Bold)
    }
}
