package com.letsmoe.otter.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.ClockCounterClockwise
import com.adamglin.phosphoricons.regular.GearSix
import com.adamglin.phosphoricons.regular.PuzzlePiece
import com.adamglin.phosphoricons.regular.Users
import com.letsmoe.otter.ui.components.frostedGlass

private val cardShape = RoundedCornerShape(24.dp)

/** The app's other pages, listed openly on the profile page (they used to hide in a menu). */
class ProfileLinkActions(
    val onHistory: () -> Unit,
    val onPlugins: () -> Unit,
    val onSettings: () -> Unit,
    val onSwitchProfile: () -> Unit,
)

@Composable
fun ProfileLinks(actions: ProfileLinkActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().frostedGlass(cardShape).clip(cardShape)) {
        LinkRow("Watch History", PhosphorIcons.Regular.ClockCounterClockwise, actions.onHistory)
        LinkDivider()
        LinkRow("Plugins", PhosphorIcons.Regular.PuzzlePiece, actions.onPlugins)
        LinkDivider()
        LinkRow("App Settings", PhosphorIcons.Regular.GearSix, actions.onSettings)
        LinkDivider()
        LinkRow("Switch Profile", PhosphorIcons.Regular.Users, actions.onSwitchProfile)
    }
}

@Composable
private fun LinkRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, modifier = Modifier.size(18.dp).alpha(0.6f))
    }
}

@Composable
private fun LinkDivider() {
    HorizontalDivider(Modifier.padding(start = 54.dp), thickness = 1.dp, color = Color.White.copy(alpha = 0.08f))
}
