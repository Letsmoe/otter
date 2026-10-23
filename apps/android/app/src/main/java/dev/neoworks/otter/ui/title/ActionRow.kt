package dev.neoworks.otter.ui.title

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Heart as HeartFill
import com.adamglin.phosphoricons.fill.ThumbsDown as ThumbsDownFill
import com.adamglin.phosphoricons.fill.ThumbsUp as ThumbsUpFill
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.Heart
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.ShareNetwork
import com.adamglin.phosphoricons.regular.ThumbsDown
import com.adamglin.phosphoricons.regular.ThumbsUp
import dev.neoworks.otter.ui.components.frostedGlass

/** Netflix's three ratings on the backend's 1-10 scale. */
private enum class RatingOption(val label: String, val value: Double, val icon: ImageVector, val filledIcon: ImageVector) {
    NOT_FOR_ME("Not for me", 2.0, PhosphorIcons.Regular.ThumbsDown, PhosphorIcons.Fill.ThumbsDownFill),
    LIKE("I like this", 7.5, PhosphorIcons.Regular.ThumbsUp, PhosphorIcons.Fill.ThumbsUpFill),
    LOVE("Love this!", 10.0, PhosphorIcons.Regular.Heart, PhosphorIcons.Fill.HeartFill),
}

private val actionTextColor = Color.White.copy(alpha = 0.7f)
private val rateMenuLift = 64.dp

/**
 * Netflix's icon row under the description: My List, Rate, Share and a download slot.
 * [downloadButton] renders the offline-download action, sized like the other four-by-column items.
 */
@Composable
fun ActionRow(
    title: String,
    shareUrl: String,
    inWatchlist: Boolean,
    rating: Double?,
    onToggleWatchlist: () -> Unit,
    onRate: (Double) -> Unit,
    modifier: Modifier = Modifier,
    downloadButton: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    Row(modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
        var listIcon = PhosphorIcons.Regular.Plus
        if (inWatchlist) {
            listIcon = PhosphorIcons.Regular.Check
        }
        ActionButton(listIcon, "My List", onToggleWatchlist)
        RateButton(rating, onRate)
        ActionButton(PhosphorIcons.Regular.ShareNetwork, "Share", onClick = { share(context, title, shareUrl) })
        downloadButton()
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(80.dp).clip(CircleShape).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 11.sp, color = actionTextColor)
    }
}

@Composable
private fun RateButton(rating: Double?, onRate: (Double) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val current = RatingOption.entries.firstOrNull { option -> option.value == rating }
    var icon = PhosphorIcons.Regular.ThumbsUp
    if (current != null) {
        icon = current.filledIcon
    }
    Box {
        ActionButton(icon, "Rate", onClick = { menuOpen = !menuOpen })
        if (menuOpen) {
            RateMenu(current, onDismiss = { menuOpen = false }, onChoose = { value ->
                menuOpen = false
                onRate(value)
            })
        }
    }
}

@Composable
private fun RateMenu(current: RatingOption?, onDismiss: () -> Unit, onChoose: (Double) -> Unit) {
    val lift = with(LocalDensity.current) { rateMenuLift.roundToPx() }
    Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -lift), onDismissRequest = onDismiss) {
        Row(Modifier.frostedGlass(CircleShape).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (option in RatingOption.entries) {
                RateMenuItem(option, selected = option == current, onClick = { onChoose(option.value) })
            }
        }
    }
}

@Composable
private fun RateMenuItem(option: RatingOption, selected: Boolean, onClick: () -> Unit) {
    var background = Color.Transparent
    var textColor = actionTextColor
    var icon = option.icon
    if (selected) {
        background = Color.White.copy(alpha = 0.1f)
        textColor = Color.White
        icon = option.filledIcon
    }
    Column(
        Modifier.width(80.dp).clip(CircleShape).background(background).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(22.dp))
        Text(option.label, fontSize = 10.sp, color = textColor, maxLines = 1)
    }
}

private fun share(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(send, title))
}
