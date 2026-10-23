package dev.neoworks.otter.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.adamglin.phosphoricons.regular.ClockCounterClockwise
import dev.neoworks.otter.ui.components.ScreenHeader
import dev.neoworks.otter.ui.components.WithoutBackdrop
import dev.neoworks.otter.ui.components.frostedGlass
import dev.neoworks.otter.ui.media.RailItem
import dev.neoworks.otter.ui.navigation.bottomBarClearance
import dev.neoworks.otter.ui.theme.OtterColors

/** Watch History: what the profile watched, newest first, grouped by day, with each title's progress. */
@Composable
fun HistoryScreen(onOpenItem: (RailItem) -> Unit, onBack: () -> Unit, viewModel: HistoryViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WithoutBackdrop {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomBarClearance()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { ScreenHeader("Watch History", onBack) }
            items(state.days) { day -> DaySection(day, onOpenItem) }
            if (state.days.isEmpty() && state.loaded) {
                item { EmptyHistory() }
            }
        }
    }
}

@Composable
private fun DaySection(day: HistoryDay, onOpenItem: (RailItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            day.label.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(
            Modifier.fillMaxWidth().frostedGlass(RoundedCornerShape(24.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (item in day.items) {
                HistoryRow(item, onClick = { onOpenItem(item) })
            }
        }
    }
}

@Composable
private fun HistoryRow(item: RailItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f))) {
            if (item.backdropUrl != null) {
                AsyncImage(item.backdropUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            HistoryProgress(item.progress, Modifier.align(Alignment.BottomStart))
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle != null) {
                Text(item.subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HistoryProgress(progress: Float?, modifier: Modifier) {
    var fraction = 0f
    if (progress != null) {
        fraction = progress
    }
    Box(modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.2f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(OtterColors.baseContent))
    }
}

@Composable
private fun EmptyHistory() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(PhosphorIcons.Regular.ClockCounterClockwise, null, Modifier.size(56.dp).alpha(0.4f))
        Text("What you watch shows up here.", fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.alpha(0.6f))
    }
}
