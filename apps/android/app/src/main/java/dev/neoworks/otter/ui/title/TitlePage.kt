package dev.neoworks.otter.ui.title

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neoworks.otter.ui.components.ActorItem
import dev.neoworks.otter.ui.components.ActorRail
import dev.neoworks.otter.ui.components.GlassBackdropBox
import dev.neoworks.otter.ui.components.WithoutBackdrop
import dev.neoworks.otter.ui.navigation.bottomBarClearance
import dev.neoworks.otter.ui.theme.OtterColors

/** The state of the page's Play button. */
class PlayButtonState(val label: String, val busy: Boolean, val ready: Boolean, val disabled: Boolean, val onClick: () -> Unit)

class TitlePageActions(
    val onBack: () -> Unit,
    val onOpenPerson: (String) -> Unit,
    val onSearch: () -> Unit,
    val onToggleWatchlist: () -> Unit,
    val onRate: (Double) -> Unit,
)

private val floatingNavHeight = 56.dp
private const val OVERVIEW_COLLAPSED_LINES = 4
private val mutedFactColor = Color.White.copy(alpha = 0.6f)

/**
 * The layout movie and series pages share: trailer banner, title and facts, Play, description,
 * credit lines, the action row and a tab bar with its content. Back and search float over the banner.
 */
@Composable
fun TitlePage(
    detail: TitleDetail,
    facts: List<String>,
    playButton: PlayButtonState,
    creditLines: List<String>,
    shareUrl: String,
    actions: TitlePageActions,
    slots: TitleSlots,
    tabBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    playExtras: @Composable () -> Unit = {},
    tabContent: @Composable () -> Unit,
) {
    val listState = rememberLazyListState()
    val trailer = rememberTrailerState(detail.trailerKey)
    // The banner sits below the status bar and the floating back/search row, not under them.
    val bannerPlacement = Modifier.statusBarsPadding().padding(top = floatingNavHeight)
    GlassBackdropBox(modifier.fillMaxSize()) { backdropSource ->
        TitleBackdropMedia(detail.bannerUrl, trailer, scroll = { bannerScroll(listState) }, modifier = bannerPlacement)
        WithoutBackdrop {
            LazyColumn(
                state = listState,
                modifier = backdropSource.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomBarClearance()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { TitleBackdropOverlay(trailer, modifier = bannerPlacement) }
                item {
                    TitleSummary(detail, facts, playButton, creditLines, shareUrl, actions, slots, playExtras)
                }
                item { CastRow(detail.cast, actions.onOpenPerson) }
                item { Box(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp)) { tabBar() } }
                item { Box(Modifier.padding(top = 8.dp, bottom = 16.dp)) { tabContent() } }
            }
        }
        FloatingNav(actions.onBack, actions.onSearch, togetherButton = slots.togetherButton)
    }
}

private const val CAST_ROW_SIZE = 15

@Composable
private fun CastRow(cast: List<CastMember>, onOpenPerson: (String) -> Unit) {
    val actors = cast.take(CAST_ROW_SIZE).map { member -> ActorItem(member.personId, member.name, member.profileUrl, member.character) }
    ActorRail("Cast", actors, onOpen = { actor -> onOpenPerson(actor.personId) }, modifier = Modifier.padding(top = 8.dp))
}

private fun bannerScroll(listState: LazyListState): BannerScroll {
    if (listState.firstVisibleItemIndex > 0) {
        return BannerScroll(0f, bannerGone = true)
    }
    return BannerScroll(listState.firstVisibleItemScrollOffset.toFloat(), bannerGone = false)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleSummary(
    detail: TitleDetail,
    facts: List<String>,
    playButton: PlayButtonState,
    creditLines: List<String>,
    shareUrl: String,
    actions: TitlePageActions,
    slots: TitleSlots,
    playExtras: @Composable () -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(detail.title, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (fact in facts) {
                Text(fact, fontSize = 14.sp, color = mutedFactColor)
            }
        }
        PlayButton(playButton.label, playButton.busy, playButton.ready, playButton.disabled, playButton.onClick)
        playExtras()
        Overview(detail.overview)
        CreditLines(creditLines)
        ActionRow(
            title = detail.title,
            shareUrl = shareUrl,
            inWatchlist = detail.inWatchlist,
            rating = detail.rating,
            onToggleWatchlist = actions.onToggleWatchlist,
            onRate = actions.onRate,
            downloadButton = { slots.downloadButton(detail.id, detail.title) },
        )
    }
}

@Composable
private fun Overview(overview: String?) {
    if (overview.isNullOrEmpty()) {
        return
    }
    var expanded by remember { mutableStateOf(false) }
    var maxLines = OVERVIEW_COLLAPSED_LINES
    if (expanded) {
        maxLines = Int.MAX_VALUE
    }
    Text(
        overview,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        color = Color.White.copy(alpha = 0.85f),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    )
}

@Composable
private fun CreditLines(lines: List<String>) {
    if (lines.isEmpty()) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (line in lines) {
            Text(line, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A thin bar for the up-next episode's progress, with the episode named beside it. */
@Composable
fun UpNextProgress(progress: Float, label: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(OtterColors.baseContent))
        }
        Text(label, fontSize = 12.sp, color = mutedFactColor, maxLines = 1)
    }
}
