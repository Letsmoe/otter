package dev.neoworks.otter.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import dev.neoworks.otter.ui.profile.ProfileFlight
import dev.neoworks.otter.graphql.fragment.ProfileFields
import dev.neoworks.otter.profiles.avatarImage
import dev.neoworks.otter.ui.LoadState
import dev.neoworks.otter.ui.components.frostedGlass

private val tileShape = RoundedCornerShape(16.dp)

// The sheet's top edge bulges upwards like Netflix's.
private object BulgingSheetShape : Shape {
    private val bulgeHeight = 44.dp

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val bulge = with(density) { bulgeHeight.toPx() }
        val path = Path().apply {
            moveTo(0f, bulge)
            // A quadratic curve peaks halfway to its control point: y = 0 at the centre.
            quadraticTo(size.width / 2f, -bulge, size.width, bulge)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun ProfilePicker(gate: ProfileGateViewModel) {
    Box(Modifier.fillMaxSize()) {
        Text(
            "OTTER",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 14.dp),
        )
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            gate.spotlight?.let { spotlight -> SpotlightCaption(spotlight) }
            ProfileSheet(gate)
        }
    }
}

@Composable
private fun SpotlightCaption(spotlight: Spotlight) {
    Column(
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (spotlight.logoUrl != null) {
            AsyncImage(spotlight.logoUrl, contentDescription = spotlight.title, modifier = Modifier.heightIn(max = 96.dp).fillMaxWidth(0.7f))
        } else {
            Text(spotlight.title, fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
        spotlight.tagline?.let { tagline ->
            Text(tagline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProfileSheet(gate: ProfileGateViewModel) {
    var heading = "Choose Your Profile"
    if (gate.managing) {
        heading = "Manage Profiles"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass(BulgingSheetShape, withRim = false)
            .navigationBarsPadding()
            .padding(start = 32.dp, end = 32.dp, top = 36.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(heading, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
        when (val profiles = gate.profiles) {
            is LoadState.Loading -> CircularProgressIndicator(Modifier.padding(vertical = 32.dp))
            is LoadState.Failed -> ServerUnreachable(profiles.message, onRetry = gate::loadProfiles)
            is LoadState.Loaded -> ProfileGrid(profiles.value, gate)
        }
    }
}

@Composable
private fun ServerUnreachable(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, modifier = Modifier.alpha(0.7f))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White)) {
            Text("Try again", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Two columns: every profile, then Add and Edit. */
@Composable
private fun ProfileGrid(profiles: List<ProfileFields>, gate: ProfileGateViewModel) {
    val tiles = mutableListOf<@Composable () -> Unit>()
    for (profile in profiles) {
        tiles.add { ProfileTile(profile, gate.managing, onClick = { bounds -> openProfileWithFlight(gate, profile, bounds) }) }
    }
    if (!gate.managing) {
        tiles.add { ActionTile(Icons.Filled.Add, "Add", onClick = gate::openAdd) }
    }
    if (profiles.isNotEmpty()) {
        tiles.add { ManageTile(gate) }
    }
    Column(
        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        for (row in tiles.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                for (tile in row) {
                    Box(Modifier.width(96.dp), contentAlignment = Alignment.TopCenter) { tile() }
                }
            }
        }
    }
}

@Composable
private fun ManageTile(gate: ProfileGateViewModel) {
    if (gate.managing) {
        ActionTile(Icons.Filled.Check, "Done", onClick = { gate.managing = false })
        return
    }
    ActionTile(Icons.Filled.Edit, "Edit", onClick = { gate.managing = true })
}

// A profile that opens without a PIN flies its avatar into the tab bar.
private fun openProfileWithFlight(gate: ProfileGateViewModel, profile: ProfileFields, bounds: Rect) {
    if (!gate.managing && !profile.hasPin) {
        ProfileFlight.start(avatarImage(profile.avatarUrl, profile.name), bounds)
    }
    gate.openProfile(profile)
}

@Composable
private fun ProfileTile(profile: ProfileFields, managing: Boolean, onClick: (Rect) -> Unit) {
    var avatarBounds by remember { mutableStateOf(Rect.Zero) }
    Column(
        modifier = Modifier.clickable { onClick(avatarBounds) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(80.dp).clip(tileShape).onGloballyPositioned { coordinates -> avatarBounds = coordinates.boundsInWindow() }) {
            AsyncImage(avatarImage(profile.avatarUrl, profile.name), contentDescription = null, modifier = Modifier.fillMaxSize())
            if (managing) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${profile.name}", modifier = Modifier.size(28.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(profile.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (profile.hasPin) {
                Icon(Icons.Filled.Lock, contentDescription = "Locked", modifier = Modifier.size(13.dp).alpha(0.6f))
            }
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(tileShape).background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
    }
}
