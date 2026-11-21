package com.letsmoe.otter.ui.upcoming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CalendarBlank
import com.letsmoe.otter.ui.FailedMessage
import com.letsmoe.otter.ui.LoadState
import com.letsmoe.otter.ui.components.PRESSED_SCALE_LARGE
import com.letsmoe.otter.ui.components.ScreenHeader
import com.letsmoe.otter.ui.components.WithoutBackdrop
import com.letsmoe.otter.ui.components.frostedGlass
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.navigation.bottomBarClearance

private val mutedColor = Color.White.copy(alpha = 0.55f)

/** The coming episodes of the series the profile watches or has on My List, grouped by day. */
@Composable
fun UpcomingScreen(onOpenItem: (RailItem) -> Unit, onBack: () -> Unit, viewModel: UpcomingViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WithoutBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader("Upcoming", onBack)
            when (val current = state) {
                is LoadState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator() }
                is LoadState.Failed -> FailedMessage(current.message, onRetry = viewModel::load)
                is LoadState.Loaded -> UpcomingList(current.value, onOpenItem)
            }
        }
    }
}

@Composable
private fun UpcomingList(days: List<UpcomingDay>, onOpenItem: (RailItem) -> Unit) {
    if (days.isEmpty()) {
        EmptyUpcoming()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomBarClearance()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(days) { day -> DaySection(day, onOpenItem) }
    }
}

@Composable
private fun DaySection(day: UpcomingDay, onOpenItem: (RailItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(day.heading, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(day.dateLabel, fontSize = 13.sp, color = mutedColor)
        }
        Column(
            Modifier.fillMaxWidth().frostedGlass(RoundedCornerShape(24.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (episode in day.episodes) {
                EpisodeRow(episode, onClick = { onOpenItem(episode.item) })
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: UpcomingEpisode, onClick: () -> Unit) {
    Row(
        Modifier.pressable(pressedScale = PRESSED_SCALE_LARGE, onClick = onClick).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f))) {
            if (episode.imageUrl != null) {
                AsyncImage(episode.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f)) {
            Text(episode.item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (episode.item.subtitle != null) {
                Text(episode.item.subtitle, fontSize = 12.sp, color = mutedColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EmptyUpcoming() {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(PhosphorIcons.Regular.CalendarBlank, null, Modifier.size(56.dp).alpha(0.4f))
        Text(
            "No new episodes in the next four weeks. Series you watch or add to My List show up here once TMDB has an air date.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(0.6f),
        )
    }
}
