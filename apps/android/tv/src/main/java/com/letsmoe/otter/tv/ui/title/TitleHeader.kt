package com.letsmoe.otter.tv.ui.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.fill.Play
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.PillButton
import com.letsmoe.otter.tv.ui.components.TitleName
import com.letsmoe.otter.ui.title.TitleDetail

/** What the Play button says and does; disabled while there's nothing to play. */
class PlayAction(val label: String, val enabled: Boolean, val onClick: () -> Unit)

/** Title, facts, overview and credits, then Play and My List; Play has focus when the page opens. */
@Composable
fun TitleHeader(detail: TitleDetail, facts: List<String>, creditLines: List<String>, play: PlayAction, onToggleMyList: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = TvSpacing.screenHorizontal, vertical = 24.dp).width(440.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TitleName(detail.id, detail.title, modifier = Modifier.padding(bottom = 4.dp))
        Text(facts.joinToString("  ·  "), fontSize = TvType.detail, fontWeight = FontWeight.SemiBold, color = TvColors.secondaryContent, maxLines = 1)
        Overview(detail.overview)
        for (line in creditLines) {
            Text(line, fontSize = TvType.caption, color = TvColors.secondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TitleButtons(play, detail.inWatchlist, onToggleMyList)
    }
}

@Composable
private fun Overview(overview: String?) {
    if (overview.isNullOrBlank()) {
        return
    }
    Text(
        overview,
        fontSize = TvType.detail,
        lineHeight = 17.sp,
        color = Color.White.copy(alpha = 0.85f),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TitleButtons(play: PlayAction, inMyList: Boolean, onToggleMyList: () -> Unit) {
    val playButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { playButton.requestFocus() }
    var myListIcon = PhosphorIcons.Bold.Plus
    if (inMyList) {
        myListIcon = PhosphorIcons.Bold.Check
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(
            play.label,
            onClick = play.onClick,
            icon = PhosphorIcons.Fill.Play,
            primary = true,
            enabled = play.enabled,
            modifier = Modifier.focusRequester(playButton),
        )
        PillButton("My List", onClick = onToggleMyList, icon = myListIcon)
    }
}
