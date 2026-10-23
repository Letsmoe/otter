package dev.neoworks.otter.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.neoworks.otter.profiles.ProfileSession
import dev.neoworks.otter.profiles.avatarImage
import dev.neoworks.otter.ui.components.liquidGlass
import dev.neoworks.otter.ui.profile.ProfileFlight
import dev.neoworks.otter.ui.theme.OtterColors
import dev.neoworks.otter.vpn.VpnStatusStore

private val barPadding = 4.dp
private val tabHeight = 44.dp
private val barCornerRadius = 24.dp
private val pillCornerRadius = 20.dp
private val barMargin = 12.dp

// The theme's body line height (24sp) would leave a wide gap under the icon and push the pair off-centre.
private val tabLabelStyle = TextStyle(
    fontSize = 10.sp,
    lineHeight = 12.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/** Space to leave below scrolling content so its end isn't hidden under the bar. */
@Composable
fun bottomBarClearance(): Dp {
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return tabHeight + barPadding * 2 + barMargin * 2 + navigationBarHeight
}

/**
 * Phone tab bar floating over the content: the main destinations within thumb
 * reach, with a highlight that slides to the active tab.
 */
@Composable
fun BottomBar(activeTab: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = barMargin)
            .padding(bottom = barMargin)
            .liquidGlass(barCornerRadius)
            .padding(barPadding),
    ) {
        val tabWidth = maxWidth / Tab.entries.size
        ActivePill(activeTab, tabWidth)
        Row(Modifier.fillMaxWidth()) {
            for (tab in Tab.entries) {
                TabButton(tab, isActive = tab == activeTab, onClick = { onSelect(tab) }, modifier = Modifier.width(tabWidth))
            }
        }
    }
}

// Slides with a slight overshoot; fades out on pages that belong to no tab.
@Composable
private fun ActivePill(activeTab: Tab?, tabWidth: Dp) {
    var targetIndex = 0
    var targetAlpha = 0f
    if (activeTab != null) {
        targetIndex = activeTab.ordinal
        targetAlpha = 1f
    }
    val offset by animateDpAsState(
        targetValue = tabWidth * targetIndex,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "pillOffset",
    )
    val alpha by animateFloatAsState(targetAlpha, tween(200), label = "pillAlpha")
    Box(
        Modifier
            .offset(x = offset)
            .width(tabWidth)
            .height(tabHeight)
            .clip(RoundedCornerShape(pillCornerRadius))
            .background(OtterColors.baseContent.copy(alpha = 0.1f * alpha)),
    )
}

@Composable
private fun TabButton(tab: Tab, isActive: Boolean, onClick: () -> Unit, modifier: Modifier) {
    var targetColor = OtterColors.baseContent.copy(alpha = 0.6f)
    var fontWeight = FontWeight.Normal
    if (isActive) {
        targetColor = OtterColors.baseContent
        fontWeight = FontWeight.SemiBold
    }
    val color by animateColorAsState(targetColor, label = "tabColor")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(isActive) {
        if (!isActive) {
            return@LaunchedEffect
        }
        pop.snapTo(0.8f)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
    }
    Column(
        modifier = modifier
            .height(tabHeight)
            .clip(RoundedCornerShape(pillCornerRadius))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.scale(pop.value)) {
            TabIcon(tab, isActive, color)
        }
        Text(tab.label, color = color, fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis, style = tabLabelStyle)
    }
}

@Composable
private fun TabIcon(tab: Tab, isActive: Boolean, color: Color) {
    val activeProfile by ProfileSession.activeProfile.collectAsStateWithLifecycle()
    val profile = activeProfile
    if (tab == Tab.PROFILE && profile != null) {
        ProfileAvatarIcon(avatarImage(profile.avatarUrl, profile.name), isActive)
        return
    }
    var icon = tab.icon
    if (isActive) {
        icon = tab.activeIcon
    }
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
}

// Active: outlined with a 1px gap, like the web app's outline-offset. Hidden while the picked
// avatar flies in; shows the VPN state as a dot.
@Composable
private fun ProfileAvatarIcon(avatarModel: String, isActive: Boolean) {
    val flight by ProfileFlight.flight.collectAsStateWithLifecycle()
    val outerShape = RoundedCornerShape(7.dp)
    var outline = Modifier.size(20.dp)
    if (isActive) {
        outline = outline.border(1.5.dp, OtterColors.baseContent, outerShape)
    }
    var avatarAlpha = 1f
    if (flight != null) {
        avatarAlpha = 0f
    }
    Box(outline, contentAlignment = Alignment.Center) {
        AsyncImage(
            avatarModel,
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .alpha(avatarAlpha)
                .clip(RoundedCornerShape(5.dp))
                .onGloballyPositioned { coordinates -> ProfileFlight.reportTargetBounds(coordinates.boundsInWindow()) },
        )
        VpnDot(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-4).dp))
    }
}

// Green on VPN, red when a required VPN is down, amber when it's down but not required.
@Composable
private fun VpnDot(modifier: Modifier) {
    val vpn by VpnStatusStore.status.collectAsStateWithLifecycle()
    if (!vpn.known) {
        return
    }
    var color = OtterColors.warning
    if (vpn.connected) {
        color = OtterColors.success
    } else if (vpn.required) {
        color = OtterColors.error
    }
    Box(modifier.size(10.dp).background(OtterColors.base200, CircleShape).padding(2.dp).background(color, CircleShape))
}

