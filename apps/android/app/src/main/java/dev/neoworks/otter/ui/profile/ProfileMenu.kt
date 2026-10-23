package dev.neoworks.otter.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Bell
import com.adamglin.phosphoricons.regular.ClockCounterClockwise
import com.adamglin.phosphoricons.regular.GearSix
import com.adamglin.phosphoricons.regular.List as ListIcon
import com.adamglin.phosphoricons.regular.PuzzlePiece
import com.adamglin.phosphoricons.regular.Users
import com.adamglin.phosphoricons.regular.UsersThree
import dev.neoworks.otter.together.TogetherSession
import dev.neoworks.otter.ui.components.GlassColor
import dev.neoworks.otter.ui.theme.OtterColors

/** Where the profile page's menu leads. */
class ProfileMenuActions(
    val onNotifications: () -> Unit,
    val onHistory: () -> Unit,
    val onPlugins: () -> Unit,
    val onSettings: () -> Unit,
    val onSwitchProfile: () -> Unit,
)

/** The menu button of the profile page: a glass dropdown with the app's other pages. Mirrors ProfileMenu.svelte. */
@Composable
fun ProfileMenu(actions: ProfileMenuActions) {
    var open by remember { mutableStateOf(false) }

    fun choose(action: () -> Unit) {
        open = false
        action()
    }

    Box {
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { open = !open }, contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Regular.ListIcon, contentDescription = "Menu", modifier = Modifier.size(24.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(240.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = GlassColor,
            border = BorderStroke(1.dp, OtterColors.baseContent.copy(alpha = 0.1f)),
        ) {
            MenuItem("Watch Together", PhosphorIcons.Regular.UsersThree) { choose { TogetherSession.openSheet() } }
            MenuItem("Notifications", PhosphorIcons.Regular.Bell) { choose(actions.onNotifications) }
            MenuItem("Watch History", PhosphorIcons.Regular.ClockCounterClockwise) { choose(actions.onHistory) }
            MenuItem("Plugins", PhosphorIcons.Regular.PuzzlePiece) { choose(actions.onPlugins) }
            MenuItem("Settings", PhosphorIcons.Regular.GearSix) { choose(actions.onSettings) }
            MenuItem("Switch Profile", PhosphorIcons.Regular.Users) { choose(actions.onSwitchProfile) }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp).alpha(0.8f))
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        onClick = onClick,
    )
}
