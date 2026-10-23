package dev.neoworks.otter.ui.together

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.regular.FilmSlate
import dev.neoworks.otter.together.TogetherSession
import dev.neoworks.otter.ui.components.frostedGlass

/** Compact card with the title the room is watching, or a hint to pick one. */
@Composable
internal fun NowPlayingCard() {
    val title by TogetherSession.nowPlayingTitle.collectAsStateWithLifecycle()
    val playingTitle = title
    Row(
        Modifier.fillMaxWidth().subtleCard().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(innerGap),
    ) {
        Box(Modifier.size(44.dp).frostedGlass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(nowPlayingIcon(playingTitle), contentDescription = null, modifier = Modifier.size(20.dp))
        }
        if (playingTitle == null) {
            EmptyNowPlayingText()
            return@Row
        }
        Column(Modifier.weight(1f)) {
            Text("NOW PLAYING", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = faintText)
            Text(playingTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyNowPlayingText() {
    Text("Pick something to watch, everyone follows along.", fontSize = 14.sp, color = mutedText, modifier = Modifier.padding(end = 4.dp))
}

private fun nowPlayingIcon(title: String?): ImageVector {
    if (title == null) {
        return PhosphorIcons.Regular.FilmSlate
    }
    return PhosphorIcons.Fill.Play
}
