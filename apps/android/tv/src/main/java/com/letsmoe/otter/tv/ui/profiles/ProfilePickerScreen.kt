package com.letsmoe.otter.tv.ui.profiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.fill.LockSimple
import com.adamglin.phosphoricons.fill.PencilSimple
import com.letsmoe.otter.graphql.fragment.ProfileFields
import com.letsmoe.otter.profiles.Spotlight
import com.letsmoe.otter.profiles.avatarImage
import com.letsmoe.otter.tv.theme.TvColors
import com.letsmoe.otter.tv.theme.TvSpacing
import com.letsmoe.otter.tv.theme.TvType
import com.letsmoe.otter.tv.ui.components.FailedNotice
import com.letsmoe.otter.tv.ui.components.FocusableSurface
import com.letsmoe.otter.tv.ui.components.PillButton
import com.letsmoe.otter.ui.LoadState

private val TILE_SIZE = 96.dp
private val TILE_SHAPE = RoundedCornerShape(14.dp)
private val TILE_RING = 3.dp

/** "Who's watching?": a row of profiles, then a button to manage them. */
@Composable
fun ProfilePickerScreen(gate: ProfileGateViewModel) {
    Box(Modifier.fillMaxSize()) {
        when (val profiles = gate.profiles) {
            LoadState.Loading -> Unit
            is LoadState.Failed -> FailedNotice(
                "Otter isn't answering",
                profiles.message,
                onRetry = gate::loadProfiles,
                modifier = Modifier.align(Alignment.Center),
            )
            is LoadState.Loaded -> PickerContent(profiles.value, gate)
        }
        SpotlightCaption(gate.spotlight, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun PickerContent(profiles: List<ProfileFields>, gate: ProfileGateViewModel) {
    val firstTile = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstTile.requestFocus() }
    var heading = "Who's watching?"
    if (gate.managing) {
        heading = "Manage profiles"
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(heading, fontSize = TvType.screenTitle, fontWeight = FontWeight.Bold, color = TvColors.baseContent)
        ProfileRow(profiles, gate, firstTile)
        ManageButton(gate)
    }
}

@Composable
private fun ProfileRow(profiles: List<ProfileFields>, gate: ProfileGateViewModel, firstTile: FocusRequester) {
    LazyRow(
        modifier = Modifier.padding(top = 28.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(profiles, key = { profile -> profile.id }) { profile ->
            var tileModifier: Modifier = Modifier
            if (profile == profiles.first()) {
                tileModifier = Modifier.focusRequester(firstTile)
            }
            ProfileTile(profile, gate.managing, onClick = { gate.openProfile(profile) }, modifier = tileModifier)
        }
        item {
            var addModifier: Modifier = Modifier
            if (profiles.isEmpty()) {
                addModifier = Modifier.focusRequester(firstTile)
            }
            AddTile(onClick = gate::openAdd, modifier = addModifier)
        }
    }
}

@Composable
private fun ProfileTile(profile: ProfileFields, managing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    TileColumn(profile.name, focused) {
        FocusableSurface(
            onClick = onClick,
            shape = TILE_SHAPE,
            focusedScale = 1.1f,
            ringWidth = TILE_RING,
            modifier = modifier.size(TILE_SIZE).onFocusChanged { state -> focused = state.isFocused },
        ) {
            Box {
                AsyncImage(
                    model = avatarImage(profile.avatarUrl, profile.name),
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(TILE_SHAPE),
                )
                TileBadge(profile, managing, Modifier.align(Alignment.Center))
            }
        }
    }
}

/** Managing shows a pencil over every tile; otherwise locked profiles show a small lock. */
@Composable
private fun TileBadge(profile: ProfileFields, managing: Boolean, modifier: Modifier) {
    if (managing) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        Icon(PhosphorIcons.Fill.PencilSimple, contentDescription = "Edit", modifier = modifier.size(26.dp))
        return
    }
    if (profile.hasPin) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            Icon(
                PhosphorIcons.Fill.LockSimple,
                contentDescription = "Locked",
                modifier = Modifier
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(4.dp)
                    .size(11.dp),
            )
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    TileColumn("Add profile", focused) {
        FocusableSurface(
            onClick = onClick,
            shape = TILE_SHAPE,
            focusedScale = 1.1f,
            ringWidth = TILE_RING,
            modifier = modifier.size(TILE_SIZE).onFocusChanged { state -> focused = state.isFocused },
        ) {
            TileIcon(PhosphorIcons.Bold.Plus)
        }
    }
}

@Composable
private fun TileIcon(icon: ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
    }
}

/** A tile with its label underneath; the label lights up with the tile's focus. */
@Composable
private fun TileColumn(label: String, focused: Boolean, tile: @Composable () -> Unit) {
    var targetAlpha = 0.55f
    if (focused) {
        targetAlpha = 1f
    }
    val labelAlpha by animateFloatAsState(targetAlpha, label = "labelAlpha")
    Column(
        modifier = Modifier.width(TILE_SIZE + 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        tile()
        Text(
            label,
            fontSize = TvType.body,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().alpha(labelAlpha),
        )
    }
}

@Composable
private fun ManageButton(gate: ProfileGateViewModel) {
    var label = "Manage profiles"
    if (gate.managing) {
        label = "Done"
    }
    PillButton(label, onClick = { gate.managing = !gate.managing })
}

/** The trending title behind the picker, named in the bottom left. */
@Composable
private fun SpotlightCaption(spotlight: Spotlight?, modifier: Modifier) {
    if (spotlight == null) {
        return
    }
    Column(
        modifier = modifier
            .padding(horizontal = TvSpacing.screenHorizontal, vertical = TvSpacing.screenVertical)
            .width(260.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Trending now", fontSize = TvType.caption, fontWeight = FontWeight.SemiBold, color = TvColors.secondaryContent)
        if (spotlight.logoUrl != null) {
            AsyncImage(spotlight.logoUrl, contentDescription = spotlight.title, modifier = Modifier.heightIn(max = 36.dp))
        } else {
            Text(spotlight.title, fontSize = TvType.sectionTitle, fontWeight = FontWeight.Bold)
        }
        val tagline = spotlight.tagline
        if (tagline != null) {
            Text(tagline, fontSize = TvType.detail, color = TvColors.secondaryContent, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
