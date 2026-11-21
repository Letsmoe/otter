package com.letsmoe.otter.ui.watchorder

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.letsmoe.otter.ui.FailedMessage
import com.letsmoe.otter.ui.LoadState
import com.letsmoe.otter.ui.components.PillButton
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.navigation.bottomBarClearance
import com.letsmoe.otter.ui.theme.OtterColors
import com.letsmoe.otter.ui.title.GlassIconButton

/** What the guide's buttons and rows open. */
class WatchOrderActions(
    /** Plays a film (the main button: the next film, resuming where the profile stopped). */
    val onPlay: (titleId: String) -> Unit,
    /** Opens a film's page. */
    val onOpenFilm: (titleId: String) -> Unit,
)

private val posterShape = RoundedCornerShape(6.dp)
private val mutedColor = Color.White.copy(alpha = 0.6f)

/** A franchise's watch-order guide: the artwork, the profile's progress, Continue, and every film in order. */
@Composable
fun WatchOrderScreen(collectionId: String, actions: WatchOrderActions, onBack: () -> Unit) {
    val viewModel = viewModel(key = "watch-order-$collectionId") { WatchOrderViewModel(collectionId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val current = state) {
            is LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is LoadState.Failed -> FailedMessage(current.message, onRetry = viewModel::load)
            is LoadState.Loaded -> WatchOrderContent(current.value, actions, header = { BackButton(onBack) })
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    GlassIconButton(PhosphorIcons.Regular.ArrowLeft, "Back", onBack)
}

/** The guide itself; [header] sits above the artwork (the back button). */
@Composable
fun WatchOrderContent(guide: WatchOrderGuide, actions: WatchOrderActions, modifier: Modifier = Modifier, header: @Composable () -> Unit = {}) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomBarClearance()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Column(Modifier.statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.padding(start = 12.dp, top = 8.dp)) { header() }
                WatchOrderArtwork(guide, Modifier.padding(horizontal = 12.dp).fillMaxWidth())
            }
        }
        item(key = "summary") { Summary(guide, actions) }
        itemsIndexed(guide.parts, key = { _, part -> part.number }) { _, part ->
            FilmRow(part, isNext = part.number == guide.nextNumber, onClick = { actions.onOpenFilm(part.titleId) })
        }
    }
}

@Composable
private fun Summary(guide: WatchOrderGuide, actions: WatchOrderActions) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(progressLabel(guide), fontSize = 14.sp, color = mutedColor)
        ProgressBar(watchedFraction(guide))
        ContinueButton(guide, actions)
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(OtterColors.success))
    }
}

@Composable
private fun ContinueButton(guide: WatchOrderGuide, actions: WatchOrderActions) {
    val label = continueLabel(guide)
    val next = guide.next
    if (label == null || next == null) {
        Text("You've watched every film that is out so far.", fontSize = 14.sp, color = mutedColor)
        return
    }
    PillButton(label, Modifier.fillMaxWidth(), enabled = true, filled = true, onClick = { actions.onPlay(next.titleId) })
}

@Composable
private fun FilmRow(part: GuidePart, isNext: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberBadge(part)
        ArtworkPoster(part.posterUrl, Modifier.width(52.dp).aspectRatio(2f / 3f).clip(posterShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(part.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(partFacts(part, isNext), fontSize = 12.sp, color = mutedColor)
            Text(statusLabel(part), fontSize = 12.sp, color = statusColor(part))
        }
    }
}

@Composable
private fun NumberBadge(part: GuidePart) {
    var background = Color.White.copy(alpha = 0.1f)
    var content = Color.White
    if (part.status == PartStatus.WATCHED) {
        background = OtterColors.success
        content = Color(0xFF082814)
    }
    if (part.status == PartStatus.IN_PROGRESS) {
        background = OtterColors.accent
        content = Color(0xFF140A32)
    }
    Box(Modifier.size(28.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Text(part.number.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = content)
    }
}

private fun statusColor(part: GuidePart): Color {
    if (part.status == PartStatus.WATCHED) {
        return OtterColors.success
    }
    if (part.status == PartStatus.IN_PROGRESS) {
        return OtterColors.accent
    }
    return mutedColor
}
