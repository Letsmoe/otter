package com.letsmoe.otter.ui.title

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.adamglin.phosphoricons.fill.BellRinging as BellRingingFill
import com.adamglin.phosphoricons.regular.Bell
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.Heart
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.ShareNetwork
import com.adamglin.phosphoricons.regular.ThumbsDown
import com.adamglin.phosphoricons.regular.ThumbsUp
import com.letsmoe.otter.ui.components.frostedGlass
import com.letsmoe.otter.ui.components.jumpOnChange
import com.letsmoe.otter.ui.components.pressable
import com.letsmoe.otter.ui.components.ringOnChange
import com.letsmoe.otter.ui.components.squishOnChange
import kotlinx.coroutines.delay

/** Netflix's three ratings on the backend's 1-10 scale. */
private enum class RatingOption(val label: String, val value: Double, val icon: ImageVector, val filledIcon: ImageVector) {
    NOT_FOR_ME("Not for me", 2.0, PhosphorIcons.Regular.ThumbsDown, PhosphorIcons.Fill.ThumbsDownFill),
    LIKE("I like this", 7.5, PhosphorIcons.Regular.ThumbsUp, PhosphorIcons.Fill.ThumbsUpFill),
    LOVE("Love this!", 10.0, PhosphorIcons.Regular.Heart, PhosphorIcons.Fill.HeartFill),
}

private val actionTextColor = Color.White.copy(alpha = 0.7f)
private val rateMenuLift = 64.dp
private const val RATE_MENU_STAGGER_MILLISECONDS = 45L

/** Width of each action in the row; five of them fit a phone's width. */
val ACTION_BUTTON_WIDTH = 68.dp

/**
 * Netflix's icon row under the description: My List, Rate, Share and a download slot.
 * [downloadButton] renders the offline-download action, sized like the other four-by-column items.
 */
@Composable
fun ActionRow(
    title: String,
    shareUrl: String,
    inWatchlist: Boolean,
    notifying: Boolean,
    rating: Double?,
    onToggleWatchlist: () -> Unit,
    onToggleNotifying: () -> Unit,
    onRate: (Double) -> Unit,
    modifier: Modifier = Modifier,
    downloadButton: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    Row(modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
        ActionButton("My List", onToggleWatchlist) { WatchlistIcon(inWatchlist) }
        NotifyButton(notifying, onToggleNotifying)
        RateButton(rating, onRate)
        ActionButton(PhosphorIcons.Regular.ShareNetwork, "Share", onClick = { share(context, title, shareUrl) })
        downloadButton()
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    ActionButton(label, onClick) { ActionIcon(icon) }
}

/** An action with an animated [icon] slot; a changed [label] slides in from below. */
@Composable
fun ActionButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        Modifier.pressable(onClick = onClick).width(ACTION_BUTTON_WIDTH).clip(CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { icon() }
        AnimatedContent(label, transitionSpec = { labelSwap }, label = "actionLabel") { shownLabel ->
            Text(shownLabel, fontSize = 11.sp, color = actionTextColor, maxLines = 1)
        }
    }
}

private val labelSwap = (fadeIn(tween(180)) + slideInVertically(tween(220)) { height -> height / 2 }) togetherWith
    (fadeOut(tween(120)) + slideOutVertically(tween(160)) { height -> -height / 2 })

@Composable
private fun ActionIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = Color.White, modifier = modifier.size(26.dp))
}

/** Plus and check spin into each other, and the button squashes as it changes. */
@Composable
private fun WatchlistIcon(inWatchlist: Boolean) {
    AnimatedContent(
        inWatchlist,
        modifier = Modifier.squishOnChange(inWatchlist),
        transitionSpec = { popSwap },
        contentAlignment = Alignment.Center,
        label = "watchlistIcon",
    ) { saved ->
        val turn by transition.animateFloat(label = "watchlistTurn") { state -> quarterTurnUnlessVisible(state) }
        var icon = PhosphorIcons.Regular.Plus
        if (saved) {
            icon = PhosphorIcons.Regular.Check
        }
        ActionIcon(icon, Modifier.graphicsLayer { rotationZ = turn })
    }
}

private fun quarterTurnUnlessVisible(state: EnterExitState): Float {
    if (state == EnterExitState.Visible) {
        return 0f
    }
    return -90f
}

// The new icon grows in with an overshoot while the old one shrinks away.
private val popSwap = (scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f), initialScale = 0.2f) + fadeIn(tween(120))) togetherWith
    (scaleOut(tween(120), targetScale = 0.2f) + fadeOut(tween(120)))

/** The bell rings when turned on and squashes when turned off. */
@Composable
private fun NotifyButton(notifying: Boolean, onToggle: () -> Unit) {
    var label = "Notify me"
    if (notifying) {
        label = "Notifying"
    }
    ActionButton(label, onToggle) {
        AnimatedContent(
            notifying,
            modifier = Modifier.ringOnChange(notifying).squishOnChange(notifying),
            transitionSpec = { popSwap },
            contentAlignment = Alignment.Center,
            label = "notifyIcon",
        ) { ringing ->
            var icon = PhosphorIcons.Regular.Bell
            if (ringing) {
                icon = PhosphorIcons.Fill.BellRingingFill
            }
            ActionIcon(icon)
        }
    }
}

/** The chosen thumb (or heart) pops in and jumps up. */
@Composable
private fun RateButton(rating: Double?, onRate: (Double) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val current = RatingOption.entries.firstOrNull { option -> option.value == rating }
    Box {
        ActionButton("Rate", onClick = { menuOpen = !menuOpen }) {
            AnimatedContent(
                current,
                modifier = Modifier.jumpOnChange(current),
                transitionSpec = { popSwap },
                contentAlignment = Alignment.Center,
                label = "rateIcon",
            ) { shown ->
                var icon = PhosphorIcons.Regular.ThumbsUp
                if (shown != null) {
                    icon = shown.filledIcon
                }
                ActionIcon(icon)
            }
        }
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
            RatingOption.entries.forEachIndexed { index, option ->
                RateMenuItem(option, index, selected = option == current, onClick = { onChoose(option.value) })
            }
        }
    }
}

@Composable
private fun RateMenuItem(option: RatingOption, index: Int, selected: Boolean, onClick: () -> Unit) {
    var background = Color.Transparent
    var textColor = actionTextColor
    var icon = option.icon
    if (selected) {
        background = Color.White.copy(alpha = 0.1f)
        textColor = Color.White
        icon = option.filledIcon
    }
    // The three choices pop up one after the other.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * RATE_MENU_STAGGER_MILLISECONDS)
        appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f))
    }
    Column(
        Modifier
            .graphicsLayer {
                val scale = 0.4f + 0.6f * appear.value
                scaleX = scale
                scaleY = scale
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * size.height * 0.5f
            }
            .pressable(onClick = onClick)
            .width(80.dp)
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
