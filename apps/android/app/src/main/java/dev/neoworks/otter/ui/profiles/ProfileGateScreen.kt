package dev.neoworks.otter.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import dev.neoworks.otter.profiles.avatarImage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.neoworks.otter.ui.components.LocalGlassBackdrop
import dev.neoworks.otter.ui.components.frostedGlass

/**
 * "Choose Your Profile" over a trending title's poster: pick, unlock, add and
 * manage profiles before the app opens.
 */
@Composable
fun ProfileGateScreen(gate: ProfileGateViewModel = viewModel()) {
    val screen = gate.screen
    BackHandler(enabled = screen != GateScreen.Pick, onBack = gate::backToPicker)
    val glassBackdrop = rememberHazeState()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().hazeSource(glassBackdrop)) {
            SpotlightBackdrop(gate.spotlight, blurred = screen != GateScreen.Pick)
        }
        CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) {
            GateContent(gate, screen)
        }
    }
}

@Composable
private fun GateContent(gate: ProfileGateViewModel, screen: GateScreen) {
    AnimatedContent(
        targetState = screen,
        transitionSpec = { gateTransition(targetState) },
        contentKey = { current -> current::class },
        label = "gate",
    ) { current ->
        if (current == GateScreen.Pick) {
            ProfilePicker(gate)
            return@AnimatedContent
        }
        EditorPanel { GateSubScreen(current, gate) }
    }
}

// The picker fades back in; sub-screens slide up from the bottom.
private fun gateTransition(target: GateScreen): ContentTransform {
    if (target == GateScreen.Pick) {
        return fadeIn(tween(260)) togetherWith fadeOut(tween(200))
    }
    return slideInVertically(tween(440, easing = FastOutSlowInEasing)) { height -> height } togetherWith fadeOut(tween(200))
}

@Composable
private fun GateSubScreen(screen: GateScreen, gate: ProfileGateViewModel) {
    when (screen) {
        is GateScreen.Unlock -> PinPad(
            title = "Profile Lock",
            message = "Enter your PIN to access this profile.",
            avatarModel = avatarImage(screen.profile.avatarUrl, screen.profile.name),
            onBack = gate::backToPicker,
            onComplete = { pin -> gate.verifyPin(screen, pin) },
        )
        is GateScreen.Edit -> ProfileEditor(screen.profile, onBack = gate::backToPicker, onSaved = gate::backToPicker)
        GateScreen.Add -> ProfileEditor(null, onBack = gate::backToPicker, onSaved = gate::backToPicker)
        GateScreen.Pick -> Unit
    }
}

/** Sub-screens slide up as a glass sheet below the status bar. */
@Composable
private fun EditorPanel(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 12.dp)
            .frostedGlass(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
    ) {
        content()
    }
}

/** The poster stays behind every screen with a slow push-in; sub-screens blur and dim it. */
@Composable
private fun SpotlightBackdrop(spotlight: Spotlight?, blurred: Boolean) {
    val drift = rememberInfiniteTransition(label = "drift")
    val driftScale by drift.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(18_000), RepeatMode.Reverse),
        label = "driftScale",
    )
    var blurRadius = 0f
    var brightness = 1f
    if (blurred) {
        blurRadius = 18f
        brightness = 0.55f
    }
    val animatedBlur by animateFloatAsState(blurRadius, tween(500), label = "blur")
    val animatedBrightness by animateFloatAsState(brightness, tween(500), label = "brightness")
    Box(Modifier.fillMaxSize().blur(animatedBlur.dp).graphicsLayer { alpha = animatedBrightness }) {
        if (spotlight != null) {
            AsyncImage(
                model = spotlight.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .graphicsLayer {
                        scaleX = driftScale
                        scaleY = driftScale
                    },
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.5f), 0.3f to Color.Transparent, 0.78f to Color.Black),
            ),
        )
    }
}
