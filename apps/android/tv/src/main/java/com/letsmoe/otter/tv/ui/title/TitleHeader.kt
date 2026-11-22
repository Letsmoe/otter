package com.letsmoe.otter.tv.ui.title

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.bold.FilmStrip
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.fill.Play
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.FocusableSurface
import com.letsmoe.otter.tv.ui.components.IconButton
import com.letsmoe.otter.tv.ui.components.PillButton
import com.letsmoe.otter.tv.ui.components.RatingButton
import com.letsmoe.otter.tv.ui.components.TitleName
import com.letsmoe.otter.ui.title.TitleDetail

/** What the Watch button says and does; disabled while there's nothing to play. */
class PlayAction(val label: String, val enabled: Boolean, val onClick: () -> Unit)

/** What the header's buttons besides Watch do. */
class HeaderActions(
    val onToggleMyList: () -> Unit,
    val onRate: (Double) -> Unit,
    val onPlayTrailer: (String) -> Unit,
)

private val HEADER_WIDTH = 440.dp
private const val OVERVIEW_FOLDED_LINES = 3
// Unfolded, the description still leaves the header room under the top bar.
private const val OVERVIEW_UNFOLDED_LINES = 12

/**
 * Title, facts, the description (Up from the buttons focuses it, OK unfolds it), the credits,
 * then Watch, Trailer, My List and the rating. Watch has focus when the page opens.
 */
@Composable
fun TitleHeader(detail: TitleDetail, facts: List<String>, creditLines: List<String>, play: PlayAction, actions: HeaderActions) {
    Column(
        modifier = Modifier.padding(horizontal = TvSpacing.screenHorizontal, vertical = 24.dp).width(HEADER_WIDTH),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TitleName(detail.id, detail.title, modifier = Modifier.padding(bottom = 4.dp))
        Text(facts.joinToString("  ·  "), fontSize = TvType.detail, fontWeight = FontWeight.SemiBold, color = TvColors.secondaryContent, maxLines = 1)
        Overview(detail.overview)
        for (line in creditLines) {
            Text(line, fontSize = TvType.caption, color = TvColors.secondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TitleButtons(detail, play, actions)
    }
}

/** The description, folded to three lines; focusable so OK can unfold it. */
@Composable
private fun Overview(overview: String?) {
    if (overview.isNullOrBlank()) {
        return
    }
    var unfolded by remember { mutableStateOf(false) }
    var maxLines = OVERVIEW_FOLDED_LINES
    if (unfolded) {
        maxLines = OVERVIEW_UNFOLDED_LINES
    }
    FocusableSurface(
        onClick = { unfolded = !unfolded },
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.02f,
        containerColor = Color.Transparent,
        focusedContainerColor = Color.White.copy(alpha = 0.1f),
        // The text stays where it is; the padding only gives the focus ring room around it.
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            overview,
            fontSize = TvType.detail,
            lineHeight = 17.sp,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).animateContentSize(),
        )
    }
}

@Composable
private fun TitleButtons(detail: TitleDetail, play: PlayAction, actions: HeaderActions) {
    val watchButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { watchButton.requestFocus() }
    var myListIcon = PhosphorIcons.Bold.Plus
    if (detail.inWatchlist) {
        myListIcon = PhosphorIcons.Bold.Check
    }
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(
            watchLabel(play.label),
            onClick = play.onClick,
            icon = PhosphorIcons.Fill.Play,
            primary = true,
            enabled = play.enabled,
            modifier = Modifier.focusRequester(watchButton),
        )
        TrailerButton(detail.trailerKey, actions.onPlayTrailer)
        IconButton(myListIcon, "My List", onClick = actions.onToggleMyList)
        RatingButton(detail.rating, onRate = actions.onRate)
    }
}

@Composable
private fun TrailerButton(trailerKey: String?, onPlayTrailer: (String) -> Unit) {
    if (trailerKey == null) {
        return
    }
    PillButton("Trailer", onClick = { onPlayTrailer(trailerKey) }, icon = PhosphorIcons.Bold.FilmStrip)
}

// The shared labels say "Play" and "Play S1:E3"; the TV says "Watch".
private fun watchLabel(label: String): String {
    if (label == "Play") {
        return "Watch"
    }
    if (label.startsWith("Play ")) {
        return "Watch " + label.removePrefix("Play ")
    }
    return label
}
