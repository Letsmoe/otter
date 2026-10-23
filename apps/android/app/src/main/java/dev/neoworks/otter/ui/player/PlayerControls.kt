package dev.neoworks.otter.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Pause
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.regular.ArrowClockwise
import com.adamglin.phosphoricons.regular.ArrowCounterClockwise
import com.adamglin.phosphoricons.regular.LockSimple
import com.adamglin.phosphoricons.regular.LockSimpleOpen
import com.adamglin.phosphoricons.regular.X
import dev.neoworks.otter.together.TogetherSession
import dev.neoworks.otter.together.otherPeers
import dev.neoworks.otter.ui.together.PeerAvatar

const val SKIP_SECONDS = 10

enum class PlayerSheetKind(val isSide: Boolean) {
    SPEED(true),
    SOURCES(true),
    TOGETHER(true),
    TELEVISION(true),
    TRACKS(false),
    EPISODES(false),
}

/** What the controls ask of the player screen. */
class PlayerActions(
    val close: () -> Unit,
    val togglePlay: () -> Unit,
    val skip: (Int) -> Unit,
    val seek: (Double) -> Unit,
    val preview: (Double?) -> Unit,
    val openSheet: (PlayerSheetKind) -> Unit,
    val playNext: () -> Unit,
    val toggleLock: () -> Unit,
)

/** Whether the controls are showing, and the reasons they stay up. */
@Stable
class PlayerChrome {
    var visible by mutableStateOf(true)
    var locked by mutableStateOf(false)
    /** The time under the finger while scrubbing. */
    var previewSeconds by mutableStateOf<Double?>(null)
    /** Counts interactions so the hide timer restarts. */
    var interactions by mutableIntStateOf(0)

    fun show() {
        visible = true
        interactions += 1
    }

    fun toggle() {
        if (visible) {
            visible = false
            return
        }
        show()
    }
}

private val topFade = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))

/** Top bar, centre controls and bottom bar over the video; hides on tap. Mirrors the controls of Player.svelte. */
@Composable
fun PlayerControlsLayer(
    state: PlayerUiState,
    position: PlaybackPosition,
    chrome: PlayerChrome,
    actions: PlayerActions,
    previewPlayer: () -> androidx.media3.exoplayer.ExoPlayer,
) {
    AnimatedVisibility(chrome.visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
        if (chrome.locked) {
            LockedControls(actions.toggleLock)
            return@AnimatedVisibility
        }
        UnlockedControls(state, position, chrome, actions, previewPlayer)
    }
}

@Composable
private fun LockedControls(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.size(width = 120.dp, height = 44.dp).playerGlass(CircleShape).clickableWithoutRipple(onClick = onUnlock),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PhosphorIcons.Regular.LockSimpleOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Unlock", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UnlockedControls(
    state: PlayerUiState,
    position: PlaybackPosition,
    chrome: PlayerChrome,
    actions: PlayerActions,
    previewPlayer: () -> androidx.media3.exoplayer.ExoPlayer,
) {
    // While scrubbing only the progress bar and its preview stay visible.
    var fadedAlpha = 1f
    if (chrome.previewSeconds != null) {
        fadedAlpha = 0f
    }
    Box(Modifier.fillMaxSize()) {
        PlayerTopBar(state, chrome, actions, Modifier.align(Alignment.TopCenter).alpha(fadedAlpha))
        if (!state.isBuffering && state.shownError == null && !state.isHolding) {
            CenterControls(state.isPlaying, actions, Modifier.align(Alignment.Center).alpha(fadedAlpha))
        }
        PlayerBottomBar(state, position, chrome, actions, previewPlayer, fadedAlpha, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PlayerTopBar(state: PlayerUiState, chrome: PlayerChrome, actions: PlayerActions, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(topFade)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            state.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(top = 8.dp),
        )
        RatingTrio(mediaId = state.mediaId, onInteract = chrome::show)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            TogetherPeersButton { actions.openSheet(PlayerSheetKind.TOGETHER) }
            CastButton(state.isCasting) { actions.openSheet(PlayerSheetKind.TELEVISION) }
            PlainIconButton(PhosphorIcons.Regular.LockSimple, "Lock controls", 24, actions.toggleLock)
            PlainIconButton(PhosphorIcons.Regular.X, "Close player", 26, actions.close)
        }
    }
}

@Composable
private fun TogetherPeersButton(onClick: () -> Unit) {
    val room by TogetherSession.room.collectAsStateWithLifecycle()
    val peerStates by TogetherSession.peerStates.collectAsStateWithLifecycle()
    val others = room.otherPeers()
    if (others.isEmpty()) {
        return
    }
    Row(Modifier.clickableWithoutRipple(onClick = onClick).padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        for (peer in others) {
            PeerAvatar(peer, 30.dp, buffering = peerStates[peer.id]?.buffering == true)
        }
    }
}

@Composable
private fun PlainIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, iconSize: Int, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickableWithoutRipple(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(iconSize.dp))
    }
}

// The standard cast icon (Media3's), filled while a TV plays.
@Composable
private fun CastButton(casting: Boolean, onClick: () -> Unit) {
    var icon = androidx.media3.cast.R.drawable.media_route_button_disconnected
    if (casting) {
        icon = androidx.media3.cast.R.drawable.media_route_button_connected
    }
    Box(Modifier.size(44.dp).clickableWithoutRipple(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = "Play on TV", modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun CenterControls(isPlaying: Boolean, actions: PlayerActions, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(80.dp), verticalAlignment = Alignment.CenterVertically) {
        SkipButton(PhosphorIcons.Regular.ArrowCounterClockwise, "Back $SKIP_SECONDS seconds") { actions.skip(-SKIP_SECONDS) }
        Box(Modifier.size(80.dp).clickableWithoutRipple(onClick = actions.togglePlay), contentAlignment = Alignment.Center) {
            if (isPlaying) {
                Icon(PhosphorIcons.Fill.Pause, "Pause", modifier = Modifier.size(56.dp))
            } else {
                Icon(PhosphorIcons.Fill.Play, "Play", modifier = Modifier.size(56.dp))
            }
        }
        SkipButton(PhosphorIcons.Regular.ArrowClockwise, "Forward $SKIP_SECONDS seconds") { actions.skip(SKIP_SECONDS) }
    }
}

@Composable
private fun SkipButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(64.dp).clickableWithoutRipple(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(44.dp))
        Text("$SKIP_SECONDS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
