package com.letsmoe.otter.tv.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.letsmoe.otter.profiles.Spotlight
import com.letsmoe.otter.profiles.avatarImage
import com.letsmoe.otter.tv.ui.components.TopBar

/** Who's watching: pick, unlock, add and manage profiles over a trending title's backdrop. */
@Composable
fun ProfileGateScreen(gate: ProfileGateViewModel = viewModel()) {
    val screen = gate.screen
    BackHandler(enabled = screen != GateScreen.Pick, onBack = gate::backToPicker)
    BackHandler(enabled = screen == GateScreen.Pick && gate.managing) { gate.managing = false }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        SpotlightBackdrop(gate.spotlight, dimmed = screen != GateScreen.Pick)
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
            contentKey = { current -> current::class },
            label = "gate",
        ) { current ->
            GateContent(current, gate)
        }
        TopBar()
    }
}

@Composable
private fun GateContent(screen: GateScreen, gate: ProfileGateViewModel) {
    when (screen) {
        GateScreen.Pick -> ProfilePickerScreen(gate)
        is GateScreen.Unlock -> PinEntry(
            title = screen.profile.name,
            message = "Enter your PIN to open this profile.",
            onComplete = { pin -> gate.verifyPin(screen, pin) },
            avatarModel = avatarImage(screen.profile.avatarUrl, screen.profile.name),
        )
        is GateScreen.Edit -> ProfileEditorScreen(screen.profile, onDone = gate::backToPicker)
        GateScreen.Add -> ProfileEditorScreen(null, onDone = gate::backToPicker)
    }
}

/** The backdrop drifts in slowly; everything but the picker blurs and dims it. */
@Composable
private fun SpotlightBackdrop(spotlight: Spotlight?, dimmed: Boolean) {
    val drift = rememberInfiniteTransition(label = "drift")
    val driftScale by drift.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(24_000), RepeatMode.Reverse),
        label = "driftScale",
    )
    var blurRadius = 0f
    var brightness = 0.75f
    if (dimmed) {
        blurRadius = 24f
        brightness = 0.35f
    }
    val animatedBlur by animateFloatAsState(blurRadius, tween(500), label = "blur")
    val animatedBrightness by animateFloatAsState(brightness, tween(500), label = "brightness")
    Box(Modifier.fillMaxSize().blur(animatedBlur.dp).graphicsLayer { alpha = animatedBrightness }) {
        if (spotlight != null) {
            AsyncImage(
                model = spotlightImage(spotlight),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = driftScale
                    scaleY = driftScale
                },
            )
        }
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(0f to Color.Black.copy(alpha = 0.35f), 1f to Color.Black.copy(alpha = 0.85f)),
        ),
    )
}

// A TV is landscape: the backdrop fits it, the poster only when there's no backdrop.
private fun spotlightImage(spotlight: Spotlight): String {
    val backdrop = spotlight.backdropUrl
    if (backdrop != null) {
        return backdrop
    }
    return spotlight.posterUrl
}
